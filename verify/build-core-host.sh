#!/usr/bin/env bash
# Keep the release manifest, resources and DEX (including ARCore SDK metadata).
# Substitute only the architecture-specific SDK libraries on the emulator.
set -euo pipefail
repo=$PWD
dir=/tmp/ar-core-host
mkdir -p "$dir/lib/x86_64"
cd "$dir"
cp "$repo/Little-Whale-AR-0.9.0-arm64.apk" core.apk
zip -qd core.apk 'lib/*'
curl -fsSL --retry 3 https://dl.google.com/dl/android/maven2/com/google/ar/core/1.53.0/core-1.53.0.aar -o core.aar
unzip -j core.aar 'jni/x86_64/*.so' -d lib/x86_64
zip -q0 core.apk lib/x86_64/*.so
bt=$(dirname "$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name aapt2 | sort -V | tail -1)")
"$bt/zipalign" -P 16 -f 4 core.apk aligned.apk
"$bt/apksigner" sign --ks /tmp/ar-touch-host/qa.p12 --ks-pass pass:ephemeral --out /tmp/core-host.apk aligned.apk
