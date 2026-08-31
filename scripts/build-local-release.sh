#!/usr/bin/env bash
#
# Build Kreate's GitHub/universal release APK and sign it with a LOCAL keystore.
#
# Official Kreate APKs are signed by the maintainer's key, which we don't have. A self-built
# APK therefore needs its own key; Android refuses to update an app with a different signature,
# so keep the keystore (and its password file) safe - losing it means uninstalling the app
# (and its local data) to install a newer self-built version.
#
# Expected files (gitignored, see .gitignore -> .ignore.d/):
#   .ignore.d/keystores/kreate-local.jks
#   .ignore.d/keystores/kreate-local.properties   (storeFile, storePassword, keyAlias, keyPassword)
#
# Usage:
#   scripts/build-local-release.sh [FLAVOR] [--skip-build]
#     FLAVOR       kruxx (default: own app id de.kruxx.music, name "KruXx") or github (stock Kreate)
#     --skip-build only sign the existing unsigned APK
#
set -euo pipefail

FLAVOR="kruxx"
SKIP_BUILD=0
for arg in "$@"; do
    case "$arg" in
        --skip-build) SKIP_BUILD=1 ;;
        kruxx|github|fdroid|izzy) FLAVOR="$arg" ;;
        *) echo "usage: $0 [kruxx|github|fdroid|izzy] [--skip-build]" >&2; exit 2 ;;
    esac
done
case "$FLAVOR" in
    kruxx) APP_NAME="KruXx" ;;
    *)     APP_NAME="Kreate" ;;
esac
FLAVOR_CAP="$(tr '[:lower:]' '[:upper:]' <<< "${FLAVOR:0:1}")${FLAVOR:1}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT/.ignore.d/keystores/kreate-local.properties"
OUT_DIR="$ROOT/composeApp/build/outputs/apk/${FLAVOR}UniversalProd/release"
UNSIGNED="$OUT_DIR/$APP_NAME-release.apk"
SIGNED="$OUT_DIR/$APP_NAME-release-signed.apk"

[[ -f "$PROPS" ]] || { echo "error: $PROPS not found (create keystore + properties first)" >&2; exit 1; }

prop() { grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2-; }
STORE_FILE="$ROOT/$(prop storeFile)"
STORE_PASSWORD="$(prop storePassword)"
KEY_ALIAS="$(prop keyAlias)"
KEY_PASSWORD="$(prop keyPassword)"
[[ -f "$STORE_FILE" ]] || { echo "error: keystore $STORE_FILE not found" >&2; exit 1; }

# Android SDK: local.properties (sdk.dir) or ANDROID_HOME
SDK_DIR="$(grep -E '^sdk.dir=' "$ROOT/local.properties" 2>/dev/null | cut -d= -f2- || true)"
SDK_DIR="${SDK_DIR:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
[[ -d "$SDK_DIR" ]] || { echo "error: Android SDK not found (set sdk.dir in local.properties or ANDROID_HOME)" >&2; exit 1; }
BUILD_TOOLS="$(ls -d "$SDK_DIR"/build-tools/*/ | sort -V | tail -1)"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
    echo ">> building $FLAVOR release APK (this takes a few minutes) ..."
    "$ROOT/gradlew" -p "$ROOT" ":composeApp:assemble${FLAVOR_CAP}UniversalProdRelease" --console=plain -q
fi
[[ -f "$UNSIGNED" ]] || { echo "error: $UNSIGNED not found - did the build succeed?" >&2; exit 1; }

echo ">> aligning ..."
ALIGNED="$(mktemp --suffix=.apk)"
trap 'rm -f "$ALIGNED"' EXIT
# -P 16: 16 KB page alignment for native libs (Android 15+), -p: page-align stored .so files
if ! "$ZIPALIGN" -f -P 16 -p 4 "$UNSIGNED" "$ALIGNED" 2>/dev/null; then
    "$ZIPALIGN" -f -p 4 "$UNSIGNED" "$ALIGNED"
fi

echo ">> signing ..."
"$APKSIGNER" sign \
    --ks "$STORE_FILE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$STORE_PASSWORD" --key-pass "pass:$KEY_PASSWORD" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$SIGNED" "$ALIGNED"

"$APKSIGNER" verify --print-certs "$SIGNED" | grep -E 'Signer #1 certificate (DN|SHA-256)' || true
echo ">> done: $SIGNED ($(du -h "$SIGNED" | cut -f1))"
