#!/usr/bin/env bash
# Add the locally licensed SDK to the unsigned ARM64 CI base before normal APK signing.
# Inputs: extracted official distribution, license file, packaging-tools artifact,
# unsigned CI base, destination unsigned APK. Requires JDK 17+ and Python 3.
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
sdk=$(realpath "$1")
license=$(realpath "$2")
tools=$(realpath "$3")
base=$(realpath "$4")
destination=$(realpath -m "$5")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
character_api="$(dirname "$base")/character-api.jar"
test -f "$character_api"
cd "$work"
mkdir classes dex
aar="$sdk/variants/Community/Android/EasyAR.aar"
sample="$sdk/samples/Community/Android/HelloARMotionTracking/app/src/main/java/cn/easyar/samples/helloarmotiontracking"
unzip -p "$aar" classes.jar > easyar.jar
java -m jdk.compiler/com.sun.tools.javac.Main -source 8 -target 8 -encoding UTF-8 -d classes \
  -cp "$tools/android.jar:easyar.jar:$character_api" "$sample/BGRenderer.java" \
  "$repo"/app/src/easyar/java/dev/littlewhale/ar/*.java
java -m jdk.jartool/sun.tools.jar.Main cf adapter.jar -C classes .
java -cp "$tools/d8.jar" com.android.tools.r8.D8 --release --min-api 29 --lib "$tools/android.jar" \
  --classpath "$character_api" --output dex adapter.jar easyar.jar
unzip -p "$aar" jni/arm64-v8a/libEasyAR.so > libEasyAR.so
cp "$base" merged.apk
python3 - "$license" "$(dirname "$base")/motions" "$repo/docs/yuchat-motion-files.tsv" <<'PY'
from pathlib import Path
import csv, hashlib, re, sys, zipfile
with zipfile.ZipFile('merged.apk', 'a') as apk:
    names = set(apk.namelist())
    assert 'assets/easyar-license.txt' not in names
    assert 'lib/arm64-v8a/libEasyAR.so' not in names
    number = 1 + max(int(n or 1) for p in names for n in re.findall(r'^classes(\d*)\.dex$', p))
    for dex in sorted(Path('dex').glob('*.dex')):
        apk.write(dex, f'classes{number}.dex', zipfile.ZIP_DEFLATED)
        number += 1
    apk.write('libEasyAR.so', 'lib/arm64-v8a/libEasyAR.so', zipfile.ZIP_STORED)
    apk.write(sys.argv[1], 'assets/easyar-license.txt', zipfile.ZIP_DEFLATED)
    with open(sys.argv[3], encoding='utf-8') as inventory:
        digests = {row['动作 ID']: row['SHA-256'] for row in csv.DictReader(inventory, delimiter='\t')}
    selected = """kay_pickup ual2_walk_carry_loop grabbed jitabata locomotion_walk locomotion_left locomotion_right
        idle_normal_both_550 kay_idle_a ual2_idle_foldarms_loop hands_back_reserved_both_560
        tilt_reserved_head_616 calm_reserved_both_729 think_reserved_both_643
        sigh_reserved_both_696 wave_normal_right_661""".split()
    for name in selected:
        clip = Path(sys.argv[2]) / (name + '.vrma')
        if hashlib.sha256(clip.read_bytes()).hexdigest() != digests[name]:
            raise ValueError(f'动作校验失败 {name}')
        apk.write(clip, 'assets/motions/' + clip.name, zipfile.ZIP_DEFLATED)
PY
"$tools/zipalign" -f -P 16 4 merged.apk "$destination"
"$tools/zipalign" -c -P 16 4 "$destination"
