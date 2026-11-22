#!/usr/bin/env bash
set -euo pipefail
repo=$PWD
work=/tmp/ar-palm-test
signed="$repo/Little-Whale-AR-0.7.0-arm64.apk"
mkdir -p "$work"/{assets,classes,dex,payload/lib/x86_64,src}
cd "$work"
bt=$(dirname "$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name aapt2 | sort -V | tail -1)")
platform="$ANDROID_HOME/platforms/android-35/android.jar"
cp "$repo/verify/PalmProbe.java" src/
# Compile against the target's package-private signatures. Only the probe goes into the extra dex.
cat >src/Signature.java <<'JAVA'
package dev.littlewhale.ar;
class CharacterView extends android.webkit.WebView {
 String state;int renderFps;CharacterView(android.content.Context c){super(c);}
}
class PalmTracking{
 PalmTracking(android.content.Context c,CharacterView v){} void close(){} void offer(android.graphics.Bitmap b){}
 String status(){return "";} boolean failed(){return false;}
}
JAVA
javac -source 8 -target 8 -classpath "$platform" -d classes src/*.java
jar cf probe.jar -C classes dev/littlewhale/ar/PalmProbe.class
"$bt/d8" --min-api 29 --lib "$platform" --output dex probe.jar
unzip -q "$signed" 'assets/viewer/*' 'assets/hand_landmarker.task'
unzip -q "$signed" 'classes*.dex' -d payload
n=$(find payload -name 'classes*.dex' | wc -l)
cp dex/classes.dex "payload/classes$((n+1)).dex"
curl -fsSL --retry 3 https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-vision/0.10.32/tasks-vision-0.10.32.aar -o vision.aar
curl -fsSL --retry 3 https://storage.googleapis.com/mediapipe-tasks/hand_landmarker/woman_hands.jpg -o assets/woman_hands.jpg
for f in fist right_hands; do curl -fsSL --retry 3 "https://storage.googleapis.com/mediapipe-assets/$f.jpg" -o "assets/$f.jpg"; done
sha256sum -c <<'HASH'
d6e69475707d07a24478e9ff00c437c48c9a834ffb04d4e6e0ba907defa87777  vision.aar
70cbeb38e198c9862202e0979c21a99b40ca980d3e7b250176c85b1636a40f12  assets/woman_hands.jpg
4b5134daa4cb60465535239535f9f74c2842aba3aa5fd30bf04ef5678f93d87f  assets/right_hands.jpg
43fa1cabf3f90d574accc9a56986e2ee48638ce59fc65af1846487f73bb2ef24  assets/fist.jpg
HASH
unzip -p vision.aar jni/x86_64/libmediapipe_tasks_vision_jni.so >payload/lib/x86_64/libmediapipe_tasks_vision_jni.so
cat >AndroidManifest.xml <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.littlewhale.palmtest"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="35"/><application android:hardwareAccelerated="true" android:extractNativeLibs="true" android:theme="@android:style/Theme.Material.NoActionBar"><activity android:name="dev.littlewhale.ar.PalmProbe" android:exported="true"/></application></manifest>
XML
"$bt/aapt2" link -I "$platform" --manifest AndroidManifest.xml -A assets -0 task -o palm.apk
(cd payload && zip -qr ../palm.apk .)
"$bt/zipalign" -f 4 palm.apk aligned.apk
"$bt/apksigner" sign --ks /tmp/ar-touch-host/qa.p12 --ks-pass pass:ephemeral --out /tmp/palm-host.apk aligned.apk
