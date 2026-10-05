#!/usr/bin/env python3

import os
import re
import sys
import time
from pathlib import Path

# ============================================================
# TYAGIRTK CALL SYSTEM READER
# Developer-friendly animated terminal analyzer
# ============================================================

RESET  = "\033[0m"
BOLD   = "\033[1m"
DIM    = "\033[2m"

RED    = "\033[91m"
GREEN  = "\033[92m"
YELLOW = "\033[93m"
BLUE   = "\033[94m"
MAGENTA= "\033[95m"
CYAN   = "\033[96m"
WHITE  = "\033[97m"

ROOT = Path(".").resolve()

# Backup folders are intentionally separated from ACTIVE source.
BACKUP_WORDS = (
    "backup",
    "backup-",
    "backup_before",
)

EXTENSIONS = {
    ".kt", ".java", ".xml", ".gradle", ".kts",
    ".py", ".json", ".html", ".js"
}

PATTERNS = [
    "CallLog",
    "READ_CALL_LOG",
    "WRITE_CALL_LOG",
    "CallLogManager",
    "callLog",
    "calllog",
    "/calls",
    "getRecentCalls",
    "getCallById",
    "getLatestCallId",
    "formattedText",
    "ContentObserver",
    "INCOMING_TYPE",
    "OUTGOING_TYPE",
    "MISSED_TYPE",
    "REJECTED_TYPE",
    "BLOCKED_TYPE",
]

SKIP_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
    "node_modules",
}

def color(c, text):
    return f"{c}{text}{RESET}"

def slow(text="", delay=0.012):
    for ch in text:
        print(ch, end="", flush=True)
        time.sleep(delay)
    print()

def line(char="═", n=70):
    print(CYAN + char * n + RESET)

def title(text):
    print()
    line()
    print(BOLD + MAGENTA + "        " + text + RESET)
    line()

def section(icon, name, c=CYAN):
    print()
    print(BOLD + c + f"{icon}  {name}" + RESET)
    print(c + "─" * 68 + RESET)

def path_type(path):
    s = str(path).lower()
    if any(x in s for x in BACKUP_WORDS):
        return "BACKUP"
    return "ACTIVE"

def rel(path):
    try:
        return str(path.relative_to(ROOT))
    except:
        return str(path)

def scan_file(path):
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return []

    matches = []

    for no, raw in enumerate(text.splitlines(), 1):
        for pattern in PATTERNS:
            if re.search(re.escape(pattern), raw, re.I):
                matches.append((no, raw.strip()))
                break

    return matches

def active_files():
    results = []

    for root, dirs, files in os.walk(ROOT):
        dirs[:] = [
            d for d in dirs
            if d not in SKIP_DIRS
        ]

        for filename in files:
            path = Path(root) / filename

            if path.suffix.lower() not in EXTENSIONS:
                continue

            if path_type(path) != "ACTIVE":
                continue

            matches = scan_file(path)

            if matches:
                results.append((path, matches))

    return sorted(results, key=lambda x: str(x[0]))

def find_line(path, regex):
    try:
        lines = path.read_text(
            encoding="utf-8",
            errors="ignore"
        ).splitlines()
    except:
        return []

    out = []

    for no, text in enumerate(lines, 1):
        if re.search(regex, text, re.I):
            out.append((no, text.strip()))

    return out

def detect_limits():
    router = ROOT / "app/src/main/java/com/rtk/telegrammanager/telegram/CommandRouter.kt"
    manager = ROOT / "app/src/main/java/com/rtk/telegrammanager/calls/CallLogManager.kt"

    result = []

    if router.exists():
        for no, text in find_line(router, r'"/calls"'):
            m = re.search(r'formattedText\((\d+)\)', text)
            if m:
                result.append(
                    ("Telegram /calls", m.group(1), rel(router), no)
                )

    if manager.exists():
        for no, text in find_line(manager, r'limit\s*:\s*Int\s*=\s*\d+'):
            m = re.search(r'limit\s*:\s*Int\s*=\s*(\d+)', text)
            if m:
                result.append(
                    ("getRecentCalls default", m.group(1), rel(manager), no)
                )

        for no, text in find_line(manager, r'formattedText\('):
            pass

    return result

