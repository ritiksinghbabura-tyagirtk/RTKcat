#!/usr/bin/env bash
set -u

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLCHAIN_HOME="${RTK_TOOLCHAIN_HOME:-$HOME/RTK_ANDROID_TOOLCHAIN}"

PASS=0
FAIL=0
WARN=0

ok() {
    echo "[OK]   $1"
    PASS=$((PASS + 1))
}

fail() {
    echo "[FAIL] $1"
    FAIL=$((FAIL + 1))
}

warn() {
    echo "[WARN] $1"
    WARN=$((WARN + 1))
}

section() {
    echo
    echo "------------------------------------------------------------"
    echo " $1"
    echo "------------------------------------------------------------"
}

echo
echo "============================================================"
echo " RTKcat Doctor"
echo "============================================================"
echo

# ------------------------------------------------------------
# Load RTK environment if available
# ------------------------------------------------------------

ENV_FILE="$TOOLCHAIN_HOME/env.sh"

if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    ok "RTK toolchain environment loaded"
else
    fail "RTK environment not found: $ENV_FILE"
    echo
    echo "Run first:"
    echo
    echo "    ./setup-rtk.sh"
    echo
fi

# ------------------------------------------------------------
# System
# ------------------------------------------------------------

section "System"

ARCH="$(uname -m 2>/dev/null || true)"

if [[ "$ARCH" == "aarch64" ]]; then
    ok "Architecture: aarch64"
else
    fail "Architecture is '$ARCH' - expected aarch64"
fi

if [[ -f /etc/os-release ]]; then
    . /etc/os-release
    echo "[INFO] OS       : ${PRETTY_NAME:-unknown}"
fi

echo "[INFO] Kernel   : $(uname -r 2>/dev/null || echo unknown)"

# ------------------------------------------------------------
# Toolchain directory
# ------------------------------------------------------------

section "RTK Toolchain"

if [[ -d "$TOOLCHAIN_HOME" ]]; then
    ok "Toolchain directory exists: $TOOLCHAIN_HOME"
else
    fail "Toolchain directory missing: $TOOLCHAIN_HOME"
fi

if [[ -f "$TOOLCHAIN_HOME/TOOLCHAIN_MANIFEST.txt" ]]; then
    ok "TOOLCHAIN_MANIFEST.txt found"
else
    fail "TOOLCHAIN_MANIFEST.txt missing"
fi

# ------------------------------------------------------------
# Java
# ------------------------------------------------------------

section "Java"

if command -v java >/dev/null 2>&1; then

    JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
    echo "[INFO] $JAVA_VERSION"

    if java -version 2>&1 | grep -qE '"21([."]|$)'; then
        ok "Java 21 detected"
    else
        fail "Java 21 required"
    fi
else
    fail "java command not found"
fi

if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME" ]]; then
    ok "JAVA_HOME: $JAVA_HOME"
else
    fail "JAVA_HOME is not configured correctly"
fi

# ------------------------------------------------------------
# Android SDK
# ------------------------------------------------------------

section "Android SDK"

if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
    ok "ANDROID_HOME: $ANDROID_HOME"
else
    fail "ANDROID_HOME is not configured"
fi

if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT" ]]; then
    ok "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
else
    warn "ANDROID_SDK_ROOT is not configured"
fi

SDK="${ANDROID_HOME:-$TOOLCHAIN_HOME/android-sdk}"

if [[ -d "$SDK/platforms/android-35" ]]; then
    ok "Android SDK Platform 35"
else
    fail "Android SDK Platform 35 missing"
fi

if [[ -d "$SDK/build-tools/35.0.0" ]]; then
    ok "Android Build Tools 35.0.0"
else
    fail "Android Build Tools 35.0.0 missing"
fi

if [[ -d "$SDK/platform-tools" ]]; then
    ok "Android Platform Tools"
else
    fail "Android Platform Tools missing"
fi

if [[ -d "$SDK/cmdline-tools" ]]; then
    ok "Android Command Line Tools"
else
    fail "Android Command Line Tools missing"
fi

# ------------------------------------------------------------
# NDK
# ------------------------------------------------------------

