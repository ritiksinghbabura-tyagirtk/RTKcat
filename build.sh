#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLCHAIN_HOME="${RTK_TOOLCHAIN_HOME:-$HOME/RTK_ANDROID_TOOLCHAIN}"

TASK="${1:-assembleDebug}"

echo
echo "============================================================"
echo " RTKcat Build"
echo "============================================================"
echo
echo "[INFO] Project : $PROJECT_ROOT"
echo "[INFO] Task    : $TASK"

# ------------------------------------------------------------
# Load RTK environment
# ------------------------------------------------------------

ENV_FILE="$TOOLCHAIN_HOME/env.sh"

if [[ ! -f "$ENV_FILE" ]]; then
    echo
    echo "[ERROR] RTK toolchain is not configured."
    echo
    echo "Run:"
    echo
    echo "    ./setup-rtk.sh"
    echo
    exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

# ------------------------------------------------------------
# Gradle wrapper
# ------------------------------------------------------------

chmod +x "$PROJECT_ROOT/gradlew"

# ------------------------------------------------------------
# Environment doctor
# ------------------------------------------------------------

echo
echo "[INFO] Running environment check..."
echo

"$PROJECT_ROOT/doctor.sh"

# ------------------------------------------------------------
# AAPT2 override
# ------------------------------------------------------------

if [[ -n "${RTK_AAPT2:-}" && -x "$RTK_AAPT2" ]]; then
    AAPT2_PROPERTY=(
        "-Pandroid.aapt2FromMavenOverride=$RTK_AAPT2"
    )
else
    AAPT2_PROPERTY=()
fi

# ------------------------------------------------------------
# Build
# ------------------------------------------------------------

echo
echo "============================================================"
echo " Gradle Build"
echo "============================================================"
echo

cd "$PROJECT_ROOT"

./gradlew \
    "${AAPT2_PROPERTY[@]}" \
    "$TASK"

# ------------------------------------------------------------
# Locate APK
# ------------------------------------------------------------

echo
echo "============================================================"
echo " Build Output"
echo "============================================================"
echo

mapfile -t APKS < <(
    find "$PROJECT_ROOT/app/build/outputs" \
        -type f \
        -name "*.apk" \
        2>/dev/null | sort
)

if [[ "${#APKS[@]}" -eq 0 ]]; then
    echo "[ERROR] Build completed but no APK was found."
    exit 1
fi

for APK in "${APKS[@]}"; do
    echo "[APK] $APK"
    ls -lh "$APK"
done

# ------------------------------------------------------------
# Optional Android Download copy
# ------------------------------------------------------------

if [[ "$TASK" == "assembleDebug" && -d "/sdcard/Download" ]]; then

    APK="${APKS[-1]}"
    DEST="/sdcard/Download/RTKcat-debug.apk"

    cp -f "$APK" "$DEST"

    echo
    echo "[OK] APK copied to:"
    echo
    echo "     $DEST"
fi

echo
echo "============================================================"
echo " BUILD SUCCESS"
echo "============================================================"
echo
