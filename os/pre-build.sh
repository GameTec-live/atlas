#!/bin/bash
set -euo pipefail

archive=${IGconf_atlasoci_archive:?missing Atlas OCI archive path}
management_binary=${IGconf_atlasmanagement_binary:?missing Atlas management binary path}
image_list=${SRCROOT:?missing source root}/images.txt
management_source=${SRCROOT}/../apps/osManagementAPI

[[ -f "$management_source/go.mod" ]] || {
    echo "Atlas management API source not found: $management_source" >&2
    exit 1
}
mkdir -p "$(dirname "$management_binary")"
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 \
    go -C "$management_source" build \
    -buildvcs=false -trimpath -ldflags='-s -w' \
    -o "$management_binary" .
echo "Built Atlas management API: $management_binary"

mapfile -t images < <(sed '/^[[:space:]]*#/d;/^[[:space:]]*$/d' "$image_list")
if [[ ${#images[@]} -eq 0 ]]; then
    echo "No Atlas OCI images configured" >&2
    exit 1
fi

mkdir -p "$(dirname "$archive")"
rm -f "$archive"

for image in "${images[@]}"; do
    podman pull --platform linux/arm64 "$image"
done

# docker-archive supports multiple tagged images and deduplicates shared blobs.
podman save --multi-image-archive --format docker-archive \
    --output "$archive" "${images[@]}"

archive_size=$(stat -c %s "$archive")
echo "Created offline ARM64 image archive: $archive ($archive_size bytes)"
