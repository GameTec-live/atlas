# Implementation reference

## Top-level files

| File | Responsibility |
| --- | --- |
| `config/atlas.yaml` | Composes the Pi 5 device, image layout, Atlas layers, public update key and secure-boot policy |
| `images.txt` | Authoritative list of OCI tags captured for offline first boot |
| `pre-build.sh` | Builds the ARM64 management binary, pulls ARM64 images and creates the multi-image archive |
| `post-image.sh` | Adds hashes/signature to the generated A/B bundle |
| `ci/validate.sh` | Fast source/configuration/Quadlet validation |
| `ci/audit-build.sh` | Post-build filesystem, partition, OCI and signature audit |
| `keys/` | Public-project default A/B signing keypair and key instructions |

The GitHub workflow is `.github/workflows/os.yml` at the repository root.

## Layer file pattern

Each `layer/atlas-*.yaml` contains rpi-image-gen metadata and the smallest set
of package/customization hooks for one concern. A sibling
`<layer>.rootfs-overlay` is copied into the target by rpi-image-gen.

When adding a layer or changing ordering:

1. give it a unique `X-Env-Layer-Name` and Atlas category;
2. declare only real provider/layer dependencies;
3. add it to `config/atlas.yaml` in dependency order;
4. keep executable mode changes in customize hooks because source checkouts can
   lose executable bits;
5. run metadata lint, composed config validation and the full Quadlet generator;
6. extend the build audit for target-only invariants.

## Feature-to-source map

### Base and identity

| Feature | Source |
| --- | --- |
| Debian/Raspberry Pi base | `layer/atlas-base.yaml` |
| Atlas `os-release`, issue and MOTD branding | `layer/atlas-branding.yaml`, `layer/atlas-branding.rootfs-overlay/etc/*` |
| Primary user/password/sudo and image layout | `config/atlas.yaml` |

### Networking

| Feature | Source |
| --- | --- |
| NetworkManager packages and online target | `layer/atlas-networking.yaml` |
| systemd-resolved and IPv6 privacy config | `layer/atlas-networking.rootfs-overlay/etc/NetworkManager/conf.d/10-atlas.conf` |
| Dynamic auth refresh on network events | `layer/atlas-podman.rootfs-overlay/etc/NetworkManager/dispatcher.d/90-atlas-auth-origins` |
| Slot-shared NetworkManager profiles and secrets | `layer/atlas-networking.rootfs-overlay/etc/rpi-image-gen/slot-shared.d/atlas-networkmanager.conf` |
| Rootless low-port sysctl | `layer/atlas-podman.rootfs-overlay/etc/sysctl.d/90-atlas-rootless-ports.conf` |

### Rootless runtime and workloads

| Feature | Source |
| --- | --- |
| UID 2000, subordinate IDs and linger | `layer/atlas-users.yaml` |
| Podman packages, timer/socket enablement and embedded archive | `layer/atlas-podman.yaml` |
| Secret generation and offline import | `layer/atlas-podman.rootfs-overlay/usr/local/libexec/atlas-container-init` |
| Better Auth URL/origin generation | `layer/atlas-podman.rootfs-overlay/usr/local/libexec/atlas-auth-origins` |
| Database/API/web and optional remote-access Quadlets | `layer/atlas-containers-core.rootfs-overlay/etc/containers/systemd/users/2000/` |
| Tailscale HTTPS serve policy | `layer/atlas-containers-core.rootfs-overlay/usr/share/atlas/tailscale-serve.json` |
| Geodata Quadlets and internal control network | `layer/atlas-containers-geodata.rootfs-overlay/etc/containers/systemd/users/2000/` |

### Updates and host policy

| Feature | Source |
| --- | --- |
| A/B installer/state machine | `layer/atlas-ab-update.rootfs-overlay/usr/local/sbin/atlas-ab-update` |
| Embedded update public key | `layer/atlas-ab-update.yaml`, `config/atlas.yaml` |
| Serial/PCIe policy | `layer/atlas-device-policy.yaml` |
| EEPROM boot-order service/tool | `layer/atlas-device-policy.rootfs-overlay/usr/lib/systemd/system/atlas-usb-boot-order.service`, `usr/local/sbin/atlas-usb-boot-order` |
| Encryption diagnostic | `layer/atlas-device-policy.rootfs-overlay/usr/local/sbin/atlas-security-status` |
| Persistent SSH controller and hardening | `layer/atlas-ssh.rootfs-overlay/` |
| Privileged management API and factory-reset boot unit | `apps/osManagementAPI/`, `layer/atlas-management.rootfs-overlay/` |
| Root-only management CLI | `layer/atlas-management.rootfs-overlay/usr/local/sbin/atlas-sys` |
| On-demand TTY launcher, Cage/Chromium kiosk and retry extension | `layer/atlas-kiosk.rootfs-overlay/` |

