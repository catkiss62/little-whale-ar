#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
src=${ANIMALESE_SOURCE:-app/build/animalese}
revision=014ec5c6a5c14b3d4c4dab91119d6de6ca838ae4
if [[ ! -d "$src/.git" ]]; then
    git init -q "$src"
    git -C "$src" fetch -q --depth=1 https://github.com/smmcat/animalese-api.git "$revision"
    git -C "$src" checkout -q --detach FETCH_HEAD
fi
test "$(git -C "$src" rev-parse HEAD)" = "$revision"
node scripts/render-voices.cjs "$src" "${1:-app/build/voice-assets/voices}"
