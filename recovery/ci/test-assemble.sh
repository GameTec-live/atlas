#!/bin/sh
set -eu

project_dir=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)

for command in mkfs.vfat mcopy mdir mtype sfdisk truncate unzip zip zstd; do
    command -v "$command" >/dev/null 2>&1 || {
        echo "Skipping assembler test; missing $command"
        exit 0
    }
done

temporary=$(mktemp -d "${TMPDIR:-/tmp}/atlas-recovery-test.XXXXXX")
trap 'rm -rf "$temporary"' EXIT INT TERM

mkdir -p "$temporary/boot"
touch \
    "$temporary/boot/config.txt" \
    "$temporary/boot/cmdline.txt" \
    "$temporary/boot/rootfs.cpio.zst" \
    "$temporary/boot/zImage"
truncate -s 1M "$temporary/development.img"
zstd -q "$temporary/development.img" -o "$temporary/development.img.zst"

RECOVERY_IMAGE_SIZE_MIB=1024 \
    "$project_dir/scripts/assemble-image.sh" \
    "$temporary/boot" \
    "$temporary/development.img.zst" \
    "$temporary/recovery.img" >/dev/null

sfdisk -d "$temporary/recovery.img" | \
    grep -q 'start=[[:space:]]*2048, size=[[:space:]]*2097152, type=c, bootable'
mdir -i "$temporary/recovery.img@@1048576" :: | grep -q 'ATLASRECOV'
mdir -i "$temporary/recovery.img@@1048576" :: | grep -qi 'development.img.zst'
mtype -i "$temporary/recovery.img@@1048576" ::SHA256SUMS | \
    grep -q '  development.img.zst$'
unzip -Z1 "$temporary/recovery.zip" | grep -qx '.atlas-recovery-media'
unzip -Z1 "$temporary/recovery.zip" | grep -qx 'development.img.zst'
unzip -p "$temporary/recovery.zip" SHA256SUMS | \
    grep -q '  development.img.zst$'
if unzip -Z1 "$temporary/recovery.zip" | grep -q '^recovery\.img'; then
    echo "File-only recovery ZIP unexpectedly contains a disk image" >&2
    exit 1
fi
(cd "$temporary" && sha256sum --check SHA256SUMS.recovery >/dev/null)

echo "Atlas recovery image assembler test passed."