section "NDK / CMake"

if [[ -d "$SDK/ndk/26.3.11579264" ]]; then
    ok "NDK 26.3.11579264"
else
    fail "NDK 26.3.11579264 missing"
fi

if [[ -d "$SDK/cmake/3.30.3" ]]; then
    ok "CMake 3.30.3"
else
    fail "CMake 3.30.3 missing"
fi

# ------------------------------------------------------------
# AAPT2
# ------------------------------------------------------------

section "ARM64 AAPT2"

AAPT2="${RTK_AAPT2:-$SDK/build-tools/native/aapt2}"

if [[ -x "$AAPT2" ]]; then
    ok "ARM64 AAPT2 found: $AAPT2"

    if "$AAPT2" version >/dev/null 2>&1; then
        ok "AAPT2 executable test passed"
    else
        fail "AAPT2 exists but could not execute"
    fi
else
    fail "ARM64 AAPT2 missing or not executable: $AAPT2"
fi

# ------------------------------------------------------------
# Gradle
# ------------------------------------------------------------

section "Gradle"

if [[ -x "$PROJECT_ROOT/gradlew" ]]; then
    ok "Gradle wrapper exists and is executable"
else
    fail "gradlew missing or not executable"
fi

WRAPPER_PROPERTIES="$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties"

if [[ -f "$WRAPPER_PROPERTIES" ]]; then

    if grep -q "gradle-8.10.2-" "$WRAPPER_PROPERTIES"; then
        ok "Gradle Wrapper configured for 8.10.2"
    else
        warn "Gradle wrapper version is not 8.10.2"
    fi

else
    fail "gradle-wrapper.properties missing"
fi

if command -v gradle >/dev/null 2>&1; then
    GRADLE_VERSION="$(gradle --version 2>/dev/null | grep '^Gradle ' | head -n 1 || true)"

    if [[ -n "$GRADLE_VERSION" ]]; then
        echo "[INFO] $GRADLE_VERSION"
    fi
fi

# ------------------------------------------------------------
# Project
# ------------------------------------------------------------

section "RTKcat Project"

for file in \
    "settings.gradle.kts" \
    "build.gradle.kts" \
    "gradle.properties" \
    "gradlew" \
    "app/build.gradle.kts"
do
    if [[ -f "$PROJECT_ROOT/$file" ]]; then
        ok "$file"
    else
        fail "$file missing"
    fi
done

if [[ -f "$PROJECT_ROOT/.gitignore" ]]; then
    ok ".gitignore exists"
else
    warn ".gitignore missing"
fi

if [[ -f "$PROJECT_ROOT/local.properties" ]]; then
    ok "local.properties exists"
else
    warn "local.properties missing"
fi

# ------------------------------------------------------------
# Git ignore check
# ------------------------------------------------------------

section "Git Safety"

if command -v git >/dev/null 2>&1 && [[ -d "$PROJECT_ROOT/.git" ]]; then

    if git -C "$PROJECT_ROOT" check-ignore -q local.properties 2>/dev/null; then
        ok "local.properties is ignored by Git"
    else
        warn "local.properties is NOT ignored by Git"
    fi

    if git -C "$PROJECT_ROOT" check-ignore -q app/build 2>/dev/null; then
        ok "app/build is ignored by Git"
    else
        warn "app/build is NOT ignored by Git"
    fi
else
    warn "Git repository not detected"
fi

# ------------------------------------------------------------
# Final
# ------------------------------------------------------------

echo
echo "============================================================"
echo " Doctor Result"
echo "============================================================"
echo

echo "Passed : $PASS"
echo "Failed : $FAIL"
echo "Warned : $WARN"

echo

if [[ "$FAIL" -eq 0 ]]; then
    echo "[SUCCESS] RTKcat build environment looks healthy."
    echo
    echo "Next:"
    echo
    echo "    ./build.sh"
    echo
    exit 0
else
    echo "[ERROR] Fix the failed checks before building."
    echo
    echo "If this is a fresh environment, run:"
    echo
    echo "    ./setup-rtk.sh"
    echo
    exit 1
fi