## Invariants to preserve

### Image and update

- Target remains Pi 5/ARM64 unless a new device configuration is added
  explicitly.
- Production `pmap` remains `crypt`; development raw image remains clearly
  labeled unencrypted.
- Persistent partition has `expand-to-fit` in the generated provisioning map.
- Both boot and system are A/B components.
- Update bundle member allowlist stays narrow.
- System is written before boot, and tryboot metadata is written last.
- Tryboot uses `reboot '0 tryboot'` exactly.
- Customer public/private update keys must be a matching pair.

### Rootless Podman

- Quadlets are user units under `etc/containers/systemd/users/2000`.
- UID/GID 2000, subuid/subgid and linger remain aligned.
- All images in `images.txt` have a Quadlet and vice versa.
- `Pull=missing` preserves offline first boot.
- Only web publishes host ports.
- Optional Cloudflare and Tailscale connectors stay rootless, capability-free
  and condition-gated by their mode-0600 credential files.
- Only reloader mounts the Podman socket.
- Router/geocoder retain exact consumer labels.
- Reloader keeps the image's exec-form health check.
- The shared `atlas.network` aliases stay compatible with current environment
  URLs and Caddy behavior.

### Networking and auth

- `network-online.target` is pulled into normal boot.
- systemd-resolved owns `/etc/resolv.conf` after all build hooks finish.
- Better Auth receives both required generated variables before API start.
- Interface filtering does not trust Podman/virtual bridge addresses.
- Managed origins remain persistent, HTTPS-only and path-free.
- Address-change regeneration restarts the API through the UID 2000 user bus.
- NetworkManager profiles remain shared across A/B slots and are erased by a
  factory reset.

### Management boundary

- The manager listens only on `/run/atlas-management/api.sock`.
- Socket access requires the retained UID 2000 group and every request also
  requires the per-device bearer token.
- Update data enters only as a streamed upload; the root service has no update
  downloader.
- A candidate is committed only after all eight workloads stay healthy for
  five continuous minutes.
- Factory reset preserves the encryption container and OCI image layers, but
  removes application data, credentials, host policy and network profiles.
- `atlas-sys` remains a narrow Unix-socket client and keeps destructive reset
  confirmation in front of the API call.
- Management API SSH changes delegate to `atlas-ssh`; they do not introduce a
  second persistent SSH policy.

### Security and persistence

- SSH is disabled by default and only `atlas` is allowed.
- Cage and Chromium run as the locked `atlas-kiosk` system account, never as
  the administrative `atlas` account.
- Cage and Chromium do not start at boot; the TTY launcher waits for an empty
  ENTER keypress first.
- `atlas-kiosk` has no supplementary groups or persistent home, can reach only
  loopback addresses, and retains Chromium's user-namespace/process sandbox
  without allowing a setuid bootstrap.
- The kiosk targets only `https://localhost/` and relaxes certificate checks
  only for localhost.
- SSH state is written atomically under `/persistent/atlas/system`.
- Rootless secrets stay mode 0600 and are not regenerated on every boot.
- Root and persistent ownership checks remain in the artifact audit.
- Secure boot/OTP policy remains opt-in and separate from ordinary image boot.
- OS rollback documentation never promises application-data rollback.

## Change checklist

Before merging an OS change:

1. update comments and the relevant document in `docs/`;
2. run `ci/validate.sh` against the pinned generator;
3. perform a full build on ARM64 or the supported WSL path;
4. run `ci/audit-build.sh` (using `podman unshare` only when shifted local
   ownership requires it);
5. record release checksums;
6. install into the inactive slot without committing first;
7. verify host services, all eight core Quadlets, any configured optional
   connectors, UI/map/auth, reloader socket and labels;
8. commit and perform an ordinary reboot;
9. verify the active slot and endpoints again;
10. remove temporary update bundles from the target.

If a change affects provisioning, test both the IDP provisioning archive and
the unencrypted development image. Testing only an A/B update cannot validate
partition creation, data expansion, first-boot OCI import or per-device
encryption.
