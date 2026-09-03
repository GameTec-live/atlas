#!/bin/sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH='' cd -- "$script_dir/.." && pwd)

# shellcheck disable=SC1091
. "$project_dir/versions.env"

state_dir=${ATLAS_RECOVERY_STATE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/atlas-recovery}
cache_dir=${ATLAS_RECOVERY_CACHE_DIR:-$state_dir/cache}
build_dir=${ATLAS_RECOVERY_BUILD_DIR:-$state_dir/build}
source_dir=$cache_dir/pi-gen-micro
work_dir=$build_dir/pi-gen-micro
configuration=atlas-recovery

mkdir -p "$cache_dir" "$build_dir"
policy_file=$(mktemp "${TMPDIR:-/tmp}/atlas-recovery-policy.XXXXXX")
trap 'rm -f "$policy_file"' EXIT INT TERM
install -m 0644 "$project_dir/apt-sequoia.config" "$policy_file"

if [ ! -d "$source_dir/.git" ]; then
    git clone "$PI_GEN_MICRO_REPOSITORY" "$source_dir"
fi

git -C "$source_dir" fetch --force origin "$PI_GEN_MICRO_REVISION"
git -C "$source_dir" checkout --detach "$PI_GEN_MICRO_REVISION"
git -C "$source_dir" restore --source "$PI_GEN_MICRO_REVISION" --worktree -- .
test "$(git -C "$source_dir" rev-parse HEAD)" = "$PI_GEN_MICRO_REVISION"

# pi-gen-micro intentionally discovers configurations relative to its own
# checkout. Synchronise only this project's out-of-tree configuration into the
# pinned checkout; no upstream source is patched.
mkdir -p "$source_dir/configurations/$configuration"
rsync -a --delete \
    "$project_dir/configurations/$configuration/" \
    "$source_dir/configurations/$configuration/"

mkdir -p "$work_dir"
cd "$work_dir"
SEQUOIA_CRYPTO_POLICY=$policy_file \
    "$source_dir/pi-gen-micro-sysroot" run "$configuration" pi5

test -f "$work_dir/out_image/config.txt"
test -f "$work_dir/out_image/cmdline.txt"
test -f "$work_dir/out_image/rootfs.cpio.zst"
test -f "$work_dir/out_image/zImage"

root=$work_dir/build
for executable in \
    usr/local/sbin/atlas-recover \
    usr/bin/fastboot \
    usr/bin/fastbootd \
    usr/bin/jq \
    usr/bin/zstd; do
    test -x "$root/$executable" || {
        echo "Recovery executable missing from built root: $executable" >&2
        exit 1
    }
done

test -x "$root/bin/busybox"
for applet in blkid blockdev dd find sha256sum stat tar timeout; do
    test "$(readlink "$root/usr/bin/$applet")" = /bin/busybox || {
        echo "BusyBox applet link missing from built root: $applet" >&2
        exit 1
    }
done
test "$(readlink "$root/init")" = /bin/busybox
test -x "$root/etc/init.d/rcS"
test -e "$root/usr/lib/aarch64-linux-gnu/libsystemd.so.0"
test -e "$root/usr/lib/aarch64-linux-gnu/libudev.so.1"
test ! -d "$root/usr/lib/systemd"
test ! -d "$root/usr/lib/udev"
test ! -e "$root/usr/bin/systemctl"
test ! -e "$root/usr/bin/udevadm"
test ! -e "$root/usr/lib/modules-load.d/fastbootd.conf"

printf '%s\n' "$work_dir/out_image"
