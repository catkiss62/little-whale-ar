#!/usr/bin/env bash
# Sign a CI release candidate and discard the output if the pinned identity differs.
set -euo pipefail
if [[ $# != 2 ]]; then echo 'usage: bash scripts/sign-apk.sh unsigned.apk output.apk' >&2; exit 2; fi
: "${AR_KEYSTORE:?Set the private PKCS12 path}" "${AR_PASSWORD_FILE:?Set the private password file path}"
signer=${APKSIGNER:-apksigner}
expected=43df484ab6e086c882fd3cb8719c8de3439c81151f59b96af76faac3d4037b61
stage=$(mktemp "${2}.XXXXXX")
trap 'rm -f "$stage"' EXIT
"$signer" sign --ks "$AR_KEYSTORE" --ks-key-alias little-whale-ar --ks-pass "file:$AR_PASSWORD_FILE" \
    --v1-signing-enabled false --v4-signing-enabled false --out "$stage" "$1"
report=$("$signer" verify --verbose --print-certs "$stage")
grep -Fqx "Signer #1 certificate SHA-256 digest: $expected" <<< "$report"
mv "$stage" "$2"
sha256sum "$2"
