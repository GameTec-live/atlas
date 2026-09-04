# Atlas Raspberry Pi image

This directory is an out-of-tree `rpi-image-gen` source tree. In the Atlas
repository build it also compiles the sibling `apps/osManagementAPI` module. It
builds a minimal Raspberry Pi 5 appliance with an immutable A/B root, persistent
data, rootless Podman Quadlets, NetworkManager, optional Cloudflare Tunnel,
Tailscale and SSH, text branding, and an on-demand Cage/Chromium web kiosk.

Detailed design, build, provisioning, operation and troubleshooting guidance is
in [the documentation index](docs/README.md). Start there before flashing,
provisioning or updating a device.

## Build and releases

`.github/workflows/os.yml` is the canonical build. Pull requests validate the
configuration and Quadlets. Pushes to `main`, manual runs and `v*` tags build on
a native ARM64 GitHub runner using an immutable `rpi-image-gen` revision. Full
builds publish:

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
# Go 1.26+ is also required to build apps/osManagementAPI for ARM64.
./os/ci/validate.sh ./os ./rpi-image-gen
ATLAS_UPDATE_SIGNING_KEY="$PWD/os/keys/atlas-update.key" \
  ./rpi-image-gen/rpi-image-gen build -S "$PWD/os" -c atlas.yaml
```

The preloaded OCI archive is imported by `atlas-container-init.service` before
any Quadlet starts and then deleted. First boot therefore needs no network.
Quadlets use `Pull=missing`; `atlas-container-init.service` starts the rootless
`podman-auto-update.timer`, which checks the same mutable tags on later online
boots.

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

Cloudflare Tunnel and Tailscale are optional rootless Quadlets. They remain
stopped until provisioned through the management API, use outbound connections
only, drop all Linux capabilities and share the host network solely to proxy
the local Atlas HTTPS listener.

## SSH

SSH is disabled by default. Its state and host keys persist across A/B updates.

```sh
sudo atlas-ssh enable
sudo atlas-ssh disable
sudo atlas-ssh status
```

The authenticated management API and `atlas-sys ssh` commands delegate to the
same controller, so local and API-driven changes share one persistent policy.

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

Before the API starts, Atlas generates `BETTER_AUTH_URL` from the device's first
current physical or VPN interface address, falling back to
`https://atlas.local` when no such address exists. It also generates
`BETTER_AUTH_TRUSTED_ORIGINS` from `https://atlas.local`, HTTPS loopback origins
(`127.0.0.1`, `[::1]`, and `localhost`), all current physical and VPN interface
addresses, and this persistent management allowlist:

```text
/home/atlas-containers/.config/atlas/trusted-origins
```

The allowlist accepts one HTTPS origin (scheme and authority, without a path)
per line; blank lines and lines starting with `#` are ignored. NetworkManager
regenerates the environment and restarts the API after DHCP address changes.
The management service may update the same file for externally configured
domains, then run `atlas-auth-origins` as the `atlas-containers` user and
restart `atlas-api.service`.

## OS management API

The root service listens only on `/run/atlas-management/api.sock`. The API
container receives that socket plus a per-device bearer-token file; no other
workload receives either. Updates are streamed over this socket rather than
downloaded by the root daemon. Trial boots are committed after all eight
containers remain healthy for five minutes. Power, NetworkManager,
trusted-origin, Cloudflare Tunnel, Tailscale and factory-reset operations use
the same narrow API. Timezone changes also synchronize PostgreSQL's default
and log timezones. See the
[management API contract](../apps/osManagementAPI/README.md).

Host administrators can use the authenticated Unix-socket client directly:

```sh
sudo atlas-sys status
sudo atlas-sys update apply /path/to/atlas-rpi5-update.tar.zst
sudo atlas-sys timezone set Europe/Vienna
sudo atlas-sys cloudflare-tunnel provision /path/to/cloudflare-token
sudo atlas-sys tailscale provision /path/to/tailscale-auth-key atlas-1
sudo atlas-sys factory-reset
```

Factory reset asks for the exact confirmation `RESET` before scheduling the
wipe and reboot.

## Device policy

- Serial console and `serial-getty@serial0` are enabled.
- PCIe Gen 3 is enabled only for Pi 5; remove the setting if the uncertified
  link is unstable.
- A one-time service schedules EEPROM `BOOT_ORDER=0xf164`: USB mass storage,
  then NVMe, then SD. The previous EEPROM configuration is backed up under
  `/persistent/atlas/system/`.
- No secure-boot key or OTP security policy is applied by the image.
