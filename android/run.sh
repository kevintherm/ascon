#!/usr/bin/env bash
# Builds the debug app, installs it on an emulator and opens it.
# Starts the emulator first when no device is connected.
#
# Usage: ./run.sh [avd-name]    default avd: PHONE
set -euo pipefail

AVD="${1:-PHONE}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"

cd "$(dirname "$0")"

if ! "$ADB" devices | grep -qw "device$"; then
    echo "Starting emulator $AVD"
    nohup "$EMULATOR" -avd "$AVD" >/dev/null 2>&1 &
    "$ADB" wait-for-device
    until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 2
    done
fi

./gradlew installDebug
"$ADB" shell am start -n com.ascon.app/.MainActivity
