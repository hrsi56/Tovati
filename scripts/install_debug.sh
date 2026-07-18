#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"

fail() {
    printf 'Debug install failed: %s\n' "$*" >&2
    exit 1
}

sdk_directory() {
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        printf '%s\n' "$ANDROID_HOME"
    elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        printf '%s\n' "$ANDROID_SDK_ROOT"
    elif [[ -f "$ROOT_DIR/local.properties" ]]; then
        sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' \
            "$ROOT_DIR/local.properties" | tail -n 1
    fi
}

find_adb() {
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return
    fi
    local sdk_dir
    sdk_dir="$(sdk_directory || true)"
    if [[ -n "$sdk_dir" && -x "$sdk_dir/platform-tools/adb" ]]; then
        printf '%s\n' "$sdk_dir/platform-tools/adb"
        return
    fi
    return 1
}

if (( $# > 1 )); then
    printf 'Usage: scripts/install_debug.sh [path/to/debug.apk]\n' >&2
    exit 2
fi

apk_path="${1:-}"
if [[ -z "$apk_path" ]]; then
    cd "$ROOT_DIR"
    ./gradlew --no-daemon assembleDebug
    apk_path="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
elif [[ "$apk_path" != /* ]]; then
    apk_path="$ROOT_DIR/$apk_path"
fi

[[ -f "$apk_path" ]] || fail "APK does not exist: $apk_path"
"$SCRIPT_DIR/verify_permissions.sh" "$apk_path"

adb_path="$(find_adb || true)"
[[ -n "$adb_path" ]] || fail "adb was not found; install Android platform-tools or set ANDROID_HOME"
"$adb_path" start-server >/dev/null

serial="${ANDROID_SERIAL:-}"
if [[ -n "$serial" ]]; then
    state="$("$adb_path" -s "$serial" get-state 2>/dev/null || true)"
    [[ "$state" == device ]] || fail "ANDROID_SERIAL=$serial is not an authorized online device"
else
    devices="$("$adb_path" devices | awk '$2 == "device" { print $1 }')"
    device_count="$(printf '%s\n' "$devices" | awk 'NF { count++ } END { print count + 0 }')"
    if [[ "$device_count" -eq 0 ]]; then
        "$adb_path" devices -l >&2
        fail "no authorized Android device or emulator is online"
    fi
    if [[ "$device_count" -gt 1 ]]; then
        printf '%s\n' "$devices" >&2
        fail "multiple devices are online; set ANDROID_SERIAL to choose one"
    fi
    serial="$(printf '%s\n' "$devices" | awk 'NF { print; exit }')"
fi

printf 'Installing %s on %s...\n' "$apk_path" "$serial"
"$adb_path" -s "$serial" install -r "$apk_path"

printf 'Launching BBT Fertility Tracker...\n'
"$adb_path" -s "$serial" shell am start \
    -n com.yv.bbttracker.debug/com.yv.bbttracker.MainActivity >/dev/null
printf 'Debug app installed and launched.\n'
