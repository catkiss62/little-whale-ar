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
