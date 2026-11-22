#!/usr/bin/env bash
set -euo pipefail
pkg=dev.littlewhale.ar
adb root
adb wait-for-device
adb shell wm size 480x672
adb shell wm density 160
adb install legacy/Little-Whale-AR-0.6.2-arm64.apk
adb push /tmp/character.vrm /data/local/tmp/whale-test.vrm
seed(){
  local dir="/data/user/0/$1" uid
  uid=$(adb shell stat -c %u "$dir" | tr -d '\r')
  adb shell "mkdir -p '$dir/files' && cp /data/local/tmp/whale-test.vrm '$dir/files/SmallBlueFish-V21.vrm' && chown -R $uid:$uid '$dir/files' && restorecon -R '$dir/files'"
}
seed "$pkg"
adb install -r Little-Whale-AR-0.7.0-arm64.apk
adb shell dumpsys package "$pkg" | grep -F 'versionCode=10 '
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
  if grep -Fx 'native area retained' /tmp/area-result; then break; fi
  sleep 1
done
grep -Fx 'native area retained' /tmp/area-result
grip=
for n in $(seq 1 30); do
  grip=$(adb logcat -d -s WhaleTouchTest:I -v raw | sed -n 's/^grip coordinates //p' | tr -d '\r' | tail -1)
  if [[ -n "$grip" ]]; then break; fi
  sleep .3
done
test -n "$grip"
read -r gx gy gx2 <<<"$grip"
adb shell input motionevent DOWN "$gx" "$gy"
for n in $(seq 1 20); do
  adb logcat -d -s WhaleTouchTest:I -v raw > /tmp/hold-result
  if grep -Fx 'valid floor shown' /tmp/hold-result; then break; fi
  sleep .4
done
tail -28 /tmp/hold-result
grep -Fx 'valid floor shown' /tmp/hold-result
adb shell input motionevent MOVE "$gx2" "$gy"
sleep .6
adb shell input motionevent UP "$gx2" "$gy"
for n in $(seq 1 16); do
  adb logcat -d -s WhaleTouchTest:I -v raw > /tmp/hold-result
  if grep -Fx 'body held, dragged and released on valid floor' /tmp/hold-result; then break; fi
  sleep .5
done
tail -28 /tmp/hold-result
grep -Fx 'body held, dragged and released on valid floor' /tmp/hold-result
adb shell am force-stop dev.littlewhale.touchtest
adb install /tmp/palm-host.apk
seed dev.littlewhale.palmtest
adb logcat -c
adb shell am start -n dev.littlewhale.palmtest/dev.littlewhale.ar.PalmProbe
for n in $(seq 1 90); do
  adb logcat -d -s WhalePalmQA:I WhalePalm:E AndroidRuntime:E -v raw > /tmp/palm-result
  if grep -Fx 'stopped feed hidden' /tmp/palm-result; then cat /tmp/palm-result; exit 0; fi
  if grep -E 'phase [0-9]+ failed|FATAL EXCEPTION|model failed|accepted closed fist' /tmp/palm-result; then break; fi
  sleep 2
done
tail -60 /tmp/palm-result
exit 1
