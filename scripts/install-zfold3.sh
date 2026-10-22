#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
command -v adb >/dev/null || { echo 'ADB missing: install Android platform-tools'; exit 1; }
./gradlew :app:assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo 'Debug APK missing'; exit 1; }
ARGS=()
if [[ -n "${ADB_SERIAL:-}" ]]; then ARGS=(-s "$ADB_SERIAL"); fi
adb "${ARGS[@]}" devices -l
adb "${ARGS[@]}" install -r "$APK"
adb "${ARGS[@]}" shell am start -n dev.kun.siwcpoc/.MainActivity
printf '\nInstalled. Open Chrome login on the SAME Z Fold3 and return to app.\n'
