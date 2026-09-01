#!/bin/bash
set -euo pipefail

output_dir=${1:?missing image output directory}
bundle="$output_dir/update.tar.zst"
[[ -f "$bundle" ]] || exit 0
signing_key=${ATLAS_UPDATE_SIGNING_KEY:-${SRCROOT}/keys/atlas-update.key}
[[ -f "$signing_key" ]] || {
    echo "Update signing key not found: $signing_key" >&2
    exit 1
}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

tar --zstd -xf "$bundle" -C "$work"
(
    cd "$work"
    sha256sum boot system > SHA256SUMS
    openssl dgst -sha256 -sign "$signing_key" \
        -out SHA256SUMS.sig SHA256SUMS
    files=(boot system SHA256SUMS SHA256SUMS.sig)
    tar --zstd -cf update.tar.zst.new "${files[@]}"
)
mv -f "$work/update.tar.zst.new" "$bundle"