def developer_map():
    section("🗺️", "CALL SYSTEM ARCHITECTURE", MAGENTA)

    items = [
        (
            "🔴 CORE",
            "app/src/main/java/com/rtk/telegrammanager/calls/CallLogManager.kt",
            "Main call-log engine"
        ),
        (
            "🟣 TELEGRAM",
            "app/src/main/java/com/rtk/telegrammanager/telegram/CommandRouter.kt",
            "/calls command"
        ),
        (
            "🟠 AUTO ALERT",
            "app/src/main/java/com/rtk/telegrammanager/service/BotForegroundService.kt",
            "Detects new calls"
        ),
        (
            "🟡 PERMISSION",
            "app/src/main/AndroidManifest.xml",
            "READ_CALL_LOG permission"
        ),
        (
            "🟢 RUNTIME",
            "app/src/main/java/com/rtk/telegrammanager/MainActivity.kt",
            "Requests permission"
        ),
        (
            "🔵 MODULE",
            "app/src/main/java/com/rtk/telegrammanager/modulemanager/ModuleManager.kt",
            "Registers calllog module"
        ),
    ]

    for icon, path, desc in items:
        print(
            f"{icon} {YELLOW}{path}{RESET}"
            f"\n   {DIM}└── {desc}{RESET}"
        )

def function_map():
    manager = ROOT / "app/src/main/java/com/rtk/telegrammanager/calls/CallLogManager.kt"

    section("⚙️", "CORE FUNCTIONS", BLUE)

    if not manager.exists():
        print(color(RED, "❌ CallLogManager.kt not found"))
        return

    funcs = [
        ("getRecentCalls", "Reads recent call history"),
        ("getLatestCallId", "Gets newest call record ID"),
        ("getCallById", "Reads exactly one call"),
        ("formattedText", "Formats calls for Telegram"),
        ("lookupContactName", "Finds contact name"),
        ("formatDuration", "Formats call duration"),
    ]

    for fn, desc in funcs:
        hits = find_line(
            manager,
            rf"\bfun\s+{re.escape(fn)}\b"
        )

        if hits:
            no = hits[0][0]
            print(
                f"{GREEN}✓{RESET} "
                f"{BOLD}{fn}(){RESET} "
                f"{DIM}line {no}{RESET}"
            )
            print(f"   {CYAN}{desc}{RESET}")

def limits_report():
    section("🎯", "CURRENT CALL LIMITS", YELLOW)

    limits = detect_limits()

    if not limits:
        print(color(RED, "Could not detect limits"))
        return

    for name, value, path, no in limits:
        print(
            f"{GREEN}●{RESET} "
            f"{BOLD}{name}{RESET}: "
            f"{YELLOW}{value}{RESET}"
        )
        print(
            f"   📍 {CYAN}{path}:{no}{RESET}"
        )

    print()
    print(
        f"{MAGENTA}ℹ Automatic NEW CALL detection:{RESET} "
        f"{GREEN}No 20/500 history limit detected{RESET}"
    )
    print(
        f"   It uses latest CallLog ID + ContentObserver."
    )

def permissions_report():
    section("🔐", "CALL PERMISSIONS", RED)

    manifest = ROOT / "app/src/main/AndroidManifest.xml"
    activity = ROOT / "app/src/main/java/com/rtk/telegrammanager/MainActivity.kt"

    for path, regex, desc in [
        (manifest, r"READ_CALL_LOG", "Manifest permission"),
        (activity, r"READ_CALL_LOG", "Runtime permission request"),
    ]:
        if path.exists():
            hits = find_line(path, regex)

            for no, text in hits:
                print(
                    f"{GREEN}✓{RESET} "
                    f"{BOLD}{desc}{RESET}"
                )
                print(
                    f"   📍 {CYAN}{rel(path)}:{no}{RESET}"
                )
                print(f"   {DIM}{text}{RESET}")

def auto_call_flow():
    section("⚡", "AUTOMATIC NEW-CALL FLOW", GREEN)

    flow = [
        ("1", "CallLog.Calls.CONTENT_URI", "Android call database changes"),
        ("2", "ContentObserver", "Detects call-log change"),
        ("3", "getLatestCallId()", "Gets newest record ID"),
        ("4", "lastProcessedCallId", "Compares previous ID"),
        ("5", "getCallById(id)", "Reads only new call"),
        ("6", "Telegram message", "Sends NEW CALL alert"),
    ]

    for num, name, desc in flow:
        print(
            f"{GREEN}{num}{RESET} "
            f"{BOLD}{name}{RESET}"
            f"  {DIM}→ {desc}{RESET}"
        )

