#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"

usage() {
    cat <<'EOF'
Usage: scripts/verify_permissions.sh [AndroidManifest.xml|app.apk]

Checks a merged Android manifest or packaged APK against the app's explicit
permission allowlist. Any new permission requires a deliberate script update.
When no path is supplied, the script looks for the debug APK and then for a
merged debug manifest under app/build.
EOF
}

fail() {
    printf 'Permission verification failed: %s\n' "$*" >&2
    exit 1
}

sdk_directory() {
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        printf '%s\n' "$ANDROID_HOME"
        return
    fi
    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        printf '%s\n' "$ANDROID_SDK_ROOT"
        return
    fi
    if [[ -f "$ROOT_DIR/local.properties" ]]; then
        sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' \
            "$ROOT_DIR/local.properties" | tail -n 1
    fi
}

find_android_tool() {
    local tool_name="$1"
    local sdk_dir="${2:-}"
    local candidate=""

    if command -v "$tool_name" >/dev/null 2>&1; then
        command -v "$tool_name"
        return 0
    fi

    if [[ -n "$sdk_dir" ]]; then
        case "$tool_name" in
            apkanalyzer)
                candidate="$(find "$sdk_dir/cmdline-tools" -type f -name apkanalyzer 2>/dev/null \
                    | sort | tail -n 1 || true)"
                ;;
            aapt2)
                candidate="$(find "$sdk_dir/build-tools" -type f -name aapt2 2>/dev/null \
                    | sort | tail -n 1 || true)"
                ;;
        esac
    fi

    if [[ -n "$candidate" && -x "$candidate" ]]; then
        printf '%s\n' "$candidate"
        return 0
    fi
    return 1
}

if (( $# > 1 )); then
    usage >&2
    exit 2
fi

target="${1:-}"
if [[ -z "$target" ]]; then
    for candidate in \
        "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" \
        "$ROOT_DIR/app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml" \
        "$ROOT_DIR/app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"
    do
        if [[ -f "$candidate" ]]; then
            target="$candidate"
            break
        fi
    done
fi

[[ -n "$target" ]] || fail "no APK or merged manifest was found; pass a path explicitly"
if [[ "$target" != /* ]]; then
    target="$ROOT_DIR/$target"
fi
[[ -f "$target" ]] || fail "file does not exist: $target"

permission_source=""
case "$target" in
    *.xml)
        permission_source="$(tr '\n' ' ' < "$target" \
            | grep -oE '<uses-permission(-sdk-[0-9]+)?[^>]*>' || true)"
        ;;
    *.apk)
        sdk_dir="$(sdk_directory || true)"
        if aapt2="$(find_android_tool aapt2 "$sdk_dir" || true)"; \
            [[ -n "$aapt2" ]]; then
            permission_source="$("$aapt2" dump permissions "$target")" \
                || fail "aapt2 could not read $target"
        elif apkanalyzer="$(find_android_tool apkanalyzer "$sdk_dir" || true)"; \
            [[ -n "$apkanalyzer" ]]; then
            manifest_xml="$("$apkanalyzer" manifest print "$target")" \
                || fail "apkanalyzer could not read $target"
            permission_source="$(printf '%s' "$manifest_xml" | tr '\n' ' ' \
                | grep -oE '<uses-permission(-sdk-[0-9]+)?[^>]*>' || true)"
        else
            fail "apkanalyzer or aapt2 is required to inspect an APK"
        fi
        ;;
    *)
        fail "unsupported input (expected .xml or .apk): $target"
        ;;
esac

permissions="$(printf '%s\n' "$permission_source" \
    | grep -oE 'android\.permission\.[A-Za-z0-9_]+' \
    | sort -u || true)"

printf 'Inspecting permissions in %s\n' "$target"
if [[ -n "$permissions" ]]; then
    printf '%s\n' "$permissions" | sed 's/^/  - /'
else
    printf '  (no platform permissions declared)\n'
fi

unexpected=""
while IFS= read -r permission; do
    [[ -n "$permission" ]] || continue
    case "$permission" in
        android.permission.ACCESS_NETWORK_STATE|\
        android.permission.FOREGROUND_SERVICE|\
        android.permission.POST_NOTIFICATIONS|\
        android.permission.RECEIVE_BOOT_COMPLETED|\
        android.permission.USE_BIOMETRIC|\
        android.permission.USE_FINGERPRINT|\
        android.permission.WAKE_LOCK)
            ;;
        *)
            unexpected="${unexpected}${permission}"$'\n'
            ;;
    esac
done <<< "$permissions"

if [[ -n "$unexpected" ]]; then
    printf 'Unexpected permissions detected (review before changing the allowlist):\n' >&2
    printf '%s' "$unexpected" | sed '/^$/d; s/^/  - /' >&2
    exit 1
fi

printf 'Permission verification passed.\n'
