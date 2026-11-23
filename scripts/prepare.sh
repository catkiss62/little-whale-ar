#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
revision=913d2c13ec300e9aaa148a45de2b14a5de805f63
if [[ ! -d upstream/.git ]]; then
  git init -q upstream
  git -C upstream remote add origin https://github.com/Olsc/Android_ORB-SLAM2s.git
fi
if ! git -C upstream cat-file -e "$revision^{commit}" 2>/dev/null; then
  git -C upstream fetch --depth=1 origin "$revision"
fi
git -C upstream checkout -q --detach "$revision"
git -C upstream submodule update --init --depth=1 -- MenthaAR/src/main/cpp/Thirdparty/opencv
# Keep gravity-assisted plane detection, but publish measured visual poses immediately.
if ! git -C upstream apply --reverse --check ../scripts/visual-recovery.patch 2>/dev/null; then
  git -C upstream apply ../scripts/visual-recovery.patch
fi

if ! git -C upstream apply --reverse --check ../scripts/palm-preview.patch 2>/dev/null; then
  git -C upstream apply ../scripts/palm-preview.patch
fi
if ! git -C upstream apply --reverse --check ../scripts/table-orbit.patch 2>/dev/null; then
  git -C upstream apply ../scripts/table-orbit.patch
fi
model=app/build/palm-assets/hand_landmarker.task
digest=fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1
if ! echo "$digest  $model" | sha256sum --check --status 2>/dev/null; then
  mkdir -p "$(dirname "$model")"
  curl -fsSL --retry 3 https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task -o "$model.tmp"
  echo "$digest  $model.tmp" | sha256sum --check
  mv "$model.tmp" "$model"
fi
# Reuse the tracking engine's already linked OpenCV modules in the book-cover lab.
cp app/src/main/cpp/image_reference.cpp upstream/MenthaAR/src/main/cpp/whale_image_reference.cpp
cmake_file=upstream/MenthaAR/CMakeLists.txt
if ! grep -q '^    src/main/cpp/whale_image_reference.cpp$' "$cmake_file"; then
  # An earlier checkout may have the old tail append attached to its final endif().
  sed -i 's@target_sources(MenthaAR_Engine PRIVATE src/main/cpp/whale_image_reference.cpp)@@' "$cmake_file"
  sed -i '/^add_library(MenthaAR_Engine SHARED$/a\    src/main/cpp/whale_image_reference.cpp' "$cmake_file"
  grep -Fxq '    src/main/cpp/whale_image_reference.cpp' "$cmake_file"
fi
