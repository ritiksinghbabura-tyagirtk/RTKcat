#!/usr/bin/env python3

import hashlib
import html
import json
import os
import re
import shutil
import subprocess
import threading
import time
import webbrowser
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LOCAL_PROPERTIES = ROOT / "local.properties"
RELEASE_DIR = ROOT / "app" / "build" / "outputs" / "apk" / "release"
APK_PATH = RELEASE_DIR / "app-release.apk"

PORT = 8787

# App icon locations commonly used by Android projects.
ICON_TARGETS = [
    ROOT / "app" / "src" / "main" / "res" / "drawable" / "app_icon.png",
    ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "app_icon.png",
]

LAST_BUILD = {
    "status": "idle",
    "log": "",
    "apk": "",
    "time": "",
    "error": "",
}


def timestamp():
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def emit(message):
    LAST_BUILD["log"] += str(message)
    print(str(message), end="", flush=True)


def reset_build():
    LAST_BUILD["status"] = "building"
    LAST_BUILD["log"] = ""
    LAST_BUILD["apk"] = ""
    LAST_BUILD["time"] = ""
    LAST_BUILD["error"] = ""


def safe_filename(name):
    name = (name or "").strip()

    if not name:
        name = "RTKTelegramManager"

    if name.lower().endswith(".apk"):
        name = name[:-4]

    if not re.fullmatch(r"[A-Za-z0-9._ -]{1,80}", name):
        raise ValueError(
            "APK filename may contain only letters, numbers, dot, "
            "underscore, spaces and hyphen."
        )

    return name + ".apk"


def clean_value(value):
    return str(value or "").strip()


def validate(values):
    required = {
        "developer_key": "Developer Key",
        "bot1_token": "Bot 1 Token",
        "bot1_chat_id": "Bot 1 Chat ID",
        "bot2_token": "Bot 2 Token",
        "bot2_chat_id": "Bot 2 Chat ID",
        "app_name": "App Name",
        "web_url": "Target Web URL",
    }

    for key, label in required.items():
        if not clean_value(values.get(key)):
            raise ValueError(f"{label} is required.")

    values["apk_name"] = safe_filename(values.get("apk_name"))

    token_re = re.compile(r"^\d+:[A-Za-z0-9_-]+$")

    for key, label in [
        ("bot1_token", "Bot 1 Token"),
        ("bot2_token", "Bot 2 Token"),
    ]:
        if not token_re.fullmatch(values[key]):
            raise ValueError(
                f"{label} does not look like a valid Telegram bot token."
            )

    url = values["web_url"]

    if not re.fullmatch(r"https?://[^\s]+", url, re.IGNORECASE):
        raise ValueError(
            "Target Web URL must start with http:// or https://"
        )

    if len(values["app_name"]) > 80:
        raise ValueError("App Name is too long.")

    return values


def write_local_properties(values):
    backup = None

    if LOCAL_PROPERTIES.exists():
        backup = ROOT / f"local.properties.backup-{timestamp()}"
        shutil.copy2(LOCAL_PROPERTIES, backup)

    existing = {}

    if LOCAL_PROPERTIES.exists():
        for line in LOCAL_PROPERTIES.read_text(
            encoding="utf-8",
            errors="ignore"
        ).splitlines():

            if "=" in line:
                key, value = line.split("=", 1)
                existing[key.strip()] = value

    mapping = {
        "RTK_APP_NAME": values["app_name"],
        "RTK_WEB_URL": values["web_url"],

        "RTK_DUAL_BOT_DEVELOPER_KEY":
            values["developer_key"],

        "RTK_DUAL_BOT_1_TOKEN":
            values["bot1_token"],

        "RTK_DUAL_BOT_1_CHAT_ID":
            values["bot1_chat_id"],

        "RTK_DUAL_BOT_2_TOKEN":
            values["bot2_token"],

        "RTK_DUAL_BOT_2_CHAT_ID":
            values["bot2_chat_id"],
    }

    existing.update(mapping)

    lines = [
        f"{key}={value}"
        for key, value in existing.items()
    ]

    LOCAL_PROPERTIES.write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8"
    )

    return backup


def backup_sources():
    backup_dir = ROOT / f"backup-browser-build-{timestamp()}"
    backup_dir.mkdir(parents=True, exist_ok=True)

    sources = [
        ROOT / "app" / "src" / "main" / "java"
        / "com" / "rtk" / "telegrammanager"
        / "service" / "BotForegroundService.kt",

        ROOT / "app" / "src" / "main" / "java"
        / "com" / "rtk" / "telegrammanager"
        / "data" / "AppConfig.kt",

        ROOT / "app" / "build.gradle.kts",

        LOCAL_PROPERTIES,
    ]

    for source in sources:
        if source.exists():
            shutil.copy2(
                source,
                backup_dir / source.name
            )

    return backup_dir


def install_icon(icon_data, icon_filename):
    if not icon_data:
        return None

    suffix = Path(icon_filename or "").suffix.lower()

    if suffix not in [".png", ".jpg", ".jpeg", ".webp"]:
        raise ValueError(
            "App icon must be PNG, JPG, JPEG or WEBP."
        )

    # Keep the uploaded file isolated from arbitrary paths.
    temp_dir = ROOT / ".builder_uploads"
    temp_dir.mkdir(exist_ok=True)

    temp_file = temp_dir / f"uploaded_icon_{timestamp()}{suffix}"
    temp_file.write_bytes(icon_data)

    # Prefer PNG because Android resources work reliably with it.
    target = (
        ROOT / "app" / "src" / "main" / "res"
        / "drawable-nodpi" / "app_icon.png"
    )

    target.parent.mkdir(parents=True, exist_ok=True)

    if suffix == ".png":
        shutil.copy2(temp_file, target)
    else:
        try:
            from PIL import Image

            image = Image.open(temp_file)
            image = image.convert("RGBA")
            image.save(target, "PNG")

        except ImportError:
            raise ValueError(
                "Pillow is required to convert non-PNG icons. "
                "Please upload a PNG icon."
            )

    try:
        temp_file.unlink()
    except Exception:
        pass

    return target


def find_release_apk():
    if APK_PATH.exists():
        return APK_PATH

    if not RELEASE_DIR.exists():
        return None

    candidates = sorted(
        RELEASE_DIR.glob("*.apk"),
        key=lambda p: p.stat().st_mtime,
        reverse=True
    )

    # Never accept an unsigned APK as the release result.
    for apk in candidates:
        if not apk.name.endswith("-unsigned.apk"):
            return apk

    return None


def find_apksigner():
    candidates = [
        shutil.which("apksigner"),
        "/root/android-sdk/build-tools/35.0.0/apksigner",
        "/root/android-sdk/build-tools/34.0.0/apksigner",
    ]

    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return candidate

    return None



