#!/usr/bin/env bash
set -euo pipefail
pkg=dev.littlewhale.ar
apk=Little-Whale-AR-0.3.0-arm64.apk
adb shell getprop ro.product.cpu.abilist
adb install legacy/Little-Whale-AR-0.2.0-arm64.apk
if adb install -r "$apk" > /tmp/legacy-upgrade.log 2>&1; then exit 1; fi
grep -F INSTALL_FAILED_UPDATE_INCOMPATIBLE /tmp/legacy-upgrade.log
adb uninstall "$pkg"
adb install /tmp/baseline.apk
adb root
adb wait-for-device
data="/data/user/0/$pkg"
uid=$(adb shell stat -c %u "$data" | tr -d '\r')
adb shell "mkdir -p '$data/files' && echo qa-private-model-marker > '$data/files/SmallBlueFish-V21.vrm' && chown -R $uid:$uid '$data/files' && restorecon -R '$data/files'"
adb install -r "$apk"
adb shell dumpsys package "$pkg" | grep -F 'versionCode=3 '
test "$(adb shell cat "$data/files/SmallBlueFish-V21.vrm" | tr -d '\r')" = qa-private-model-marker
echo 'Signed versionCode 2 -> 3 preserved test marker at the model path'
