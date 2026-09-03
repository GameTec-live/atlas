#!/bin/sh
set -eu

usage() {
    cat >&2 <<'EOF'
Usage: scripts/assemble-image.sh BOOT_FILES ARTIFACT [OUTPUT]

ARTIFACT must be one of:
  *.img.zst                 unencrypted whole-disk restore
  *provision*.tar.zst       encrypted rpi-image-gen IDP provisioning archive

Environment:
  RECOVERY_IMAGE_SIZE_MIB   FAT32 partition size (default: 6144)

Outputs:
  OUTPUT                    sparse disk image
  OUTPUT.zst                compressed disk image
  OUTPUT-without-.img.zip   FAT32 root files without a disk-image container
EOF
    exit 2
}

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
    usage
fi

boot_files=$(realpath "$1")
artifact=$(realpath "$2")
output=${3:-atlas-recovery-usb.img}
image_size_mib=${RECOVERY_IMAGE_SIZE_MIB:-6144}

[ -d "$boot_files" ] || { echo "Boot file directory not found: $boot_files" >&2; exit 1; }
[ -f "$artifact" ] || { echo "Artifact not found: $artifact" >&2; exit 1; }

artifact_name=$(basename "$artifact")
case "$artifact_name" in
    *.img.zst|*provision*.tar.zst) ;;
    *) echo "Unsupported artifact name: $artifact_name" >&2; usage ;;
esac

case "$image_size_mib" in
    ''|*[!0-9]*) echo "RECOVERY_IMAGE_SIZE_MIB must be an integer" >&2; exit 1 ;;
esac
[ "$image_size_mib" -ge 1024 ] || {
    echo "RECOVERY_IMAGE_SIZE_MIB must be at least 1024" >&2
    exit 1
}

artifact_size=$(stat -c %s "$artifact")
[ "$artifact_size" -lt 4294967295 ] || {
    echo "FAT32 cannot store $artifact_name because it is 4 GiB or larger" >&2
    exit 1
}

for command in mkfs.vfat mcopy sfdisk sha256sum truncate zip zstd; do
    command -v "$command" >/dev/null 2>&1 || {
        echo "Missing host command: $command" >&2
        exit 1
    }
done

case "$output" in
    /*) ;;
    *) output=$PWD/$output ;;
esac

output_dir=$(dirname "$output")
mkdir -p "$output_dir"
temporary=$(mktemp -d "$output_dir/.atlas-recovery.XXXXXX")
trap 'rm -rf "$temporary"' EXIT INT TERM

fat_image=$temporary/recovery.fat
disk_image=$temporary/recovery.img
files_archive=$temporary/recovery.zip
stage=$temporary/stage
mkdir -p "$stage"

cp -a "$boot_files/." "$stage/"
rm -f "$stage"/*.img "$stage"/*bootfiles.bin
cp "$artifact" "$stage/$artifact_name"
touch "$stage/.atlas-recovery-media"
cat > "$stage/recovery.conf" <<'EOF'
# For a raw image, use sd or nvme when both devices are installed.
# An IDP provisioning archive declares and enforces its own storage type.
TARGET=auto
EOF
(cd "$stage" && sha256sum "$artifact_name" > SHA256SUMS)

stage_bytes=$(du -sb "$stage" | awk '{print $1}')
capacity_bytes=$((image_size_mib * 1024 * 1024))
reserve_bytes=$((256 * 1024 * 1024))
[ "$stage_bytes" -lt $((capacity_bytes - reserve_bytes)) ] || {
    echo "Recovery files do not fit with 256 MiB free space; increase RECOVERY_IMAGE_SIZE_MIB" >&2
    exit 1
}

# This archive is the same FAT32 root without a disk-image container. Storing
# already-compressed payloads avoids wasting build time for negligible savings.
(cd "$stage" && zip -q -0 -r "$files_archive" .)

truncate -s "${image_size_mib}M" "$fat_image"
mkfs.vfat -F 32 -n ATLASRECOV "$fat_image" >/dev/null
mcopy -s -i "$fat_image" "$stage"/* ::
mcopy -i "$fat_image" "$stage/.atlas-recovery-media" ::

# A conventional MBR plus a single LBA FAT32 partition is accepted by the Pi
# EEPROM USB mass-storage boot path. The one-MiB gap keeps it broadly flashable.
disk_size_mib=$((image_size_mib + 1))
truncate -s "${disk_size_mib}M" "$disk_image"
printf 'label: dos\nunit: sectors\n\nstart=2048, size=%s, type=c, bootable\n' \
    "$((image_size_mib * 2048))" | sfdisk "$disk_image" >/dev/null
dd if="$fat_image" of="$disk_image" bs=1M seek=1 conv=notrunc,sparse status=none

mv "$disk_image" "$output"
zstd -T0 -19 -f "$output" -o "$output.zst"
case "$output" in
    *.img) zip_output=${output%.img}.zip ;;
    *) zip_output=$output.zip ;;
esac
mv "$files_archive" "$zip_output"
(cd "$output_dir" && \
    sha256sum \
        "$(basename "$output")" \
        "$(basename "$output").zst" \
        "$(basename "$zip_output")" \
        > SHA256SUMS.recovery)

printf 'Created:\n  %s\n  %s.zst\n  %s\n' \
    "$output" "$output" "$zip_output"
