#!/bin/bash
set -euo pipefail

archive=${IGconf_atlasoci_archive:?missing Atlas OCI archive path}
image_list=${SRCROOT:?missing source root}/images.txt

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
