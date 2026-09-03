#!/usr/bin/env bash
#
# Build the KruXx universal release APK, sign it with the local KruXx release key
# and archive finished APKs in the sibling Kreate-APKs directory.
#
# Android refuses to update an app with a different signature. Keep the KruXx keystore
# (and its password file) safe: losing it means uninstalling the app (and its local data)
# before a differently signed build can be installed.
#
# Expected files (gitignored, see .gitignore -> .ignore.d/):
#   .ignore.d/keystores/kreate-local.jks
#   .ignore.d/keystores/kreate-local.properties   (storeFile, storePassword, keyAlias, keyPassword)
#
# Usage:
#   scripts/build-local-release.sh [kruxx] [--skip-build]
#     --skip-build only signs the existing unsigned KruXx APK
#
set -euo pipefail

SKIP_BUILD=0
for arg in "$@"; do
    case "$arg" in
        --skip-build) SKIP_BUILD=1 ;;
        kruxx) ;;
        *) echo "usage: $0 [kruxx] [--skip-build]" >&2; exit 2 ;;
    esac
done

FLAVOR="kruxx"
FLAVOR_CAP="Kruxx"
APP_NAME="KruXx"
EXPECTED_PACKAGE="de.kruxx.music"
EXPECTED_SIGNING_CERT_SHA256="5dc08df341c5d5b56aa9fe9ebc58eb02e0a25bc4a27b48d83a4fbe31ccbdd673"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT/.ignore.d/keystores/kreate-local.properties"
OUT_DIR="$ROOT/composeApp/build/outputs/apk/${FLAVOR}UniversalProd/release"
UNSIGNED="$OUT_DIR/$APP_NAME-release.apk"
SIGNED="$OUT_DIR/$APP_NAME-release-signed.apk"
DEBUG_APK="$ROOT/composeApp/build/outputs/apk/${FLAVOR}UniversalProd/debug/$APP_NAME-debug.apk"
ARCHIVE_DIR="/home/kruxx/Schreibtisch/Android/Kreate-APKs"

command -v git >/dev/null 2>&1 || { echo "error: git is required" >&2; exit 1; }
command -v unzip >/dev/null 2>&1 || { echo "error: unzip is required" >&2; exit 1; }

# AGP records the checked-out revision in META-INF/version-control-info.textproto. A dirty build
# would attribute uncommitted source to the wrong revision, so a release requires a clean tree.
if [[ -n "$(git -C "$ROOT" status --porcelain --untracked-files=all --ignore-submodules=none)" ]]; then
    echo "error: release source tree is not clean; commit or remove all intended source changes first" >&2
    exit 1
fi
SOURCE_REVISION="$(git -C "$ROOT" rev-parse HEAD)"

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
AAPT2="$BUILD_TOOLS/aapt2"

apk_version_name() {
    "$AAPT2" dump badging "$1" \
        | sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" \
        | head -1
}

apk_version_code() {
    "$AAPT2" dump badging "$1" \
        | sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p" \
        | head -1
}

apk_package_name() {
    "$AAPT2" dump badging "$1" \
        | sed -n "s/^package: name='\([^']*\)'.*/\1/p" \
        | head -1
}

archive_apk() {
    local source_apk="$1"
    local build_type="$2"
    local version_name="$3"
    local archived_apk="$ARCHIVE_DIR/$APP_NAME-$version_name-$build_type.apk"

    install -m 0644 "$source_apk" "$archived_apk"
    echo ">> archived: $archived_apk"
}

if [[ "$SKIP_BUILD" -eq 0 ]]; then
    echo ">> building $FLAVOR debug and release APKs from the same source (this takes a few minutes) ..."
    "$ROOT/gradlew" -p "$ROOT" \
        ":composeApp:assemble${FLAVOR_CAP}UniversalProdDebug" \
        ":composeApp:assemble${FLAVOR_CAP}UniversalProdRelease" \
        --console=plain -q
fi
[[ -f "$UNSIGNED" ]] || { echo "error: $UNSIGNED not found - did the build succeed?" >&2; exit 1; }

