# Atlas OS documentation

Atlas OS is an independent, out-of-tree configuration for
[`rpi-image-gen`](https://github.com/raspberrypi/rpi-image-gen). It produces a
minimal Raspberry Pi 5 appliance for the Atlas container stack.

## Start here

| Goal | Document |
| --- | --- |
| Understand the disk, boot, user and service design | [Architecture](architecture.md) |
| Build locally or understand GitHub Actions | [Build and release](build-and-release.md) |
| Pick the correct first-install artifact and provision a device | [Provisioning and security](provisioning-and-security.md) |
| Understand or operate the rootless containers and networking | [Containers and networking](containers-and-networking.md) |
| Install, validate, commit or recover an A/B update | [A/B updates](updates.md) |
| Operate the privileged Unix-socket API or factory reset | [Operations and troubleshooting](operations-and-troubleshooting.md#management-service) |
| Bring up a device, change SSH/origins, or diagnose failures | [Operations and troubleshooting](operations-and-troubleshooting.md) |
| Find the source file responsible for a feature | [Implementation reference](implementation-reference.md) |

## Artifact cheat sheet

The three release artifacts are not interchangeable:

| Artifact | Intended use | Important limitation |
| --- | --- | --- |
| `atlas-rpi5-<version>-provisioning.tar.zst` | Production provisioning with `rpi-sb-provisioner` | It is an IDP archive, not a raw disk image and must not be passed to `dd` or an SD-card imager. |
| `atlas-rpi5-<version>-development.img.zst` | Direct development SD-card flashing after decompression | It is deliberately unencrypted and does not apply the production provisioning map. |
| `atlas-rpi5-<version>-update.tar.zst` | Updating an already installed Atlas OS through `atlas-ab-update` | It contains only boot and system payloads. It is not bootable and does not replace persistent data or the rootless container store. |

Always verify the artifact against the release `SHA256SUMS` before using it.

Each OS build also publishes encrypted and unencrypted recovery media as
`.img.zst` disk images and file-only ZIPs. The ZIP contents can be extracted to
an empty FAT32 drive labelled `ATLASRECOV`. See the
[recovery documentation](../../recovery/README.md) for the destructive boot
behavior and complete instructions.

## Non-obvious invariants

- Only `atlas-web` publishes host ports. The optional outbound Cloudflare and
  Tailscale connectors use host networking only to proxy that local listener;
  every other application is reachable only through Podman networking.
- All application containers intentionally share one locked rootless account
  and the `atlas.network` bridge. This keeps the Compose service names and
  Caddy routing unchanged, but it makes that account one security boundary.
- The geodata reloader is the only container with the Podman API socket. That
  socket can control every container owned by `atlas-containers`; the router
  and geocoder labels are an application-level restriction, not a kernel-level
  boundary.
- The API container is the only workload with the separate OS-management
  socket and token. The host manager has no TCP listener and no downloader.
- The current API image requires `BETTER_AUTH_URL`. Atlas therefore generates
  it from the current interface addresses; simply deleting the variable makes
  the API fail its environment validation and restart repeatedly.
- `https://atlas.local` is always a trusted origin, but trusting an origin does
  not make its name resolve. Resolution still depends on the client and the
  network's DNS/mDNS policy.
- OS rollback does not roll back `/persistent`, Podman volumes, container image
  updates, SSH state or other device state.
- Secure boot is deliberately not enabled and no OTP is burned by this image.
  Production encryption and optional secure-boot provisioning are separate
  operations.
- The tracked update private key is public by design. Its signatures detect
  accidental damage; they do not prove that an update came from a trusted
  publisher. Customer deployments need their own keypair.

## Supported target

The configuration targets Raspberry Pi 5 and Debian 13 (Trixie). It enables a
Pi 5-specific PCIe Gen 3 setting and schedules a Pi 5 EEPROM boot-order change.
Do not assume the resulting image is suitable for older Raspberry Pi models.
