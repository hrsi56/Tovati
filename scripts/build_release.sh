#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
KEYSTORE_PROPERTIES="$ROOT_DIR/keystore.properties"

fail() {
    printf 'Release build failed: %s\n' "$*" >&2
    exit 1
}

property_value() {
    local property_name="$1"
    sed -n "s/^[[:space:]]*${property_name}[[:space:]]*=[[:space:]]*//p" \
        "$KEYSTORE_PROPERTIES" | tail -n 1
}

find_apksigner() {
    local sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    local candidate=""

    if command -v apksigner >/dev/null 2>&1; then
        command -v apksigner
        return 0
    fi
    if [[ -z "$sdk_dir" && -f "$ROOT_DIR/local.properties" ]]; then
        sdk_dir="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' \
            "$ROOT_DIR/local.properties" | tail -n 1)"
    fi
    if [[ -n "$sdk_dir" ]]; then
        candidate="$(find "$sdk_dir/build-tools" -type f -name apksigner 2>/dev/null \
            | sort | tail -n 1 || true)"
    fi
    [[ -n "$candidate" && -x "$candidate" ]] || return 1
    printf '%s\n' "$candidate"
}

[[ -f "$KEYSTORE_PROPERTIES" ]] || fail \
    "keystore.properties is required (copy keystore.properties.example and provide real signing values)"

for property_name in storeFile storePassword keyAlias keyPassword; do
    value="$(property_value "$property_name")"
    [[ -n "$value" ]] || fail "missing $property_name in keystore.properties"
    [[ "$value" != *CHANGE_ME* ]] || fail "$property_name still contains the example placeholder"
done

store_file="$(property_value storeFile)"
if [[ "$store_file" != /* ]]; then
    # Gradle's file(...) call is evaluated from the app module directory.
    store_file="$ROOT_DIR/app/$store_file"
fi
[[ -f "$store_file" ]] || fail "signing keystore does not exist: $store_file"

cd "$ROOT_DIR"

printf 'Running unit tests, release lint, and signed release assembly...\n'
./gradlew --no-daemon test lintRelease assembleRelease

apk_path="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk_path" ]]; then
    apk_path="$(find "$ROOT_DIR/app/build/outputs/apk/release" -maxdepth 1 -type f \
        -name '*.apk' ! -name '*-unsigned.apk' 2>/dev/null | sort | head -n 1 || true)"
fi
[[ -n "$apk_path" && -f "$apk_path" ]] || fail "no signed release APK was produced"

"$SCRIPT_DIR/verify_permissions.sh" "$apk_path"

apksigner="$(find_apksigner || true)"
[[ -n "$apksigner" ]] || fail "apksigner is required to verify the release APK"
"$apksigner" verify --verbose --print-certs "$apk_path"

checksum_path="${apk_path}.sha256"
if command -v shasum >/dev/null 2>&1; then
    (cd "$(dirname -- "$apk_path")" && shasum -a 256 "$(basename -- "$apk_path")") \
        > "$checksum_path"
elif command -v sha256sum >/dev/null 2>&1; then
    (cd "$(dirname -- "$apk_path")" && sha256sum "$(basename -- "$apk_path")") \
        > "$checksum_path"
else
    fail "neither shasum nor sha256sum is available"
fi

printf 'Release APK: %s\n' "$apk_path"
printf 'SHA-256:    %s\n' "$checksum_path"