echo ">> aligning ..."
ALIGNED="$(mktemp --suffix=.apk)"
cleanup() {
    rm -f "$ALIGNED"
    unset KRUXX_STORE_PASSWORD KRUXX_KEY_PASSWORD STORE_PASSWORD KEY_PASSWORD
}
trap cleanup EXIT
# -P 16: 16 KB page alignment for stored native libraries (Android 15+).
# Older build-tools without -P fall back to their legacy 4 KB page-alignment option.
if ! "$ZIPALIGN" -f -P 16 4 "$UNSIGNED" "$ALIGNED" 2>/dev/null; then
    "$ZIPALIGN" -f -p 4 "$UNSIGNED" "$ALIGNED"
fi

echo ">> signing ..."
export KRUXX_STORE_PASSWORD="$STORE_PASSWORD"
export KRUXX_KEY_PASSWORD="$KEY_PASSWORD"
"$APKSIGNER" sign \
    --ks "$STORE_FILE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:KRUXX_STORE_PASSWORD --key-pass env:KRUXX_KEY_PASSWORD \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$SIGNED" "$ALIGNED"
unset KRUXX_STORE_PASSWORD KRUXX_KEY_PASSWORD STORE_PASSWORD KEY_PASSWORD

echo ">> verifying signature and package metadata ..."
VERIFY_OUTPUT="$("$APKSIGNER" verify --verbose --print-certs "$SIGNED")"
printf '%s\n' "$VERIFY_OUTPUT" | grep -E '^(Verifies|Verified using|Signer #1 certificate (DN|SHA-256))' || true

ACTUAL_CERT_SHA256="$(
    printf '%s\n' "$VERIFY_OUTPUT" \
        | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
        | head -1 \
        | tr -d ':' \
        | tr '[:upper:]' '[:lower:]'
)"
[[ "$ACTUAL_CERT_SHA256" == "$EXPECTED_SIGNING_CERT_SHA256" ]] || {
    echo "error: release APK has an unexpected signing certificate" >&2
    exit 1
}

VERSION_NAME="$(apk_version_name "$SIGNED")"
[[ -n "$VERSION_NAME" ]] || { echo "error: failed to read versionName from $SIGNED" >&2; exit 1; }
[[ "$VERSION_NAME" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
    echo "error: release versionName must be stable MAJOR.MINOR.PATCH (found $VERSION_NAME)" >&2
    exit 1
}

VERSION_CODE="$(apk_version_code "$SIGNED")"
[[ "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]] || {
    echo "error: invalid versionCode in $SIGNED" >&2
    exit 1
}

PACKAGE_NAME="$(apk_package_name "$SIGNED")"
[[ "$PACKAGE_NAME" == "$EXPECTED_PACKAGE" ]] || {
    echo "error: release package is $PACKAGE_NAME, expected $EXPECTED_PACKAGE" >&2
    exit 1
}

EMBEDDED_SOURCE_REVISION="$(
    unzip -p "$SIGNED" META-INF/version-control-info.textproto 2>/dev/null \
        | sed -n 's/^[[:space:]]*revision: "\([0-9a-f]\{40\}\)"/\1/p' \
        | head -1
)"
[[ "$EMBEDDED_SOURCE_REVISION" == "$SOURCE_REVISION" ]] || {
    echo "error: APK source revision does not match the checked-out Git commit" >&2
    exit 1
}

echo ">> done: $SIGNED ($(du -h "$SIGNED" | cut -f1), version $VERSION_NAME/$VERSION_CODE)"
echo ">> source revision: $SOURCE_REVISION"
sha256sum "$SIGNED"

mkdir -p "$ARCHIVE_DIR"
archive_apk "$SIGNED" "release" "$VERSION_NAME"

# A normal release run builds both variants together, so the archived debug APK comes from the
# same source tree. In --skip-build mode its provenance is unknown and it must not be re-archived.
if [[ "$SKIP_BUILD" -eq 0 && -f "$DEBUG_APK" ]]; then
    DEBUG_VERSION_NAME="$(apk_version_name "$DEBUG_APK")"
    if [[ "$DEBUG_VERSION_NAME" == "$VERSION_NAME" ]]; then
        archive_apk "$DEBUG_APK" "debug" "$VERSION_NAME"
    else
        echo ">> not archiving stale debug APK (found ${DEBUG_VERSION_NAME:-unknown}, expected $VERSION_NAME)" >&2
    fi
elif [[ "$SKIP_BUILD" -ne 0 ]]; then
    echo ">> not archiving debug APK in --skip-build mode (source provenance not verified)"
fi