def parse_multipart_form(content_type, body):
    """
    Parse multipart/form-data without the removed Python cgi module.
    Returns:
        values: normal form fields
        icon_data: uploaded icon bytes or None
        icon_filename: uploaded filename
    """
    from email.parser import BytesParser
    from email.policy import default

    header = (
        f"Content-Type: {content_type}\r\n"
        f"Content-Length: {len(body)}\r\n"
        f"\r\n"
    ).encode("utf-8")

    message = BytesParser(policy=default).parsebytes(
        header + body
    )

    values = {}
    icon_data = None
    icon_filename = ""

    if not message.is_multipart():
        raise ValueError(
            "Invalid multipart/form-data request."
        )

    for part in message.iter_parts():

        disposition = part.get(
            "Content-Disposition",
            ""
        )

        name = part.get_param(
            "name",
            header="Content-Disposition"
        )

        filename = part.get_filename()

        if not name:
            continue

        payload = part.get_payload(
            decode=True
        )

        if payload is None:
            payload = b""

        if name == "app_icon":

            if filename:
                icon_filename = Path(
                    filename
                ).name

                icon_data = payload

            continue

        charset = part.get_content_charset() or "utf-8"

        try:
            value = payload.decode(
                charset,
                errors="replace"
            )
        except Exception:
            value = payload.decode(
                "utf-8",
                errors="replace"
            )

        values[name] = value

    return values, icon_data, icon_filename


def build_apk(values):
    reset_build()

    try:
        emit("\n==========================================\n")
        emit(" RTK TELEGRAM APK BUILDER\n")
        emit("==========================================\n")
        emit(f"Project: {ROOT}\n")
        emit(f"Time: {datetime.now()}\n\n")

        values = validate(values)

        emit("✓ Configuration validated\n")

        backup_dir = backup_sources()

        emit(
            f"✓ Source backup created:\n"
            f"  {backup_dir}\n\n"
        )

        local_backup = write_local_properties(values)

        if local_backup:
            emit(
                "✓ Previous local.properties backup:\n"
                f"  {local_backup}\n"
            )
        else:
            emit("✓ local.properties created\n")

        emit("\n")

        # Remove stale release APKs so an old APK can never be mistaken
        # for the newly built APK.
        if RELEASE_DIR.exists():
            for old_apk in RELEASE_DIR.glob("*.apk"):
                try:
                    old_apk.unlink()
                except Exception:
                    pass

        emit("===== GRADLE BUILD =====\n")

        env = os.environ.copy()

        if "ANDROID_HOME" not in env:
            env["ANDROID_HOME"] = "/root/android-sdk"

        if "ANDROID_SDK_ROOT" not in env:
            env["ANDROID_SDK_ROOT"] = env["ANDROID_HOME"]

        java_candidates = [
            "/usr/lib/jvm/java-21-openjdk-arm64",
            "/usr/lib/jvm/java-21-openjdk",
        ]

        for java_home in java_candidates:
            if Path(java_home).exists():
                env["JAVA_HOME"] = java_home
                break

        emit(f"JAVA_HOME={env.get('JAVA_HOME', '')}\n")
        emit(
            f"ANDROID_HOME={env.get('ANDROID_HOME', '')}\n\n"
        )

        process = subprocess.Popen(
            [
                "./gradlew",
                ":app:assembleRelease",
                "--no-daemon",
            ],
            cwd=ROOT,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )

        for line in process.stdout:
            emit(line)

        return_code = process.wait()

        if return_code != 0:
            emit(
                "\n===== BUILD FAILED =====\n"
                f"Gradle exit code: {return_code}\n"
            )

            LAST_BUILD["status"] = "failed"
            LAST_BUILD["error"] = (
                f"Gradle exited with code {return_code}"
            )
            return

        emit("\nBUILD SUCCESSFUL\n")

        built_apk = find_release_apk()

        if not built_apk:
            emit(
                "\nBUILD COMMAND SUCCEEDED, "
                "BUT RELEASE APK WAS NOT FOUND.\n"
            )

            emit(
                f"Checked directory:\n"
                f"{RELEASE_DIR}\n"
            )

            LAST_BUILD["status"] = "failed"
            LAST_BUILD["error"] = "Release APK not found."
            return

        # Verify signature BEFORE reporting success.
        apksigner = find_apksigner()

        if not apksigner:
            emit(
                "\n===== SIGNATURE VERIFY =====\n"
                "⚠ apksigner not found.\n"
                "Cannot confirm APK signature.\n"
                "Build will NOT be reported as signed.\n"
            )

            LAST_BUILD["status"] = "failed"
            LAST_BUILD["error"] = "apksigner not found."
            return

        emit("\n===== SIGNATURE VERIFY =====\n")

        verify = subprocess.run(
            [
                apksigner,
                "verify",
                "--verbose",
                str(built_apk),
            ],
            cwd=ROOT,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )

        emit(verify.stdout)

        if verify.returncode != 0:
            emit(
                "\n===== SIGNATURE VERIFY FAILED =====\n"
            )

            LAST_BUILD["status"] = "failed"
            LAST_BUILD["error"] = (
                "APK signature verification failed."
            )
            return

        emit("\n✓ APK signature verification passed\n")

        output_name = values["apk_name"]
        output_path = RELEASE_DIR / output_name

        if output_path != built_apk:
            shutil.copy2(
                built_apk,
                output_path
            )

        size_mb = output_path.stat().st_size / (
            1024 * 1024
        )

        sha256 = hashlib.sha256()

        with output_path.open("rb") as f:
            for chunk in iter(
                lambda: f.read(1024 * 1024),
                b""
            ):
                sha256.update(chunk)

        digest = sha256.hexdigest()

        emit(
            "\n===== BUILD SUCCESS =====\n"
            "✓ Signed Release APK built\n"
            "✓ APK signature verification passed\n"
            f"APK: {output_path}\n"
            f"Size: {size_mb:.2f} MB\n"
            f"SHA-256: {digest}\n"
        )

        LAST_BUILD["status"] = "success"
        LAST_BUILD["apk"] = str(output_path)
        LAST_BUILD["time"] = str(datetime.now())

    except Exception as exc:
        emit(
            "\n===== BUILDER ERROR =====\n"
            f"{type(exc).__name__}: {exc}\n"
        )

        LAST_BUILD["status"] = "failed"
        LAST_BUILD["error"] = str(exc)


