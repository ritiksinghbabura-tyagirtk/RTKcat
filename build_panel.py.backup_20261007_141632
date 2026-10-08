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
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>RTK Telegram APK Builder</title>

<style>
body {
    font-family: Arial, sans-serif;
    background: #f4f6f8;
    margin: 0;
    padding: 20px;
}

.container {
    max-width: 850px;
    margin: auto;
}

.card {
    background: white;
    border-radius: 14px;
    padding: 22px;
    margin-bottom: 18px;
    box-shadow: 0 4px 18px rgba(0,0,0,.08);
}

h1 {
    margin-top: 0;
}

label {
    display: block;
    font-weight: 600;
    margin-top: 15px;
    margin-bottom: 6px;
}

input {
    width: 100%;
    box-sizing: border-box;
    padding: 12px;
    border: 1px solid #ccd2d8;
    border-radius: 8px;
    font-size: 15px;
}

button {
    margin-top: 20px;
    width: 100%;
    padding: 14px;
    border: 0;
    border-radius: 9px;
    background: #111827;
    color: white;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
}

button:disabled {
    opacity: .5;
}

pre {
    background: #111827;
    color: #e5e7eb;
    padding: 15px;
    border-radius: 10px;
    overflow-x: auto;
    white-space: pre-wrap;
    word-break: break-word;
    min-height: 160px;
}

.status {
    font-weight: 700;
}

.download {
    display: none;
    text-align: center;
    padding: 15px;
    border-radius: 9px;
    background: #ecfdf5;
}

.download a {
    display: inline-block;
    padding: 12px 22px;
    border-radius: 8px;
    background: #111827;
    color: white;
    font-weight: 700;
    text-decoration: none;
    cursor: pointer;
}

.warning {
    background: #fff7ed;
    border-left: 4px solid #f97316;
    padding: 12px;
    margin-top: 15px;
}

.section-title {
    margin-top: 0;
    border-bottom: 1px solid #eee;
    padding-bottom: 8px;
}
</style>
</head>

<body>

<div class="container">

<div class="card">
<h1>RTK Telegram APK Builder</h1>

<p>
Build a configured signed release APK directly from this browser.
</p>

<div class="warning">
This builder listens only on localhost.
Bot credentials are written to local.properties on this machine.
</div>
</div>

<form id="buildForm"
      enctype="multipart/form-data">

<div class="card">

<h2 class="section-title">APK Settings</h2>

<label>APK File Name</label>
<input
    name="apk_name"
    value="RTKTelegramManager"
    maxlength="80">

<label>App Name</label>
<input
    name="app_name"
    placeholder="RTK Telegram Manager"
    required>

<label>Target Web URL</label>
<input
    name="web_url"
    type="url"
    placeholder="https://example.com"
    required>

<label>App Icon</label>
<input
    name="app_icon"
    type="file"
    accept="image/png,image/jpeg,image/webp">

</div>

<div class="card">

<h2 class="section-title">Developer</h2>

<label>Developer Key</label>
<input
    name="developer_key"
    type="password"
    required>

</div>

<div class="card">

<h2 class="section-title">Bot 1</h2>

<label>Bot Name</label>
<input name="bot1_name">

<label>Bot Token</label>
<input
    name="bot1_token"
    type="password"
    required>

<label>Bot Chat ID</label>
<input
    name="bot1_chat_id"
    required>

</div>

<div class="card">

<h2 class="section-title">Bot 2</h2>

<label>Bot Name</label>
<input name="bot2_name">

<label>Bot Token</label>
<input
    name="bot2_token"
    type="password"
    required>

<label>Bot Chat ID</label>
<input
    name="bot2_chat_id"
    required>

</div>

<div class="card">

<button id="buildButton" type="submit">
BUILD SIGNED APK
</button>

</div>

</form>

<div class="card">

<h2 class="section-title">Build Status</h2>

<div id="status" class="status">
Ready
</div>

<pre id="log"></pre>

<div id="download" class="download">
    <a id="downloadLink"
       href="/download"
       download>
        ⬇ Download APK
    </a>
</div>

</div>

</div>

<script>

const form = document.getElementById("buildForm");
const button = document.getElementById("buildButton");
const statusBox = document.getElementById("status");
const logBox = document.getElementById("log");
const downloadBox = document.getElementById("download");
const downloadLink = document.getElementById("downloadLink");

let polling = null;

function escapeText(value) {
    return value || "";
}

async function pollStatus() {

    try {

        const response = await fetch(
            "/status",
            {
                cache: "no-store"
            }
        );

        const data = await response.json();

        statusBox.textContent = data.status;
        logBox.textContent = data.log || "";

        logBox.scrollTop = logBox.scrollHeight;

        if (data.status === "success") {

            button.disabled = false;
            button.textContent = "BUILD SIGNED APK";

            // Make download button visible.
            downloadBox.style.display = "block";

            // Make sure the link points to the current APK.
            downloadLink.href = "/download";
            downloadLink.setAttribute(
                "download",
                ""
            );

            if (polling) {
                clearInterval(polling);
                polling = null;
            }

        } else if (data.status === "failed") {

            button.disabled = false;
            button.textContent = "BUILD SIGNED APK";

            downloadBox.style.display = "none";

            if (polling) {
                clearInterval(polling);
                polling = null;
            }
        }

    } catch (error) {

        console.error(error);
    }
}

form.addEventListener("submit", async function(event) {

    event.preventDefault();

    button.disabled = true;
    button.textContent = "BUILDING...";
    statusBox.textContent = "building";
    logBox.textContent = "";
    downloadBox.style.display = "none";

    const formData = new FormData(form);

    try {

        /*
         * Stream the build output directly from /build.
         * This makes Gradle output appear live in the browser.
         */
        const response = await fetch("/build", {
            method: "POST",
            body: formData
        });

        if (!response.ok) {
            throw new Error(
                "Build request failed: HTTP " +
                response.status
            );
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");

        while (true) {

            const { value, done } =
                await reader.read();

            if (done) {
                break;
            }

            const chunk =
                decoder.decode(
                    value,
                    { stream: true }
                );

            logBox.textContent += chunk;

            logBox.scrollTop =
                logBox.scrollHeight;
        }

        // Get final status after stream closes.
        await pollStatus();

    } catch (error) {

        statusBox.textContent = "failed";

        logBox.textContent +=
            "\n\n===== BROWSER ERROR =====\n" +
            error;

        button.disabled = false;
        button.textContent = "BUILD SIGNED APK";
    }
});

pollStatus();

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
