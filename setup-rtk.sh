#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TOOLCHAIN_REPO="${RTK_TOOLCHAIN_REPO:-https://github.com/tyagirtk-dev/ubuntu_rtk_apk_maker.git}"
TOOLCHAIN_SOURCE="${RTK_TOOLCHAIN_SOURCE:-$HOME/ubuntu_rtk_apk_maker_git}"
TOOLCHAIN_HOME="${RTK_TOOLCHAIN_HOME:-$HOME/RTK_ANDROID_TOOLCHAIN}"

echo
echo "============================================================"
echo " RTKcat - RTK Android Toolchain Setup"
echo "============================================================"
echo

fail() {
    echo
    echo "[FAIL] $1"
    exit 1
}

ok() {
    echo "[OK]   $1"
}

info() {
    echo "[INFO] $1"
}

# ------------------------------------------------------------
# 1. Architecture
# ------------------------------------------------------------

ARCH="$(uname -m)"

if [[ "$ARCH" != "aarch64" ]]; then
    fail "Unsupported architecture: $ARCH. RTK ARM64 toolchain requires aarch64."
fi

ok "Architecture: $ARCH"

# ------------------------------------------------------------
# 2. Basic commands
# ------------------------------------------------------------

for cmd in git bash curl; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        fail "Required command not found: $cmd"
    fi
done

ok "Required bootstrap commands available"

# ------------------------------------------------------------
# 3. Create RTK workspace
# ------------------------------------------------------------

mkdir -p "$(dirname "$TOOLCHAIN_SOURCE")"

# ------------------------------------------------------------
# 4. Get official toolchain repository
# ------------------------------------------------------------

if [[ -d "$TOOLCHAIN_SOURCE/.git" ]]; then
    info "Official toolchain repository already exists."
    info "Updating repository..."

    git -C "$TOOLCHAIN_SOURCE" fetch --all --prune

    CURRENT_BRANCH="$(git -C "$TOOLCHAIN_SOURCE" branch --show-current)"

    if [[ -n "$CURRENT_BRANCH" ]]; then
        git -C "$TOOLCHAIN_SOURCE" pull --ff-only || {
            info "Repository could not be fast-forwarded."
            info "Continuing with the existing checked-out version."
        }
    fi
else
    if [[ -e "$TOOLCHAIN_SOURCE" ]]; then
        fail "Toolchain source path exists but is not a Git repository: $TOOLCHAIN_SOURCE"
    fi

    info "Cloning official RTK Android toolchain repository..."
    git clone "$TOOLCHAIN_REPO" "$TOOLCHAIN_SOURCE"
fi

ok "Official RTK toolchain repository ready"

# ------------------------------------------------------------
# 5. Verify official installer
# ------------------------------------------------------------

INSTALLER="$TOOLCHAIN_SOURCE/scripts/install-toolchain.sh"

if [[ ! -f "$INSTALLER" ]]; then
    fail "Official installer not found: $INSTALLER"
fi

chmod +x "$INSTALLER"

ok "Official toolchain installer found"

# ------------------------------------------------------------
# 6. Install official RTK toolchain
# ------------------------------------------------------------

info "Installing/verifying official RTK Android toolchain..."
echo

RTK_TOOLCHAIN_DIR="$TOOLCHAIN_HOME" \
    bash "$INSTALLER"

echo

ok "RTK Android toolchain installation completed"

# ------------------------------------------------------------
# 7. Load generated environment
# ------------------------------------------------------------

ENV_FILE="$TOOLCHAIN_HOME/env.sh"

if [[ ! -f "$ENV_FILE" ]]; then
    fail "Toolchain environment file not found: $ENV_FILE"
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

ok "RTK toolchain environment loaded"

# ------------------------------------------------------------
# 8. Generate local.properties only when missing
# ------------------------------------------------------------

if [[ -n "${ANDROID_HOME:-}" ]]; then

    if [[ ! -f "$PROJECT_ROOT/local.properties" ]]; then
        printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$PROJECT_ROOT/local.properties"
        ok "Created local.properties"
    else
        info "local.properties already exists - preserving it"
    fi

else
    info "ANDROID_HOME is not available after toolchain setup"
fi

# ------------------------------------------------------------
# 9. Gradle wrapper permission
# ------------------------------------------------------------

if [[ -f "$PROJECT_ROOT/gradlew" ]]; then
    chmod +x "$PROJECT_ROOT/gradlew"
    ok "Gradle wrapper is executable"
else
    info "gradlew not found - skipping wrapper permission step"
fi

# ------------------------------------------------------------
# 10. Run official toolchain verification
# ------------------------------------------------------------

VERIFY="$TOOLCHAIN_SOURCE/scripts/verify-toolchain.sh"

if [[ -f "$VERIFY" ]]; then
    chmod +x "$VERIFY"

    echo
    echo "============================================================"
    echo " Official RTK Toolchain Verification"
    echo "============================================================"
    echo

    bash "$VERIFY"
else
    info "Official verification script not found; skipping."
fi

# ------------------------------------------------------------
# 11. Final environment summary
# ------------------------------------------------------------

echo
echo "============================================================"
echo " RTKcat Environment Ready"
echo "============================================================"
echo

echo "RTK_TOOLCHAIN_HOME : ${TOOLCHAIN_HOME}"
echo "RTK_TOOLCHAIN_SRC  : ${TOOLCHAIN_SOURCE}"
echo "JAVA_HOME          : ${JAVA_HOME:-not-set}"
echo "ANDROID_HOME       : ${ANDROID_HOME:-not-set}"
echo "ANDROID_SDK_ROOT   : ${ANDROID_SDK_ROOT:-not-set}"
echo "GRADLE_HOME        : ${GRADLE_HOME:-not-set}"
echo "RTK_AAPT2          : ${RTK_AAPT2:-not-set}"

echo
echo "Next step:"
echo
echo "    ./doctor.sh"
echo
echo "Then:"
echo
echo "    ./build.sh"
echo
echo "============================================================"
