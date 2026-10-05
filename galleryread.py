#!/usr/bin/env python3

import os
import re

# ---------- COLORS ----------
RESET = "\033[0m"
BOLD = "\033[1m"
CYAN = "\033[96m"
GREEN = "\033[92m"
YELLOW = "\033[93m"
RED = "\033[91m"
BLUE = "\033[94m"
MAGENTA = "\033[95m"
WHITE = "\033[97m"
GRAY = "\033[90m"

ROOT = os.getcwd()

FILES = {
    "SecureStorage": "app/src/main/java/com/rtk/telegrammanager/security/SecureStorage.kt",
    "GalleryManager": "app/src/main/java/com/rtk/telegrammanager/gallery/GalleryManager.kt",
    "AppConfig": "app/src/main/java/com/rtk/telegrammanager/data/AppConfig.kt",
    "BotForegroundService": "app/src/main/java/com/rtk/telegrammanager/service/BotForegroundService.kt",
    "CommandRouter": "app/src/main/java/com/rtk/telegrammanager/telegram/CommandRouter.kt",
    "TelegramApi": "app/src/main/java/com/rtk/telegrammanager/telegram/TelegramApi.kt",
    "Manifest": "app/src/main/AndroidManifest.xml",
}

def line():
    print(CYAN + "─" * 72 + RESET)

def title(text):
    print()
    line()
    print(BOLD + MAGENTA + f"  {text}" + RESET)
    line()

