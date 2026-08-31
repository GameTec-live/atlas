# Atlas Raspberry Pi image

This directory is an independent, out-of-tree `rpi-image-gen` source tree. It
builds a minimal Raspberry Pi 5 appliance with an immutable A/B root, persistent
data, rootless Podman Quadlets, NetworkManager, optional SSH and text branding.

## Build and releases

`.github/workflows/os.yml` is the canonical build. Pull requests validate the
configuration and Quadlets. Manual runs and `v*` tags build on a native ARM64
GitHub runner using an immutable `rpi-image-gen` revision. Full builds publish:

- `atlas-rpi5-<version>-provisioning.tar.zst` - encrypted IDP provisioning
  archive for `rpi-sb-provisioner`.
- `atlas-rpi5-<version>-development.img.zst` - unencrypted whole-disk image for
  direct development SD-card flashing.
- `atlas-rpi5-<version>-update.tar.zst` - signed A/B update bundle.
- `SHA256SUMS` - hashes for those three files.

Every full run uploads an Actions artifact. A `v*` tag also publishes the files
on the matching GitHub Release. Run the workflow manually only after the
container-image workflows have published the desired `latest` tags.

For a local Linux build, clone the pinned upstream version and use this folder
as its external source tree:

```sh
git clone https://github.com/raspberrypi/rpi-image-gen.git
git -C rpi-image-gen checkout 33e98d9aca74b83002ab01ff760ae5d4fa8e99a5
sudo ./rpi-image-gen/install_deps.sh
sudo apt-get install podman ripgrep shellcheck zstd
chmod +x os/pre-build.sh os/post-image.sh os/ci/*.sh
./os/ci/validate.sh ./os ./rpi-image-gen
ATLAS_UPDATE_SIGNING_KEY="$PWD/os/keys/atlas-update.key" \
  ./rpi-image-gen/rpi-image-gen build -S "$PWD/os" -c atlas.yaml
```

The preloaded OCI archive is imported by `atlas-container-init.service` before
any Quadlet starts and then deleted. First boot therefore needs no network.
Quadlets use `Pull=missing`; the rootless `podman-auto-update.timer` checks the
same mutable tags on later online boots.

The 8 GiB persistent filesystem is a minimum needed for the compressed archive
and imported image store to coexist on first boot. IDP provisioning expands the
encrypted persistent partition to consume all remaining target storage.

## Provisioning and encryption

`image.pmap` is `crypt`. The generated IDP archive is the production artifact:
Raspberry Pi provisioning creates a device-sized LUKS2 container holding both
system slots and persistent data. Firmware-readable boot partitions remain
unencrypted.

The whole-disk `.img` is an unencrypted development image. Flashing it directly
does not apply the provisioning map or expand data to the physical device.

Secure boot is deliberately not provisioned. End users may choose secure boot
with `rpi-sb-provisioner`, accepting its OTP changes and the need to sign future
boot updates with their customer key.

## Containers

All containers run from user Quadlets as the locked `atlas-containers` account.
The normal `atlas.network` preserves Compose DNS names. Geodata API and the
reloader additionally share the internal `geodata-control.network`.

Only the web container publishes host ports 80 and 443. The rootless Podman API
socket is mounted only into the reloader. The reloader can consequently control
all containers owned by this account if compromised, although its application
API filters operations to the labeled router and geocoder consumers.

## SSH

SSH is disabled by default. Its state and host keys persist across A/B updates.

```sh
sudo atlas-ssh enable
sudo atlas-ssh disable
sudo atlas-ssh status
```

Only `atlas` may log in. Its initial password is `atlas`, and both password and
public-key authentication are supported. Replace that password after deployment.

## A/B updates

The build emits `atlas-rpi5-update.tar.zst`. Install it into the inactive slot:

```sh
sudo atlas-ab-update install /path/to/atlas-rpi5-update.tar.zst --reboot
sudo atlas-ab-update commit
```

Builds embed the tracked public-project update key and sign with its tracked
private half. This detects damage but cannot authenticate the publisher because
the private key is intentionally public. Customers needing trusted updates
should override both keys as described in `keys/README.md`.

The candidate uses Raspberry Pi tryboot. Rebooting it without `commit` returns
to the previous slot. `rollback` requests that fallback explicitly.

Container auto-updates are intentionally independent from OS A/B updates and
are not rolled back by changing the root slot.

## Authentication origins

`https://atlas.local` remains Better Auth's canonical URL. Before the API
starts, Atlas generates `BETTER_AUTH_TRUSTED_ORIGINS` from that name, HTTPS
loopback origins (`127.0.0.1`, `[::1]`, and `localhost`), the device's current
physical and VPN interface addresses, and this persistent management allowlist:

```text
/home/atlas-containers/.config/atlas/trusted-origins
```

The allowlist accepts one HTTPS origin (scheme and authority, without a path)
per line; blank lines and lines starting with `#` are ignored. NetworkManager
regenerates the environment and restarts the API after DHCP address changes.
The management service may update the same file for externally configured
domains, then run `atlas-auth-origins` as the `atlas-containers` user and
restart `atlas-api.service`.

## Device policy

- Serial console and `serial-getty@serial0` are enabled.
- PCIe Gen 3 is enabled only for Pi 5; remove the setting if the uncertified
  link is unstable.
- A one-time service schedules EEPROM `BOOT_ORDER=0xf164`: USB mass storage,
  then NVMe, then SD. The previous EEPROM configuration is backed up under
  `/persistent/atlas/system/`.
- No secure-boot key or OTP security policy is applied by the image.
