#!/usr/bin/env bash
set -eu
repo=$PWD
work=/tmp/ar-touch-host
mkdir -p "$work"/{assets/viewer,src,classes,dex}
cd "$work"
bt=$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name aapt2 | sort -V | tail -1)
bt=$(dirname "$bt")
platform="$ANDROID_HOME/platforms/android-35/android.jar"
unzip -jo "$repo/Little-Whale-AR-0.8.0-arm64.apk" 'assets/viewer/*' -d assets/viewer
cp "$repo"/app/src/main/java/dev/littlewhale/ar/{CharacterView,CharacterFile,VrmCopy,ReferenceMotions}.java src/
cp "$repo/verify/TouchHost.java" src/
cat >src/MenthaAR.java <<'JAVA'
package com.menthaar.sdk;
public class MenthaAR { public static class Result {
    public final float[] view=new float[16],model=new float[16],projection=new float[16];
} }
JAVA
cat >src/DisplayCorrection.java <<'JAVA'
package com.orb.slam2s.graphics;
public class DisplayCorrection {
    private final float[] ndc=new float[16];
    public DisplayCorrection(){android.opengl.Matrix.setIdentityM(ndc,0);}
    public float[] getNdcTransform(){return ndc;}
}
JAVA
cat >AndroidManifest.xml <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.littlewhale.touchtest"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="35"/><application android:hardwareAccelerated="true" android:debuggable="true" android:theme="@android:style/Theme.Material.NoActionBar"><activity android:name="dev.littlewhale.ar.TouchHost" android:exported="true"/></application></manifest>
XML
javac -source 8 -target 8 -classpath "$platform" -d classes src/*.java
jar cf input.jar -C classes .
"$bt/d8" --min-api 29 --lib "$platform" --output dex input.jar
"$bt/aapt2" link -I "$platform" --manifest AndroidManifest.xml -A assets -o host.apk
(cd dex && zip -q ../host.apk classes.dex)
"$bt/zipalign" -f 4 host.apk aligned.apk
keytool -genkeypair -keystore qa.p12 -storepass ephemeral -alias qa -keyalg RSA -dname CN=Test -validity 2
"$bt/apksigner" sign --ks qa.p12 --ks-pass pass:ephemeral --out /tmp/touch-host.apk aligned.apk
curl -fsSL --retry 2 -o /tmp/character.vrm 'https://github.com/CF-612/SmallBlueFish-Model/releases/download/dot-test-20261104-v21-fullmodel/SmallBlueFish-V21-AllExpressions-Acceptance.vrm'
echo 'cf331c0f5c62f818b05bb7f132bd34424bdd99784bb908d5f4651d901f855514  /tmp/character.vrm' | sha256sum -c
# Test the offline importer using the pinned author's files.
base='https://github.com/CF-612/YuChat/releases/download/v0.5.1-quest-test/Desktop-SmallBlueFish-Quest.apk'
curl -fsSL --retry 2 "$base" -o /tmp/author-quest.apk
unzip -p /tmp/author-quest.apk assets/Motions/local-grab/grabbed.vrma > /tmp/hold.vrma
unzip -p /tmp/author-quest.apk assets/Motions/local-locomotion/locomotion_walk.vrma > /tmp/walk.vrma
echo 'c51caaa527309216f5edccefdadc2ac73cc96837735e8124041d52a6646d0185  /tmp/hold.vrma' | sha256sum -c
echo '0d68383cffb93d9c1d1e98ab3cdd303562908bb4cabd24d9ae1eca438797eb92  /tmp/walk.vrma' | sha256sum -c
mkdir -p /tmp/motion-import
cp /tmp/hold.vrma /tmp/motion-import/grabbed.vrma
cp /tmp/walk.vrma /tmp/motion-import/locomotion_walk.vrma
(cd /tmp/motion-import && zip -q /tmp/test-motions.zip *.vrma)
