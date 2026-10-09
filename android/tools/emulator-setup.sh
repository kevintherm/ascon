#!/usr/bin/env bash
# Prepares an emulator for scripted UI flows. Starts one when no device is connected. The settings persist, so after the first run this only boots the emulator.
# - Portrait, so coordinates and layouts match the designs.
# - No animations, so flows never wait on a transition.
# - No stylus handwriting, whose tutorial covers the screen on the first text field.
# Usage: tools/emulator-setup.sh [avd-name]    default avd: PHONE
set -euo pipefail

AVD="${1:-PHONE}"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$SDK/platform-tools/adb"

if ! "$ADB" devices | grep -qw "device$"; then
    # With a window: -no-window crashes in the emulator's software GPU on this setup.
    echo "Starting emulator $AVD"
    nohup "$SDK/emulator/emulator" -avd "$AVD" -no-audio -no-boot-anim >/dev/null 2>&1 &
    "$ADB" wait-for-device
    until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 2
    done
fi

"$ADB" shell settings put system accelerometer_rotation 0
"$ADB" shell settings put system user_rotation 0
for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$ADB" shell settings put global "$scale" 0
done
"$ADB" shell settings put secure stylus_handwriting_enabled 0
echo "Emulator ready: portrait, no animations, no stylus handwriting."