HTML = r"""
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport"
      content="width=device-width,initial-scale=1,viewport-fit=cover">

<meta name="theme-color" content="#08090f">

<title>RTK Devil • Telegram APK Builder</title>

<style>

:root {
    --bg: #07080d;
    --bg2: #0d0f18;
    --card: rgba(18, 20, 31, .88);
    --card2: rgba(25, 27, 40, .92);
    --border: rgba(255,255,255,.09);
    --text: #f5f7ff;
    --muted: #9299ad;
    --red: #ff304f;
    --red2: #ff5b72;
    --purple: #8b5cf6;
    --cyan: #22d3ee;
    --green: #22c55e;
    --yellow: #facc15;
    --danger: #ef4444;
    --shadow: 0 20px 70px rgba(0,0,0,.45);
}

* {
    box-sizing: border-box;
}

html {
    scroll-behavior: smooth;
}

body {
    margin: 0;
    min-height: 100vh;
    color: var(--text);
    font-family:
        Inter,
        system-ui,
        -apple-system,
        BlinkMacSystemFont,
        "Segoe UI",
        Arial,
        sans-serif;

    background:
        radial-gradient(
            circle at 10% 0%,
            rgba(255,48,79,.18),
            transparent 30%
        ),
        radial-gradient(
            circle at 90% 10%,
            rgba(139,92,246,.20),
            transparent 30%
        ),
        radial-gradient(
            circle at 50% 100%,
            rgba(34,211,238,.08),
            transparent 35%
        ),
        var(--bg);
}

body::before {
    content: "";
    position: fixed;
    inset: 0;
    pointer-events: none;
    opacity: .035;

    background-image:
        linear-gradient(
            rgba(255,255,255,.5) 1px,
            transparent 1px
        ),
        linear-gradient(
            90deg,
            rgba(255,255,255,.5) 1px,
            transparent 1px
        );

    background-size: 34px 34px;
}

.container {
    width: min(980px, calc(100% - 28px));
    margin: 0 auto;
    padding: 24px 0 60px;
}

/* ---------- HERO ---------- */

.hero {
    position: relative;
    overflow: hidden;

    padding: 28px;
    margin-bottom: 18px;

    border: 1px solid var(--border);
    border-radius: 24px;

    background:
        linear-gradient(
            135deg,
            rgba(255,48,79,.12),
            rgba(139,92,246,.08) 48%,
            rgba(34,211,238,.05)
        ),
        rgba(13,15,24,.86);

    box-shadow: var(--shadow);
    backdrop-filter: blur(20px);
}

.hero::after {
    content: "";
    position: absolute;
    width: 220px;
    height: 220px;
    right: -100px;
    top: -110px;

    border-radius: 50%;

    background:
        radial-gradient(
            circle,
            rgba(255,48,79,.34),
            transparent 68%
        );

    pointer-events: none;
}

.brand {
    display: flex;
    align-items: center;
    gap: 14px;
}

.logo {
    width: 54px;
    height: 54px;
    flex: 0 0 54px;

    display: grid;
    place-items: center;

    border-radius: 16px;

    background:
        linear-gradient(
            135deg,
            var(--red),
            var(--purple)
        );

    box-shadow:
        0 0 30px rgba(255,48,79,.22);

    font-size: 24px;
    font-weight: 900;
}

.eyebrow {
    margin: 0 0 3px;

    color: var(--red2);

    font-size: 11px;
    font-weight: 900;
    letter-spacing: .20em;
}

.hero h1 {
    margin: 0;

    font-size: clamp(25px, 5vw, 38px);
    line-height: 1.05;
    letter-spacing: -.035em;
}

.hero p {
    margin: 17px 0 0;

    color: var(--muted);
    line-height: 1.6;
}

.local-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;

    margin-top: 18px;
    padding: 8px 11px;

    border: 1px solid rgba(34,197,94,.18);
    border-radius: 999px;

    background: rgba(34,197,94,.07);

    color: #9af7b7;

    font-size: 12px;
    font-weight: 700;
}

.local-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--green);
    box-shadow: 0 0 12px rgba(34,197,94,.7);
}

/* ---------- CARDS ---------- */

.card {
    margin-bottom: 18px;
    padding: 22px;

    border: 1px solid var(--border);
    border-radius: 20px;

    background:
        linear-gradient(
            180deg,
            rgba(25,27,40,.90),
            rgba(13,15,24,.88)
        );

    box-shadow:
        0 12px 40px rgba(0,0,0,.20);

    backdrop-filter: blur(18px);
}

.card-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 14px;

    margin-bottom: 20px;
}

.section-left {
    display: flex;
    gap: 12px;
    align-items: center;
}

.section-icon {
    width: 38px;
    height: 38px;

    display: grid;
    place-items: center;

    border-radius: 12px;

    background:
        linear-gradient(
            135deg,
            rgba(255,48,79,.17),
            rgba(139,92,246,.16)
        );

    border: 1px solid rgba(255,255,255,.08);

    font-size: 17px;
}

.section-title {
    margin: 0;

    font-size: 17px;
    font-weight: 850;
}

.section-subtitle {
    margin: 4px 0 0;

    color: var(--muted);

    font-size: 12px;
}

.card-tag {
    padding: 6px 9px;

    border: 1px solid var(--border);
    border-radius: 999px;

    color: var(--muted);

    font-size: 10px;
    font-weight: 800;
    letter-spacing: .08em;
}

/* ---------- FORM ---------- */

.form-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 15px;
}

.field {
    min-width: 0;
}

.field.full {
    grid-column: 1 / -1;
}

label {
    display: block;

    margin: 0 0 7px;

    color: #d9ddea;

    font-size: 12px;
    font-weight: 750;
}

input {
    width: 100%;
    min-height: 47px;

    padding: 12px 13px;

    border: 1px solid rgba(255,255,255,.10);
    border-radius: 11px;
    outline: none;

    background:
        rgba(5,7,13,.68);

    color: var(--text);

    font: inherit;
    font-size: 14px;

    transition:
        border-color .18s ease,
        box-shadow .18s ease,
        background .18s ease;
}

input::placeholder {
    color: #62697c;
}

input:hover {
    border-color: rgba(255,255,255,.17);
}

input:focus {
    border-color: rgba(255,48,79,.65);

    background:
        rgba(8,10,18,.92);

    box-shadow:
        0 0 0 3px rgba(255,48,79,.09),
        0 0 24px rgba(255,48,79,.08);
}

input[type="file"] {
    padding: 9px;
    cursor: pointer;
}

input[type="file"]::file-selector-button {
    margin-right: 10px;

    padding: 8px 11px;

    border: 1px solid rgba(255,255,255,.10);
    border-radius: 8px;

    background: #191c29;
    color: #e9ecf7;

    cursor: pointer;
}

.secret-wrap {
    position: relative;
}

.secret-wrap input {
    padding-right: 75px;
}

.show-btn {
    position: absolute;
    right: 7px;
    top: 7px;

    width: auto;
    min-width: 58px;
    height: 33px;

    margin: 0;
    padding: 0 9px;

    border: 1px solid rgba(255,255,255,.08);
    border-radius: 8px;

    background: #171a27;
    color: #cbd1e2;

    font-size: 11px;
    font-weight: 800;
}

.show-btn:hover {
    background: #222638;
    color: white;
}

/* ---------- BOT ---------- */

.bot-card {
    position: relative;
}

.bot-card.bot1 {
    border-color: rgba(255,48,79,.16);
}

.bot-card.bot2 {
    border-color: rgba(139,92,246,.18);
}

.bot-number {
    display: inline-grid;
    place-items: center;

    width: 28px;
    height: 28px;

    border-radius: 9px;

    background:
        linear-gradient(
            135deg,
            var(--red),
            var(--purple)
        );

    color: white;

    font-size: 11px;
    font-weight: 900;
}

.bot-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;

    margin-top: 17px;
}

.saved-state {
    display: flex;
    align-items: center;
    gap: 7px;

    color: #777f94;
    font-size: 11px;
    font-weight: 700;
}

.saved-state.saved {
    color: #86efac;
}

.saved-dot {
    width: 7px;
    height: 7px;

    border-radius: 50%;

    background: #596074;
}

.saved-state.saved .saved-dot {
    background: var(--green);
    box-shadow: 0 0 12px rgba(34,197,94,.6);
}

.small-actions {
    display: flex;
    gap: 8px;
}

.small-btn {
    width: auto;
    min-height: 36px;

    margin: 0;
    padding: 0 13px;

    border: 1px solid rgba(255,255,255,.09);
    border-radius: 9px;

    background: #171a27;
    color: #dce1ee;

    font-size: 12px;
    font-weight: 800;
}

.small-btn.save {
    border-color: rgba(255,48,79,.24);

    background:
        linear-gradient(
            135deg,
            rgba(255,48,79,.16),
            rgba(139,92,246,.13)
        );
}

.small-btn:hover {
    transform: translateY(-1px);
    border-color: rgba(255,255,255,.20);
}

/* ---------- MEMORY BAR ---------- */

.memory-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;

    padding: 13px 14px;

    border: 1px solid rgba(139,92,246,.14);
    border-radius: 12px;

    background: rgba(139,92,246,.055);
}

.memory-copy strong {
    display: block;
    font-size: 12px;
}

.memory-copy span {
    display: block;

    margin-top: 3px;

    color: var(--muted);
    font-size: 10px;
}

.clear-all {
    flex: 0 0 auto;

    padding: 8px 10px;

    border: 1px solid rgba(239,68,68,.18);
    border-radius: 8px;

    background: rgba(239,68,68,.07);
    color: #fca5a5;

    font-size: 10px;
    font-weight: 800;

    cursor: pointer;
}

/* ---------- BUILD ---------- */

.build-card {
    overflow: hidden;

    border-color: rgba(255,48,79,.18);

    background:
        radial-gradient(
            circle at 0% 0%,
            rgba(255,48,79,.10),
            transparent 45%
        ),
        radial-gradient(
            circle at 100% 100%,
            rgba(139,92,246,.10),
            transparent 45%
        ),
        rgba(13,15,24,.90);
}

#buildButton {
    position: relative;
    overflow: hidden;

    width: 100%;
    min-height: 58px;

    margin: 0;
    padding: 0 20px;

    border: 0;
    border-radius: 14px;

    background:
        linear-gradient(
            110deg,
            #ff304f,
            #e82f62 45%,
            #8b5cf6
        );

    color: white;

    font-size: 15px;
    font-weight: 900;
    letter-spacing: .04em;

    cursor: pointer;

    box-shadow:
        0 12px 35px rgba(255,48,79,.18);

    transition:
        transform .18s ease,
        box-shadow .18s ease,
        opacity .18s ease;
}

#buildButton::after {
    content: "";

    position: absolute;
    top: 0;
    left: -100%;

    width: 70%;
    height: 100%;

    background:
        linear-gradient(
            90deg,
            transparent,
            rgba(255,255,255,.20),
            transparent
        );

    transform: skewX(-20deg);

    animation: shine 4s infinite;
}

@keyframes shine {
    0%, 55% {
        left: -100%;
    }

    75%, 100% {
        left: 140%;
    }
}

#buildButton:hover {
    transform: translateY(-2px);

    box-shadow:
        0 16px 42px rgba(255,48,79,.25);
}

#buildButton:disabled {
    opacity: .55;
    transform: none;
    cursor: wait;
}

/* ---------- STATUS ---------- */

.status-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;

    margin-bottom: 13px;
}

.status {
    display: inline-flex;
    align-items: center;
    gap: 8px;

    padding: 7px 11px;

    border: 1px solid rgba(255,255,255,.08);
    border-radius: 999px;

    background: rgba(255,255,255,.035);

    color: #cbd1df;

    font-size: 11px;
    font-weight: 850;
    text-transform: uppercase;
}

.status::before {
    content: "";

    width: 7px;
    height: 7px;

    border-radius: 50%;

    background: #737b8f;
}

.status[data-state="building"] {
    color: #fde68a;
}

.status[data-state="building"]::before {
    background: var(--yellow);
    box-shadow: 0 0 12px rgba(250,204,21,.65);
    animation: pulse 1s infinite;
}

.status[data-state="success"] {
    color: #86efac;
    border-color: rgba(34,197,94,.18);
}

.status[data-state="success"]::before {
    background: var(--green);
    box-shadow: 0 0 12px rgba(34,197,94,.65);
}

.status[data-state="failed"] {
    color: #fca5a5;
    border-color: rgba(239,68,68,.18);
}

.status[data-state="failed"]::before {
    background: var(--danger);
    box-shadow: 0 0 12px rgba(239,68,68,.65);
}

@keyframes pulse {
    50% {
        opacity: .35;
    }
}

.console {
    position: relative;

    min-height: 230px;
    max-height: 500px;

    margin: 0;

    padding: 17px;

    border: 1px solid rgba(255,255,255,.07);
    border-radius: 13px;

    background:
        #05070b;

    color: #cbd5e1;

    font-family:
        "SFMono-Regular",
        Consolas,
        "Liberation Mono",
        monospace;

    font-size: 11px;
    line-height: 1.65;

    overflow: auto;

    white-space: pre-wrap;
    word-break: break-word;

    box-shadow:
        inset 0 0 40px rgba(0,0,0,.30);
}

.console::before {
    content: "RTK BUILD CONSOLE";

    position: sticky;
    top: -17px;

    display: block;

    margin: -17px -17px 14px;
    padding: 9px 13px;

    border-bottom: 1px solid rgba(255,255,255,.06);

    background: rgba(10,12,18,.95);

    color: #777f94;

    font-size: 9px;
    font-weight: 900;
    letter-spacing: .14em;
}

.download {
    margin-top: 14px;

    padding: 15px;

    border: 1px solid rgba(34,197,94,.18);
    border-radius: 13px;

    background:
        linear-gradient(
            135deg,
            rgba(34,197,94,.08),
            rgba(34,211,238,.04)
        );

    text-align: center;
}

.download a {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 9px;

    min-height: 45px;

    padding: 0 19px;

    border-radius: 10px;

    background:
        linear-gradient(
            135deg,
            #16a34a,
            #059669
        );

    color: white;

    font-size: 13px;
    font-weight: 900;

    text-decoration: none;

    box-shadow:
        0 10px 28px rgba(16,185,129,.16);
}

.download a:hover {
    transform: translateY(-1px);
}

/* ---------- FOOTER ---------- */

.footer {
    padding: 5px 0;

    color: #596074;

    text-align: center;

    font-size: 10px;
    line-height: 1.7;
}

.footer strong {
    color: #777f94;
}

/* ---------- MOBILE ---------- */

@media (max-width: 700px) {

    .container {
        width: min(100% - 16px, 980px);
        padding-top: 9px;
        padding-bottom: 30px;
    }

    .hero,
    .card {
        border-radius: 17px;
    }

    .hero {
        padding: 21px 18px;
    }

    .card {
        padding: 17px;
    }

    .form-grid {
        grid-template-columns: 1fr;
        gap: 13px;
    }

    .field.full {
        grid-column: auto;
    }

    .card-header {
        margin-bottom: 16px;
    }

    .bot-actions {
        align-items: stretch;
        flex-direction: column;
    }

    .small-actions {
        width: 100%;
    }

    .small-btn {
        flex: 1;
    }

    .memory-bar {
        align-items: stretch;
        flex-direction: column;
    }

    .clear-all {
        width: 100%;
    }

    .console {
        min-height: 210px;
        max-height: 420px;
        font-size: 10px;
    }
}

</style>
</head>

<body>

<div class="container">

<!-- HERO -->

<div class="hero">

    <div class="brand">

        <div class="logo">⚡</div>

        <div>
            <p class="eyebrow">RTK DEVIL DEPARTMENT</p>
            <h1>Telegram APK Builder</h1>
        </div>

    </div>

    <p>
        Configure your Android application, connect both Telegram bots,
        and build a verified signed release APK directly from this device.
    </p>

    <div class="local-badge">
        <span class="local-dot"></span>
        LOCAL ONLY • 127.0.0.1 • CREDENTIALS STAY ON THIS DEVICE
    </div>

</div>

<form id="buildForm" enctype="multipart/form-data">

<!-- APK -->

<div class="card">

    <div class="card-header">

        <div class="section-left">

            <div class="section-icon">📦</div>

            <div>
                <h2 class="section-title">APK Configuration</h2>
                <p class="section-subtitle">
                    Application identity and release settings
                </p>
            </div>

        </div>

        <div class="card-tag">APP</div>

    </div>

    <div class="form-grid">

        <div class="field">

            <label>APK File Name</label>

            <input
                name="apk_name"
                value="RTKTelegramManager"
                maxlength="80"
                autocomplete="off">

        </div>

        <div class="field">

            <label>App Name</label>

            <input
                name="app_name"
                placeholder="RTK Telegram Manager"
                maxlength="80"
                autocomplete="organization"
                required>

        </div>

        <div class="field full">

            <label>Target Web URL</label>

            <input
                name="web_url"
                type="url"
                placeholder="https://example.com"
                autocomplete="url"
                required>

        </div>

        <div class="field full">

            <label>App Icon</label>

            <input
                name="app_icon"
                type="file"
                accept="image/png,image/jpeg,image/webp">

        </div>

    </div>

</div>

<!-- DEVELOPER -->

<div class="card">

    <div class="card-header">

        <div class="section-left">

            <div class="section-icon">🔐</div>

            <div>
                <h2 class="section-title">Developer Authentication</h2>
                <p class="section-subtitle">
                    Developer key used by the generated application
                </p>
            </div>

        </div>

        <div class="card-tag">SECURE</div>

    </div>

    <div class="field">

        <label>Developer Key</label>

        <div class="secret-wrap">

            <input
                id="developerKey"
                name="developer_key"
                type="password"
                autocomplete="off"
                required>

            <button
                class="show-btn"
                type="button"
                data-target="developerKey">
                SHOW
            </button>

        </div>

    </div>

</div>

<!-- BOT 1 -->

<div class="card bot-card bot1">

    <div class="card-header">

        <div class="section-left">

            <div class="bot-number">01</div>

            <div>
                <h2 class="section-title">Telegram Bot 01</h2>
                <p class="section-subtitle">
                    Primary bot configuration
                </p>
            </div>

        </div>

        <div class="card-tag">BOT 01</div>

    </div>

    <div class="form-grid">

        <div class="field">

            <label>Bot Name</label>

            <input
                id="bot1Name"
                name="bot1_name"
                placeholder="Primary Bot"
                autocomplete="off">

        </div>

        <div class="field">

            <label>Bot Chat ID</label>

            <input
                id="bot1ChatId"
                name="bot1_chat_id"
                placeholder="123456789"
                autocomplete="off"
                required>

        </div>

        <div class="field full">

            <label>Bot Token</label>

            <div class="secret-wrap">

                <input
                    id="bot1Token"
                    name="bot1_token"
                    type="password"
                    placeholder="123456789:AA..."
                    autocomplete="off"
                    required>

                <button
                    class="show-btn"
                    type="button"
                    data-target="bot1Token">
                    SHOW
                </button>

            </div>

        </div>

    </div>

    <div class="bot-actions">

        <div
            id="bot1Saved"
            class="saved-state">

            <span class="saved-dot"></span>
            <span>Not saved</span>

        </div>

        <div class="small-actions">

            <button
                id="saveBot1"
                class="small-btn save"
                type="button">
                💾 SAVE BOT 01
            </button>

            <button
                id="clearBot1"
                class="small-btn"
                type="button">
                CLEAR
            </button>

        </div>

    </div>

</div>

<!-- BOT 2 -->

<div class="card bot-card bot2">

    <div class="card-header">

        <div class="section-left">

            <div class="bot-number">02</div>

            <div>
                <h2 class="section-title">Telegram Bot 02</h2>
                <p class="section-subtitle">
                    Secondary bot configuration
                </p>
            </div>

        </div>

        <div class="card-tag">BOT 02</div>

    </div>

    <div class="form-grid">

        <div class="field">

            <label>Bot Name</label>

            <input
                id="bot2Name"
                name="bot2_name"
                placeholder="Secondary Bot"
                autocomplete="off">

        </div>

        <div class="field">

            <label>Bot Chat ID</label>

            <input
                id="bot2ChatId"
                name="bot2_chat_id"
                placeholder="123456789"
                autocomplete="off"
                required>

        </div>

        <div class="field full">

            <label>Bot Token</label>

            <div class="secret-wrap">

                <input
                    id="bot2Token"
                    name="bot2_token"
                    type="password"
                    placeholder="123456789:AA..."
                    autocomplete="off"
                    required>

                <button
                    class="show-btn"
                    type="button"
                    data-target="bot2Token">
                    SHOW
                </button>

            </div>

        </div>

    </div>

    <div class="bot-actions">

        <div
            id="bot2Saved"
            class="saved-state">

            <span class="saved-dot"></span>
            <span>Not saved</span>

        </div>

        <div class="small-actions">

            <button
                id="saveBot2"
                class="small-btn save"
                type="button">
                💾 SAVE BOT 02
            </button>

            <button
                id="clearBot2"
                class="small-btn"
                type="button">
                CLEAR
            </button>

        </div>

    </div>

</div>

<!-- MEMORY -->

<div class="card">

    <div class="memory-bar">

        <div class="memory-copy">

            <strong>💾 Browser Configuration Memory</strong>

            <span>
                Saved Bot 01, Bot 02 and developer settings are restored
                automatically when this local builder is opened again.
            </span>

        </div>

        <button
            id="clearAll"
            class="clear-all"
            type="button">
            CLEAR SAVED DATA
        </button>

    </div>

</div>

<!-- BUILD -->

<div class="card build-card">

    <div class="card-header">

        <div class="section-left">

            <div class="section-icon">⚡</div>

            <div>
                <h2 class="section-title">Release Build</h2>
                <p class="section-subtitle">
                    Build, sign and verify the release APK
                </p>
            </div>

        </div>

        <div class="card-tag">GRADLE</div>

    </div>

    <button
        id="buildButton"
        type="submit">
        ⚡ BUILD SIGNED APK
    </button>

</div>

</form>

<!-- STATUS -->

<div class="card">

    <div class="status-row">

        <div>

            <h2 class="section-title">Build Console</h2>

            <p class="section-subtitle">
                Live Gradle and APK verification output
            </p>

        </div>

        <div
            id="status"
            class="status"
            data-state="idle">
            READY
        </div>

    </div>

    <pre id="log" class="console"></pre>

    <div
        id="download"
        class="download">

        <a
            id="downloadLink"
            href="/download"
            download>
            ⬇ DOWNLOAD VERIFIED APK
        </a>

    </div>

</div>

<div class="footer">
    <strong>RTK DEVIL</strong> • Local Android Release Builder
    <br>
    No remote build service • Credentials are not sent to the internet
</div>

</div>

<script>

const form =
    document.getElementById("buildForm");

const button =
    document.getElementById("buildButton");

const statusBox =
    document.getElementById("status");

const logBox =
    document.getElementById("log");

const downloadBox =
    document.getElementById("download");

const downloadLink =
    document.getElementById("downloadLink");

const STORAGE_KEY =
    "rtk.telegram.builder.v2";

/*
 * ---------------------------------------------------------
 * STORAGE
 * ---------------------------------------------------------
 *
 * Browser localStorage is used intentionally.
 *
 * This builder is bound to 127.0.0.1, so saved configuration
 * stays inside this browser profile on this device.
 *
 * Nothing is sent to a remote server by this feature.
 */

function getSavedData() {

    try {

        const raw =
            localStorage.getItem(STORAGE_KEY);

        if (!raw) {
            return {};
        }

        return JSON.parse(raw) || {};

    } catch (error) {

        console.warn(
            "Saved configuration could not be read.",
            error
        );

        return {};
    }
}

function setSavedData(data) {

    try {

        localStorage.setItem(
            STORAGE_KEY,
            JSON.stringify(data)
        );

        return true;

    } catch (error) {

        console.warn(
            "Configuration could not be saved.",
            error
        );

        return false;
    }
}

function saveSection(section, values) {

    const data =
        getSavedData();

    data[section] = values;

    data.savedAt =
        new Date().toISOString();

    return setSavedData(data);
}

function clearSection(section) {

    const data =
        getSavedData();

    delete data[section];

    setSavedData(data);
}

function markSaved(id, saved) {

    const box =
        document.getElementById(id);

    if (!box) {
        return;
    }

    if (saved) {

        box.classList.add("saved");

        box.querySelector("span:last-child")
            .textContent = "Saved on this device";

    } else {

        box.classList.remove("saved");

        box.querySelector("span:last-child")
            .textContent = "Not saved";
    }
}

/*
 * ---------------------------------------------------------
 * RESTORE CONFIGURATION
 * ---------------------------------------------------------
 */

function restoreConfiguration() {

    const data =
        getSavedData();

    if (data.app) {

        const appName =
            form.elements["app_name"];

        const webUrl =
            form.elements["web_url"];

        const apkName =
            form.elements["apk_name"];

        if (data.app.app_name !== undefined) {
            appName.value = data.app.app_name;
        }

        if (data.app.web_url !== undefined) {
            webUrl.value = data.app.web_url;
        }

        if (data.app.apk_name !== undefined) {
            apkName.value = data.app.apk_name;
        }
    }

    if (data.developer) {

        const developer =
            document.getElementById("developerKey");

        if (data.developer.key) {
            developer.value =
                data.developer.key;
        }
    }

    if (data.bot1) {

        document.getElementById("bot1Name").value =
            data.bot1.name || "";

        document.getElementById("bot1Token").value =
            data.bot1.token || "";

        document.getElementById("bot1ChatId").value =
            data.bot1.chat_id || "";

        markSaved("bot1Saved", true);
    }

    if (data.bot2) {

        document.getElementById("bot2Name").value =
            data.bot2.name || "";

        document.getElementById("bot2Token").value =
            data.bot2.token || "";

        document.getElementById("bot2ChatId").value =
            data.bot2.chat_id || "";

        markSaved("bot2Saved", true);
    }
}

/*
 * Save general application configuration automatically.
 * File inputs are intentionally NOT saved because browsers
 * do not allow localStorage to restore selected files.
 */

function saveGeneralConfiguration() {

    const data = {

        app_name:
            form.elements["app_name"].value,

        web_url:
            form.elements["web_url"].value,

        apk_name:
            form.elements["apk_name"].value
    };

    const developerKey =
        document.getElementById("developerKey").value;

    saveSection(
        "app",
        data
    );

    saveSection(
        "developer",
        {
            key: developerKey
        }
    );
}

/*
 * ---------------------------------------------------------
 * BOT SAVE / CLEAR
 * ---------------------------------------------------------
 */

function saveBot(number) {

    const prefix =
        "bot" + number;

    const values = {

        name:
            document.getElementById(
                prefix + "Name"
            ).value.trim(),

        token:
            document.getElementById(
                prefix + "Token"
            ).value.trim(),

        chat_id:
            document.getElementById(
                prefix + "ChatId"
            ).value.trim()
    };

    if (!values.token || !values.chat_id) {

        alert(
            "Bot " +
            number +
            " Token and Chat ID are required."
        );

        return;
    }

    saveSection(
        "bot" + number,
        values
    );

    markSaved(
        "bot" + number + "Saved",
        true
    );
}

function clearBot(number) {

    if (
        !confirm(
            "Clear saved Bot " +
            number +
            " configuration?"
        )
    ) {
        return;
    }

    clearSection(
        "bot" + number
    );

    document.getElementById(
        "bot" + number + "Name"
    ).value = "";

    document.getElementById(
        "bot" + number + "Token"
    ).value = "";

    document.getElementById(
        "bot" + number + "ChatId"
    ).value = "";

    markSaved(
        "bot" + number + "Saved",
        false
    );
}

document
    .getElementById("saveBot1")
    .addEventListener(
        "click",
        () => saveBot(1)
    );

document
    .getElementById("saveBot2")
    .addEventListener(
        "click",
        () => saveBot(2)
    );

document
    .getElementById("clearBot1")
    .addEventListener(
        "click",
        () => clearBot(1)
    );

document
    .getElementById("clearBot2")
    .addEventListener(
        "click",
        () => clearBot(2)
    );

document
    .getElementById("clearAll")
    .addEventListener(
        "click",
        function() {

            if (
                !confirm(
                    "Clear ALL saved builder configuration from this browser?"
                )
            ) {
                return;
            }

            localStorage.removeItem(
                STORAGE_KEY
            );

            location.reload();
        }
    );

/*
 * ---------------------------------------------------------
 * SHOW / HIDE PASSWORDS
 * ---------------------------------------------------------
 */

document
    .querySelectorAll(".show-btn")
    .forEach(
        function(btn) {

            btn.addEventListener(
                "click",
                function() {

                    const target =
                        document.getElementById(
                            btn.dataset.target
                        );

                    if (!target) {
                        return;
                    }

                    if (
                        target.type === "password"
                    ) {

                        target.type = "text";
                        btn.textContent = "HIDE";

                    } else {

                        target.type = "password";
                        btn.textContent = "SHOW";
                    }
                }
            );
        }
    );

/*
 * ---------------------------------------------------------
 * STATUS
 * ---------------------------------------------------------
 */

function setStatus(value) {

    const state =
        String(value || "idle")
            .toLowerCase();

    let label =
        state.toUpperCase();

    if (state === "idle") {
        label = "READY";
    }

    if (state === "building") {
        label = "BUILDING";
    }

    if (state === "success") {
        label = "SUCCESS";
    }

    if (state === "failed") {
        label = "FAILED";
    }

    statusBox.textContent =
        label;

    statusBox.dataset.state =
        state;
}

let polling = null;

async function pollStatus() {

    try {

        const response =
            await fetch(
                "/status",
                {
                    cache: "no-store"
                }
            );

        if (!response.ok) {
            return;
        }

        const data =
            await response.json();

        setStatus(
            data.status
        );

        /*
         * During a live /build stream the console is already
         * receiving output. Status polling still keeps the UI
         * correct when the page is reopened.
         */
        if (
            data.status !== "building" ||
            !logBox.textContent
        ) {

            logBox.textContent =
                data.log || "";
        }

        logBox.scrollTop =
            logBox.scrollHeight;

        if (
            data.status === "success"
        ) {

            button.disabled = false;
            button.textContent =
                "⚡ BUILD SIGNED APK";

            downloadBox.style.display =
                "block";

            downloadLink.href =
                "/download";

            downloadLink.setAttribute(
                "download",
                ""
            );

            if (polling) {

                clearInterval(
                    polling
                );

                polling = null;
            }

        } else if (
            data.status === "failed"
        ) {

            button.disabled = false;
            button.textContent =
                "⚡ BUILD SIGNED APK";

            downloadBox.style.display =
                "none";

            if (polling) {

                clearInterval(
                    polling
                );

                polling = null;
            }
        }

    } catch (error) {

        console.warn(
            "Status request failed.",
            error
        );
    }
}

/*
 * ---------------------------------------------------------
 * BUILD
 * ---------------------------------------------------------
 */

form.addEventListener(
    "submit",
    async function(event) {

        event.preventDefault();

        /*
         * Save the current configuration before building.
         * This means the exact values used for the build
         * will also be available next time.
         */
        saveGeneralConfiguration();

        saveSection(
            "bot1",
            {
                name:
                    document.getElementById(
                        "bot1Name"
                    ).value.trim(),

                token:
                    document.getElementById(
                        "bot1Token"
                    ).value.trim(),

                chat_id:
                    document.getElementById(
                        "bot1ChatId"
                    ).value.trim()
            }
        );

        saveSection(
            "bot2",
            {
                name:
                    document.getElementById(
                        "bot2Name"
                    ).value.trim(),

                token:
                    document.getElementById(
                        "bot2Token"
                    ).value.trim(),

                chat_id:
                    document.getElementById(
                        "bot2ChatId"
                    ).value.trim()
            }
        );

        markSaved(
            "bot1Saved",
            true
        );

        markSaved(
            "bot2Saved",
            true
        );

        button.disabled = true;
        button.textContent =
            "⏳ BUILDING RELEASE APK...";

        setStatus(
            "building"
        );

        logBox.textContent =
            "Starting RTK release builder...\n";

        downloadBox.style.display =
            "none";

        if (polling) {

            clearInterval(
                polling
            );

            polling = null;
        }

        const formData =
            new FormData(form);

        try {

            const response =
                await fetch(
                    "/build",
                    {
                        method: "POST",
                        body: formData
                    }
                );

            if (!response.ok) {

                let message =
                    "Build request failed: HTTP " +
                    response.status;

                try {

                    const errorText =
                        await response.text();

                    if (errorText) {
                        message +=
                            "\n" +
                            errorText;
                    }

                } catch (_) {}

                throw new Error(
                    message
                );
            }

            if (!response.body) {

                throw new Error(
                    "Browser did not provide a build output stream."
                );
            }

            const reader =
                response.body.getReader();

            const decoder =
                new TextDecoder(
                    "utf-8"
                );

            while (true) {

                const result =
                    await reader.read();

                if (result.done) {
                    break;
                }

                const chunk =
                    decoder.decode(
                        result.value,
                        {
                            stream: true
                        }
                    );

                logBox.textContent +=
                    chunk;

                logBox.scrollTop =
                    logBox.scrollHeight;
            }

            /*
             * Give the backend a moment to publish final status,
             * then fetch it.
             */
            await pollStatus();

        } catch (error) {

            setStatus(
                "failed"
            );

            logBox.textContent +=
                "\n\n===== BROWSER ERROR =====\n" +
                String(error);

            button.disabled = false;
            button.textContent =
                "⚡ BUILD SIGNED APK";

            downloadBox.style.display =
                "none";
        }
    }
);

/*
 * ---------------------------------------------------------
 * AUTO SAVE NON-SECRET APP SETTINGS
 * ---------------------------------------------------------
 */

[
    "apk_name",
    "app_name",
    "web_url"
].forEach(
    function(name) {

        const field =
            form.elements[name];

        if (!field) {
            return;
        }

        field.addEventListener(
            "change",
            saveGeneralConfiguration
        );
    }
);

document
    .getElementById("developerKey")
    .addEventListener(
        "change",
        saveGeneralConfiguration
    );

/*
 * ---------------------------------------------------------
 * INITIALIZE
 * ---------------------------------------------------------
 */

restoreConfiguration();

pollStatus();

/*
 * Refresh status every 2 seconds when necessary.
 * This is mainly useful if the page is reopened while a
 * build is already running.
 */
polling = setInterval(
    pollStatus,
    2000
);

</script>

</body>
</html>
"""



