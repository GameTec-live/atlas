#!/bin/sh
set -eu

usage() {
    echo "Usage: scripts/build-fastbootd.sh PI_GEN_MICRO_SOURCE BUILD_DIR" >&2
    exit 2
}

[ "$#" -eq 2 ] || usage

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH='' cd -- "$script_dir/.." && pwd)

# shellcheck disable=SC1091
. "$project_dir/versions.env"

pi_gen_micro=$(realpath "$1")
build_dir=$2
mkdir -p "$build_dir"
build_dir=$(realpath "$build_dir")
source_dir=$build_dir/rpi-fastbootd
patch_file=$project_dir/patches/rpi-fastbootd-persistent-tcp.patch

[ -x "$pi_gen_micro/pi-gen-micro-sysroot" ] || {
    echo "pi-gen-micro source is missing its sysroot helper: $pi_gen_micro" >&2
    exit 1
}

if [ ! -d "$source_dir/.git" ]; then
    git clone "$RPI_FASTBOOTD_REPOSITORY" "$source_dir"
fi

git -C "$source_dir" fetch --force origin "$RPI_FASTBOOTD_REVISION"
git -C "$source_dir" checkout --detach "$RPI_FASTBOOTD_REVISION"
git -C "$source_dir" restore --source "$RPI_FASTBOOTD_REVISION" --worktree -- .
git -C "$source_dir" submodule update --init --recursive --force
git -C "$source_dir" apply "$patch_file"

rm -f "$build_dir"/rpi-fastbootd_*.deb
(
    cd "$build_dir"
    "$pi_gen_micro/pi-gen-micro-sysroot" create
    # This script is evaluated inside the build sysroot.
    # shellcheck disable=SC2016
    "$pi_gen_micro/pi-gen-micro-sysroot" shell sh -c '
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get -o APT::Sandbox::User=root update
        apt-get -o APT::Sandbox::User=root install -y --no-install-recommends \
            android-libbase-dev android-libcutils-dev android-liblog-dev \
            build-essential cmake debhelper git libblockdeviceid-dev libcryptsetup-dev \
            libfdisk-dev libgpiod-dev libjsoncpp-dev librpifwcrypto-dev \
            libssl-dev libsystemd-dev liburing-dev pkg-config zlib1g-dev
        cd "$1"
        ./debian/gen-version.sh
        dpkg-buildpackage -b -uc -us -j"$(nproc)"
    ' _ "$source_dir"
)

package=$(find "$build_dir" -maxdepth 1 -type f \
    -name 'rpi-fastbootd_*_arm64.deb' -print -quit)
[ -n "$package" ] || {
    echo "Patched rpi-fastbootd package was not produced" >&2
    exit 1
}

rm -f "$pi_gen_micro/internal/packages"/rpi-fastbootd_*.deb
install -m 0644 "$package" "$pi_gen_micro/internal/packages/"
printf 'Installed patched package: %s\n' "$(basename "$package")"