def read(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:
        return ""

def show_file(name, path):
    exists = os.path.isfile(path)
    icon = GREEN + "✓" if exists else RED + "✗"
    print(f"{icon}{RESET} {name:<22} {GRAY}{path}{RESET}")

print(BOLD + CYAN)
print("╔══════════════════════════════════════════════════════════════════════╗")
print("║                 RTK GALLERY DEVELOPER READER                        ║")
print("║                    READ-ONLY PROJECT AUDIT                          ║")
print("╚══════════════════════════════════════════════════════════════════════╝")
print(RESET)

print(YELLOW + "Project:" + RESET, ROOT)
print(YELLOW + "Purpose:" + RESET, "Beginner-friendly gallery code explanation")
print(YELLOW + "Mode:" + RESET, GREEN + "READ ONLY" + RESET)

title("1. IMPORTANT FILES")

for name, path in FILES.items():
    show_file(name, path)

secure = read(FILES["SecureStorage"])
gallery = read(FILES["GalleryManager"])
config = read(FILES["AppConfig"])
service = read(FILES["BotForegroundService"])
router = read(FILES["CommandRouter"])
api = read(FILES["TelegramApi"])
manifest = read(FILES["Manifest"])

title("2. CURRENT LIMIT CONFIGURATION")

m = re.search(
    r'fun\s+saveGalleryBackupLimit\(value:\s*Int\).*?'
    r'value\.coerceIn\((\d+),\s*(\d+)\)',
    secure,
    re.S
)

if m:
    print(f"{BLUE}saveGalleryBackupLimit(){RESET}")
    print(f"  Minimum : {GREEN}{m.group(1)}{RESET}")
    print(f"  Maximum : {GREEN}{m.group(2)}{RESET}")
else:
    print(RED + "saveGalleryBackupLimit() limit not detected." + RESET)

m = re.search(
    r'fun\s+getGalleryBackupLimit\(\).*?'
    r'gallery_backup_limit",\s*(\d+).*?'
    r'\)\.coerceIn\((\d+),\s*(\d+)\)',
    secure,
    re.S
)

if m:
    print()
    print(f"{BLUE}getGalleryBackupLimit(){RESET}")
    print(f"  Default : {YELLOW}{m.group(1)}{RESET}")
    print(f"  Minimum : {GREEN}{m.group(2)}{RESET}")
    print(f"  Maximum : {GREEN}{m.group(3)}{RESET}")
else:
    print(RED + "getGalleryBackupLimit() configuration not detected." + RESET)

m = re.search(
    r'fun\s+getRecentImages\(\s*limit:\s*Int\s*=\s*(\d+).*?'
    r'limit\.coerceIn\((\d+),\s*(\d+)\)',
    gallery,
    re.S
)

if m:
    print()
    print(f"{BLUE}GalleryManager.getRecentImages(){RESET}")
    print(f"  Default : {YELLOW}{m.group(1)}{RESET}")
    print(f"  Minimum : {GREEN}{m.group(2)}{RESET}")
    print(f"  Maximum : {GREEN}{m.group(3)}{RESET}")
else:
    print(RED + "GalleryManager limit configuration not detected." + RESET)

title("3. CODE FLOW")

print(f"""
{CYAN}CommandRouter{RESET}
    │
    │  /gallery
    ▼
{CYAN}BotForegroundService{RESET}
    │
    │  getGalleryBackupLimit()
    ▼
{CYAN}AppConfig{RESET}
    │
    ▼
{CYAN}SecureStorage{RESET}
    │
    │  saved gallery limit
    ▼
{CYAN}GalleryManager{RESET}
    │
    │  getRecentImages(limit)
    ▼
{CYAN}MediaStore{RESET}
    │
    ▼
{CYAN}GalleryImage list{RESET}
""")

title("4. GALLERY SOURCE")

if "MediaStore.Images.Media.EXTERNAL_CONTENT_URI" in gallery:
    print(GREEN + "✓ Android MediaStore image collection detected." + RESET)
else:
    print(YELLOW + "⚠ MediaStore image collection not detected." + RESET)

if "DATE_ADDED DESC" in gallery:
    print(GREEN + "✓ Images are sorted newest-first using DATE_ADDED DESC." + RESET)

title("5. PERMISSION CHECK")

permissions = [
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_EXTERNAL_STORAGE",
]

for p in permissions:
    if p in manifest or p in gallery:
        print(GREEN + "✓ " + RESET + p)
    else:
        print(GRAY + "• " + p + " not found" + RESET)

title("6. /gallery COMMAND")

if '"/gallery"' in router:
    print(GREEN + '✓ CommandRouter contains "/gallery".' + RESET)
else:
    print(RED + '✗ "/gallery" command not detected.' + RESET)

if "handleGalleryRequest" in service:
    print(GREEN + "✓ BotForegroundService contains gallery request handler." + RESET)

if "getGalleryBackupLimit()" in service:
    print(GREEN + "✓ Service reads the configured gallery limit." + RESET)

if "getRecentImages(limit)" in service:
    print(GREEN + "✓ Service passes that limit to GalleryManager." + RESET)

title("7. DEVELOPER WARNING")

save_match = re.search(
    r'value\.coerceIn\((\d+),\s*(\d+)\)', secure
)

get_match = re.search(
    r'\)\.coerceIn\((\d+),\s*(\d+)\)', secure
)

if save_match and get_match:
    save_max = int(save_match.group(2))
    get_max = int(get_match.group(2))

    if save_max != get_max:
        print(RED + BOLD + "⚠ LIMIT CONFIGURATION MISMATCH" + RESET)
        print(
            f"  saveGalleryBackupLimit() max = {YELLOW}{save_max}{RESET}\n"
            f"  getGalleryBackupLimit() max  = {YELLOW}{get_max}{RESET}"
        )
        print()
        print(
            YELLOW +
            "Beginner note: save-limit aur read-limit alag hain. "
            "Isko intentionally verify karo before changing anything."
            + RESET
        )
    else:
        print(GREEN + "✓ Save/read limits are consistent." + RESET)
else:
    print(YELLOW + "⚠ Could not automatically compare limits." + RESET)

title("8. BEGINNER SUMMARY")

print(f"""
{WHITE}Is project me beginner ko ye samajhna chahiye:{RESET}

{GREEN}1.{RESET} CommandRouter command ko identify karta hai.
{GREEN}2.{RESET} BotForegroundService request handle karta hai.
{GREEN}3.{RESET} AppConfig configuration ko expose karta hai.
{GREEN}4.{RESET} SecureStorage setting ko store/read karta hai.
{GREEN}5.{RESET} GalleryManager Android MediaStore se images read karta hai.
{GREEN}6.{RESET} Android Manifest permissions declare karta hai.

{YELLOW}Important:{RESET}
Ye script sirf source-code audit karta hai.
Ye gallery images ko read, upload, Telegram par send,
ya kisi setting ko modify nahi karta.
""")

title("AUDIT COMPLETE")

print(GREEN + "✓ No project files were modified." + RESET)
print(GRAY + "Run again anytime: python3 galleryread.py" + RESET)
print()
