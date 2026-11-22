#!/usr/bin/env bash
set -euo pipefail
pkg=dev.littlewhale.ar
adb root
adb wait-for-device
adb shell wm size 600x840
adb shell wm density 200
adb install legacy/Little-Whale-AR-0.5.0-arm64.apk
adb push /tmp/character.vrm /data/local/tmp/whale-test.vrm
seed(){
  local dir="/data/user/0/$1" uid
  uid=$(adb shell stat -c %u "$dir" | tr -d '\r')
  adb shell "mkdir -p '$dir/files' && cp /data/local/tmp/whale-test.vrm '$dir/files/SmallBlueFish-V21.vrm' && chown -R $uid:$uid '$dir/files' && restorecon -R '$dir/files'"
}
seed "$pkg"
adb install -r Little-Whale-AR-0.5.1-arm64.apk
adb shell dumpsys package "$pkg" | grep -F 'versionCode=6 '
adb shell sha256sum "/data/user/0/$pkg/files/SmallBlueFish-V21.vrm" | grep -F cf331c0f5c62f818b05bb7f132bd34424bdd99784bb908d5f4651d901f855514
adb install /tmp/touch-host.apk
seed dev.littlewhale.touchtest
adb logcat -c
adb shell am start -n dev.littlewhale.touchtest/dev.littlewhale.ar.TouchHost
coords=
for i in $(seq 1 80); do
  coords=$(adb logcat -d -s WhaleTouchTest:I -v raw | sed -n 's/^stroke //p' | tr -d '\r' | head -1)
  if [[ -n "$coords" ]]; then break; fi
  if adb logcat -d -s WhaleTouchTest:E -v raw | grep -q 'renderer error'; then break; fi
  sleep 2
done
if [[ -z "$coords" ]]; then adb logcat -d -s chromium:E AndroidRuntime:E WhaleTouchTest:E | tail -50; exit 1; fi
read -r x1 y1 x2 y2 <<<"$coords"
adb shell input swipe "$x1" "$y1" "$x2" "$y2" 1450
sleep 2
adb logcat -d -s WhaleTouchTest:I -v raw | tee /tmp/whale-touch.log
grep -Fx 'pet entered' /tmp/whale-touch.log
grep -Fx 'pet released' /tmp/whale-touch.log

for n in $(seq 1 15); do
  adb logcat -d -s WhaleTouchTest:I -v raw > /tmp/area-result
  if grep -Fx 'native area retained' /tmp/area-result; then exit 0; fi
  sleep 1
done
tail -15 /tmp/area-result
exit 1
