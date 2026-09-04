#!/bin/sh
set -eu

usage() {
    echo "Usage: scripts/build.sh ARTIFACT [OUTPUT.img]" >&2
    exit 2
}

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    usage
fi

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH='' cd -- "$script_dir/.." && pwd)
artifact=$(realpath "$1")
output=${2:-$project_dir/out/atlas-recovery-usb.img}

"$script_dir/build-boot.sh"
state_dir=${ATLAS_RECOVERY_STATE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/atlas-recovery}
build_dir=${ATLAS_RECOVERY_BUILD_DIR:-$state_dir/build}
boot_files=$build_dir/pi-gen-micro/out_image
"$script_dir/assemble-image.sh" "$boot_files" "$artifact" "$output"
