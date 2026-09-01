#!/bin/bash
set -euo pipefail

work_root=${1:?usage: audit-build.sh WORK_ROOT}
image_dir="$work_root/image-atlas-rpi5"
archive="$work_root/atlas-images.tar"
rootfs=$(find "$work_root" -mindepth 2 -maxdepth 2 -type d \
    -path "$work_root/chroot-*/filesystem" -print -quit)

[[ -n "$rootfs" && -d "$image_dir" && -f "$archive" ]]
[[ -x "$rootfs/usr/lib/systemd/systemd-resolved" ]]
[[ -x "$rootfs/usr/sbin/nft" ]]
[[ -x "$rootfs/usr/local/libexec/atlas-auth-origins" ]]
[[ -x "$rootfs/usr/local/libexec/atlas-management" ]]
[[ -x "$rootfs/usr/local/sbin/atlas-sys" ]]
[[ -f "$rootfs/usr/share/zoneinfo/Etc/UTC" ]]
[[ "$(readlink "$rootfs/etc/localtime")" = /persistent/atlas/system/localtime ]]
[[ "$(readlink "$rootfs/persistent/atlas/system/localtime")" = /usr/share/zoneinfo/Etc/UTC ]]
[[ -r "$rootfs/usr/share/atlas/tailscale-serve.json" ]]
grep -q -F 'Wants=podman-auto-update.timer' \
    "$rootfs/usr/lib/systemd/user/atlas-container-init.service"
file "$rootfs/usr/local/libexec/atlas-management" | grep -q 'ARM aarch64'
grep -q -F 'ConditionPathExists=/persistent/atlas/system/factory-reset-pending' \
    "$rootfs/usr/lib/systemd/system/atlas-factory-reset.service"
grep -q -F 'd /run/atlas-management 0750 root atlas-containers' \
    "$rootfs/usr/lib/tmpfiles.d/atlas-management.conf"
grep -q -F 'L /persistent/atlas/system/localtime - - - - /usr/share/zoneinfo/Etc/UTC' \
    "$rootfs/usr/lib/tmpfiles.d/atlas-management.conf"
[[ -x "$rootfs/etc/NetworkManager/dispatcher.d/90-atlas-auth-origins" ]]
if grep -q '^BETTER_AUTH_URL=' "$rootfs/usr/local/libexec/atlas-container-init"; then
    echo "built image generates a fixed Better Auth base URL" >&2
    exit 1
fi
[[ "$(readlink "$rootfs/etc/resolv.conf")" = /run/systemd/resolve/stub-resolv.conf ]]
[[ "$(readlink "$rootfs/etc/os-release")" = ../usr/lib/os-release ]]
grep -qx 'NAME="Atlas OS"' "$rootfs/usr/lib/os-release"
grep -qx 'ID=debian' "$rootfs/usr/lib/os-release"
grep -qx 'VARIANT_ID=atlas' "$rootfs/usr/lib/os-release"
grep -qx 'IMAGE_ID=atlas' "$rootfs/usr/lib/os-release"
grep -q '^IMAGE_VERSION=' "$rootfs/usr/lib/os-release"
[[ ! -e "$rootfs/etc/atlas-release" ]]
grep -q -F 'https://atlas.local' "$rootfs/usr/local/libexec/atlas-auth-origins"
grep -q -F 'https://127.0.0.1' "$rootfs/usr/local/libexec/atlas-auth-origins"
grep -q -F 'https://[::1]' "$rootfs/usr/local/libexec/atlas-auth-origins"
grep -q -F 'https://localhost' "$rootfs/usr/local/libexec/atlas-auth-origins"
grep -q -F "printf 'BETTER_AUTH_URL=%s\\n'" \
    "$rootfs/usr/local/libexec/atlas-auth-origins"
[[ -L "$rootfs/etc/systemd/system/multi-user.target.wants/network-online.target" ]]
[[ "$(stat -c %u:%g "$rootfs/persistent/home/atlas-containers/.config")" = 2000:2000 ]]
[[ "$(stat -c %u:%g "$rootfs/persistent/home/atlas-containers/.cache")" = 2000:2000 ]]
grep -q 'console=serial0,115200' "$rootfs/boot/firmware/cmdline.txt"
grep -q '^dtparam=pciex1_gen=3$' "$rootfs/boot/firmware/config.txt"
[[ -L "$rootfs/etc/systemd/system/getty.target.wants/serial-getty@serial0.service" ]]
[[ ! -e "$rootfs/etc/systemd/system/multi-user.target.wants/ssh.service" ]]
[[ ! -L "$rootfs/etc/systemd/system/multi-user.target.wants/ssh.service" ]]

python3 - "$image_dir/provisionmap.json" "$archive" <<'PY'
import json
import sys
import tarfile

pmap_path, archive_path = sys.argv[1:]
with open(pmap_path, encoding="utf-8") as source:
    pmap = json.load(source)

encrypted = next(item["encrypted"] for item in pmap if "encrypted" in item)
assert encrypted["expand-to-fit"] is True
assert encrypted["partitions"][0]["expand-to-fit"] is True

with tarfile.open(archive_path) as archive:
    manifest = json.load(archive.extractfile("manifest.json"))
    assert len(manifest) == 10
    expected = {
        "docker.io/library/postgres:18-alpine",
        "docker.io/cloudflare/cloudflared:latest",
        "docker.io/tailscale/tailscale:stable",
        "ghcr.io/gametec-live/atlas-router:latest",
        "ghcr.io/gametec-live/atlas-map:latest",
        "ghcr.io/gametec-live/geocoder-go:latest",
        "ghcr.io/gametec-live/atlas-geodata-api:latest",
        "ghcr.io/gametec-live/atlas-geodata-reloader:latest",
        "ghcr.io/gametec-live/atlas-api:latest",
        "ghcr.io/gametec-live/atlas-web:latest",
    }
    tags = set()
    for item in manifest:
        tags.update(item["RepoTags"])
        config = json.load(archive.extractfile(item["Config"]))
        assert config["architecture"] == "arm64"
    assert tags == expected
PY

update_dir=$(mktemp -d)
trap 'rm -rf "$update_dir"' EXIT
tar --zstd -xf "$image_dir/update.tar.zst" -C "$update_dir"
(cd "$update_dir" && sha256sum --check --strict SHA256SUMS)
openssl dgst -sha256 \
    -verify "$rootfs/etc/atlas/update-public.pem" \
    -signature "$update_dir/SHA256SUMS.sig" \
    "$update_dir/SHA256SUMS"

echo "Atlas build artifact audit passed."
