#!/bin/sh
set -eu

usage() {
    echo "Usage: scripts/set-artifact.sh MOUNTED_RECOVERY_USB ARTIFACT" >&2
    exit 2
}

[ "$#" -eq 2 ] || usage

mount_dir=$(realpath "$1")
artifact=$(realpath "$2")
artifact_name=$(basename "$artifact")

[ -d "$mount_dir" ] || { echo "Mount point not found: $mount_dir" >&2; exit 1; }
[ -f "$artifact" ] || { echo "Artifact not found: $artifact" >&2; exit 1; }
[ -f "$mount_dir/.atlas-recovery-media" ] || {
    echo "Refusing to modify a filesystem without .atlas-recovery-media" >&2
    exit 1
}

case "$artifact_name" in
    *.img.zst|*provision*.tar.zst) ;;
    *) echo "Unsupported artifact name: $artifact_name" >&2; usage ;;
esac

artifact_size=$(stat -c %s "$artifact")
[ "$artifact_size" -lt 4294967295 ] || {
    echo "FAT32 cannot store artifacts of 4 GiB or larger" >&2
    exit 1
}

probe=$mount_dir/.atlas-recovery-write-test
if ! : > "$probe"; then
    echo "Recovery USB is not writable: $mount_dir" >&2
    exit 1
fi
rm -f "$probe"

# It is safe for an interrupted replacement to leave no payload: the recovery
# boot validates payload count and integrity before it touches target storage.
find "$mount_dir" -maxdepth 1 -type f \
    \( -name '*.img.zst' -o -name '*provision*.tar.zst' \) \
    -exec rm -f -- {} +
rm -f "$mount_dir/SHA256SUMS"
cp "$artifact" "$mount_dir/$artifact_name"
(cd "$mount_dir" && sha256sum "$artifact_name" > SHA256SUMS)
sync "$mount_dir"

printf 'Installed %s on %s\n' "$artifact_name" "$mount_dir"