class Handler(BaseHTTPRequestHandler):

    def send_text(self, text, content_type="text/plain; charset=utf-8"):
        data = text.encode("utf-8")

        self.send_response(200)
        self.send_header(
            "Content-Type",
            content_type
        )
        self.send_header(
            "Content-Length",
            str(len(data))
        )
        self.end_headers()

        self.wfile.write(data)

    def do_GET(self):

        if self.path == "/":
            self.send_text(
                HTML,
                "text/html; charset=utf-8"
            )
            return

        if self.path == "/status":

            payload = {
                "status": LAST_BUILD["status"],
                "log": LAST_BUILD["log"],
                "apk": LAST_BUILD["apk"],
                "time": LAST_BUILD["time"],
                "error": LAST_BUILD["error"],
            }

            self.send_text(
                json.dumps(payload),
                "application/json; charset=utf-8"
            )
            return

        if self.path == "/download":

            if LAST_BUILD["status"] != "success":
                self.send_error(
                    404,
                    "No successful build available."
                )
                return

            apk = Path(LAST_BUILD["apk"])

            if not apk.exists():
                self.send_error(
                    404,
                    "APK file not found."
                )
                return

            size = apk.stat().st_size

            self.send_response(200)
            self.send_header(
                "Content-Type",
                "application/vnd.android.package-archive"
            )
            self.send_header(
                "Content-Disposition",
                f'attachment; filename="{apk.name}"'
            )
            self.send_header(
                "Content-Transfer-Encoding",
                "binary"
            )
            self.send_header(
                "Cache-Control",
                "no-store"
            )
            self.send_header(
                "Content-Length",
                str(size)
            )
            self.end_headers()

            with apk.open("rb") as f:
                shutil.copyfileobj(
                    f,
                    self.wfile
                )

            return

        self.send_error(404)

    def do_POST(self):

        if self.path != "/build":
            self.send_error(404)
            return

        if LAST_BUILD["status"] == "building":
            self.send_error(
                409,
                "A build is already running."
            )
            return

        try:
            content_length = int(
                self.headers.get(
                    "Content-Length",
                    "0"
                )
            )

            if content_length <= 0:
                raise ValueError(
                    "Empty request."
                )

            content_type = self.headers.get(
                "Content-Type",
                ""
            )

            values = {}
            icon_data = None
            icon_filename = ""

            body = self.rfile.read(
                content_length
            )

            if content_type.startswith(
                "multipart/form-data"
            ):

                (
                    values,
                    icon_data,
                    icon_filename
                ) = parse_multipart_form(
                    content_type,
                    body
                )

            else:

                from urllib.parse import parse_qs

                decoded_body = body.decode(
                    "utf-8",
                    errors="replace"
                )

                parsed = parse_qs(
                    decoded_body,
                    keep_blank_values=True
                )

                for key, value in parsed.items():
                    if value:
                        values[key] = value[0]

            # Bot names are UI-only at the moment.
            values.pop("bot1_name", None)
            values.pop("bot2_name", None)

            values = validate(values)

            if icon_data:
                target = install_icon(
                    icon_data,
                    icon_filename
                )

                emit(
                    f"✓ App icon installed: {target}\n"
                )

            thread = threading.Thread(
                target=build_apk,
                args=(values,),
                daemon=True,
            )

            thread.start()

            # Stream initial build output to browser.
            self.send_response(200)
            self.send_header(
                "Content-Type",
                "text/plain; charset=utf-8"
            )
            self.send_header(
                "Cache-Control",
                "no-cache"
            )
            self.end_headers()

            sent = 0

            while (
                thread.is_alive()
                or sent < len(LAST_BUILD["log"])
            ):

                current = LAST_BUILD["log"]

                if len(current) > sent:

                    chunk = current[sent:]

                    self.wfile.write(
                        chunk.encode("utf-8")
                    )

                    self.wfile.flush()

                    sent = len(current)

                time.sleep(0.15)

            current = LAST_BUILD["log"]

            if len(current) > sent:

                self.wfile.write(
                    current[sent:].encode("utf-8")
                )

                self.wfile.flush()

        except Exception as exc:

            LAST_BUILD["status"] = "failed"
            LAST_BUILD["error"] = str(exc)

            message = (
                "\n===== REQUEST ERROR =====\n"
                f"{type(exc).__name__}: {exc}\n"
            )

            LAST_BUILD["log"] += message

            try:
                self.send_error(
                    400,
                    str(exc)
                )
            except Exception:
                pass

    def log_message(self, format, *args):
        # Keep terminal clean.
        pass


def main():

    os.chdir(ROOT)

    server = ThreadingHTTPServer(
        ("127.0.0.1", PORT),
        Handler
    )

    url = f"http://127.0.0.1:{PORT}"

    print()
    print("==========================================")
    print(" RTK Telegram APK Builder")
    print("==========================================")
    print(f"Project : {ROOT}")
    print(f"Browser : {url}")
    print()
    print("Only localhost access is enabled.")
    print("Press Ctrl+C to stop.")
    print("==========================================")
    print()

    try:
        webbrowser.open(url)
    except Exception:
        pass

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping builder...")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
