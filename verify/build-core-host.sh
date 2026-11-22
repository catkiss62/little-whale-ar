#!/usr/bin/env bash
# Exercise the shipping Java bytecode with the SDK's emulator-architecture libraries.
set -euo pipefail
repo=$PWD
dir=/tmp/ar-core-host
mkdir -p "$dir/lib/x86_64"
cd "$dir"
unzip -q "$repo/Little-Whale-AR-0.9.0-arm64.apk" 'classes*.dex'
curl -fsSL --retry 3 https://dl.google.com/dl/android/maven2/com/google/ar/core/1.53.0/core-1.53.0.aar -o core.aar
unzip -j core.aar 'jni/x86_64/*.so' -d lib/x86_64
cat >AndroidManifest.xml <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.littlewhale.coretest"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="35"/><uses-permission android:name="android.permission.CAMERA"/><uses-feature android:name="android.hardware.camera.ar" android:required="false"/><queries><package android:name="com.google.ar.core"/></queries><application android:extractNativeLibs="true" android:theme="@android:style/Theme.Material.NoActionBar"><meta-data android:name="com.google.ar.core" android:value="optional"/><activity android:name="dev.littlewhale.ar.CoreLabActivity" android:exported="true" android:screenOrientation="portrait"/></application></manifest>
XML
bt=$(dirname "$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name aapt2 | sort -V | tail -1)")
# Reproduce the merged SDK minimum-service metadata from the exact signed APK.
n=$("$bt/aapt2" dump xmltree --file AndroidManifest.xml "$repo/Little-Whale-AR-0.9.0-arm64.apk" | awk '/com.google.ar.core.min_apk_version/{f=1;next} f&&/android:value/{gsub(/.*=/,"");print;exit}')
if [[ "$n" =~ ^0x[0-9a-fA-F]+$|^[0-9]+$ ]]; then
  sed -i "s|<activity|<meta-data android:name=\"com.google.ar.core.min_apk_version\" android:value=\"$((n))\"/><activity|" AndroidManifest.xml
fi
"$bt/aapt2" link -I "$ANDROID_HOME/platforms/android-35/android.jar" --manifest AndroidManifest.xml -o core.apk
zip -qr core.apk classes*.dex lib
"$bt/zipalign" -f 4 core.apk aligned.apk
"$bt/apksigner" sign --ks /tmp/ar-touch-host/qa.p12 --ks-pass pass:ephemeral --out /tmp/core-host.apk aligned.apk
