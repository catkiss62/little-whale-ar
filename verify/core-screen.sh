#!/usr/bin/env bash
# Drive the signed application's public launcher and its actual test menu.
set -euo pipefail
pkg=dev.littlewhale.ar
adb install -r /tmp/ar-service.apk
adb shell pm grant "$pkg" android.permission.CAMERA
adb shell am start -W -n "$pkg/.TableActivity"
click_text(){
  local label="$1" coords=""
  for attempt in {1..12}; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null || true
    adb pull /sdcard/window.xml /tmp/ar-window.xml >/dev/null 2>&1 || true
    coords=$(python3 - "$label" <<'PY'
import sys,re,xml.etree.ElementTree as ET
try: tree=ET.parse('/tmp/ar-window.xml')
except (IOError,ET.ParseError):sys.exit()
for e in tree.iter('node'):
 if sys.argv[1] in e.get('text',''):
  x,y,u,v=map(int,re.findall(r'\d+',e.get('bounds','')))
  print((x+u)//2,(y+v)//2);break
PY
)
    if [[ -n "$coords" ]]; then adb shell input tap $coords; return; fi
    sleep .5
  done
  adb logcat -d -s AndroidRuntime:E | tail -24
  return 1
}
click_text '定位对照'
click_text 'ARCore ·'
check_state(){
for i in {1..6}; do
  sleep 1
  adb shell uiautomator dump /sdcard/window.xml >/dev/null || true
  adb pull /sdcard/window.xml /tmp/ar-core.xml >/dev/null 2>&1 || true
  if python3 - <<'PY'
import xml.etree.ElementTree as E
try: labels=[n.get('text','') for n in E.parse('/tmp/ar-core.xml').iter('node')]
except Exception:exit(1)
expected=('ARCore 已启动','定位正常','ARCore 定位中')
found=[s for s in labels if s.startswith(expected)]
if not found:exit(1)
print('Native ARCore comparison screen:',found)
PY
  then return 0; fi
done
python3 - <<'PY'
import xml.etree.ElementTree as E
print([n.get('text','') for n in E.parse('/tmp/ar-core.xml').iter('node') if n.get('text')])
PY
adb logcat -d | grep -Ei -A5 'Error while checking app details|LoadSymbolsDynamite|AR_ERROR|FATAL EXCEPTION|Failure loading' | tail -22 | cut -c1-280 || true
return 1
}
if check_state; then adb shell input keyevent 4; exit 0; fi
adb shell am force-stop "$pkg"
echo 'Signed ARM launcher opened the native lab. Trying the SDK x86_64 build in the virtual camera.'
bash verify/build-core-host.sh
adb uninstall "$pkg"
adb install /tmp/core-host.apk
adb shell pm grant dev.littlewhale.ar android.permission.CAMERA
adb shell am start -W -n dev.littlewhale.ar/dev.littlewhale.ar.CoreLabActivity
check_state
adb shell am force-stop dev.littlewhale.ar