def active_scan(files):
    section("🔎", "ACTIVE SOURCE FILES", CYAN)

    for path, matches in files:
        print()
        print(
            f"{GREEN}📁 {rel(path)}{RESET}"
            f"  {DIM}[ACTIVE]{RESET}"
        )

        shown = set()

        for no, text in matches:
            key = (no, text)

            if key in shown:
                continue

            shown.add(key)

            # Highlight important lines
            if "READ_CALL_LOG" in text:
                c = RED
            elif "CallLogManager" in text:
                c = MAGENTA
            elif "/calls" in text:
                c = YELLOW
            elif "ContentObserver" in text:
                c = GREEN
            else:
                c = WHITE

            print(
                f"   {DIM}{no:4}{RESET} "
                f"{c}{text}{RESET}"
            )

def backup_report():
    section("🗄️", "BACKUP FILES DETECTED", DIM)

    count = 0

    for root, dirs, files in os.walk(ROOT):
        dirs[:] = [
            d for d in dirs
            if d not in SKIP_DIRS
        ]

        for filename in files:
            path = Path(root) / filename

            if path.suffix.lower() not in EXTENSIONS:
                continue

            if path_type(path) == "BACKUP":
                matches = scan_file(path)

                if matches:
                    count += 1
                    print(
                        f"{DIM}📦 {rel(path)}{RESET}"
                    )

    print()
    print(
        f"{YELLOW}⚠ {count} backup files contain call references.{RESET}"
    )
    print(
        f"{DIM}These are historical copies, not the active source.{RESET}"
    )

def modification_guide():
    section("🛠️", "BEGINNER DEVELOPER MODIFICATION GUIDE", MAGENTA)

    guide = [
        (
            "Change /calls history limit",
            "telegram/CommandRouter.kt",
            '"/calls" -> calls.formattedText(N)'
        ),
        (
            "Change default history limit",
            "calls/CallLogManager.kt",
            "getRecentCalls(limit: Int = N)"
        ),
        (
            "Change call output format",
            "calls/CallLogManager.kt",
            "formattedText()"
        ),
        (
            "Change automatic new-call detection",
            "service/BotForegroundService.kt",
            "registerCallLogObserver() / handleCallLogChanged()"
        ),
        (
            "Change permission",
            "AndroidManifest.xml + MainActivity.kt",
            "READ_CALL_LOG"
        ),
        (
            "Change call type handling",
            "calls/CallLogManager.kt",
            "INCOMING / OUTGOING / MISSED / REJECTED / BLOCKED"
        ),
    ]

    for i, (what, path, target) in enumerate(guide, 1):
        print(
            f"{YELLOW}{i}.{RESET} "
            f"{BOLD}{what}{RESET}"
        )
        print(
            f"   📍 {CYAN}{path}{RESET}"
        )
        print(
            f"   🔧 {GREEN}{target}{RESET}"
        )
        print()

def main():
    os.system("clear")

    print()
    print(BOLD + MAGENTA)
    print("╔══════════════════════════════════════════════════════════════╗")
    print("║                                                            ║")
    print("║        ⚡ TYAGIRTK CALL SYSTEM READER ⚡                  ║")
    print("║                                                            ║")
    print("║       Developer • Beginner • Module Analyzer              ║")
    print("║                                                            ║")
    print("╚══════════════════════════════════════════════════════════════╝")
    print(RESET)

    slow(
        color(CYAN, "🔍 Scanning RTK Telegram Manager project..."),
        0.018
    )

    for i in range(3):
        print(
            color(
                MAGENTA,
                f"\r   Loading {'.' * (i + 1)}   "
            ),
            end="",
            flush=True
        )
        time.sleep(0.25)

    print("\n")

    files = active_files()

    developer_map()
    function_map()
    limits_report()
    permissions_report()
    auto_call_flow()
    active_scan(files)
    backup_report()
    modification_guide()

    section("🏁", "SCAN COMPLETE", GREEN)

    print(
        f"{GREEN}✓ Active call-related files: "
        f"{len(files)}{RESET}"
    )

    print()
    print(
        BOLD + CYAN +
        "TYAGIRTK • Call System Reader" +
        RESET
    )
    print(
        DIM +
        "Read-only analyzer • No project files modified" +
        RESET
    )
    print()

if __name__ == "__main__":
    main()
