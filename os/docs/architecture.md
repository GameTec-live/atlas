# Architecture

## Design goals

Atlas OS keeps the immutable operating system separate from mutable appliance
state:

- two read-only EROFS system slots allow safe A/B updates;
- boot firmware has matching A/B boot payloads;
- one persistent data area holds application state and device policy;
- all application workloads run as rootless Podman containers;
- the only normal host ingress is Caddy on ports 80 and 443;
- an on-demand local Cage session presents that HTTPS UI full-screen in Chromium;
- SSH is an explicitly enabled maintenance path, not a default service.

The `os` directory is an external source tree and does not patch or vendor
`rpi-image-gen`; builds point the generator at this directory with `-S`. The
Atlas repository layout is required because its pre-build hook compiles the
sibling `apps/osManagementAPI` module into the image.

## Storage and boot layout

The `image-rota` layout provides these logical components:

| Component | Purpose | Mutable at runtime |
| --- | --- | --- |
| `bootconfig` | Raspberry Pi slot-selection metadata | Yes, narrowly, by update/boot tooling |
| `boot_a`, `boot_b` | Firmware, kernel and initramfs for each slot | Only when writing the inactive update slot |
| `system_a`, `system_b` | Read-only EROFS operating-system roots | Only when writing the inactive update slot |
| `persistent` | Device state, homes, Podman storage and volumes | Yes |

The configured minimums are a 256 MiB boot payload, 2 GiB per system slot and
8 GiB for persistent data. The large persistent minimum is intentional: first
boot temporarily needs both the compressed multi-image OCI archive and the
imported rootless image store.

Production provisioning uses the `crypt` provisioning map. The provisioner
creates a device-sized LUKS2 data container and expands the persistent
partition to consume the remaining target storage. The firmware-readable boot
partitions cannot be encrypted because Raspberry Pi firmware must read them
before Linux and the encrypted data path are available.

The development whole-disk image is different: it is unencrypted and retains
the build-time partition sizing. See
[Provisioning and security](provisioning-and-security.md).

## A/B boot model

`rpi-image-gen` supplies the Raspberry Pi slot mapper and tryboot integration.
Atlas writes an update only to the devices exposed as `other`:

1. verify bundle members, hashes and signature;
2. write the inactive system payload;
3. write the inactive boot payload;
4. sync both devices;
5. write the pending marker and tryboot configuration;
6. reboot with the exact firmware request `reboot '0 tryboot'`.

Writing tryboot metadata last prevents an interrupted installation from
selecting a partially written candidate. A trial boot is one-shot until
`atlas-ab-update commit` changes the normal slot selection. The detailed state
machine and recovery procedures are in [A/B updates](updates.md).

## Persistent state

Important persistent paths include:

| Path | Contents |
| --- | --- |
| `/persistent/atlas/system/` | A/B pending state, SSH enablement, USB boot-order marker and EEPROM backup |
| `/persistent/atlas/timezone/` | Root-owned timezone selection, readable by rootless Podman |
| `/home/atlas-containers/` | Rootless Podman storage, generated environment files and persistent user configuration |
| `/persistent/shared/{etc,var}/.../NetworkManager` | Slot-shared IP/Wi-Fi profiles and NetworkManager secret state |
| Podman named volumes | PostgreSQL, API, Caddy, map, router and shared geodata state |

Changing system slots does not revert any of these paths. This is a feature for
normal application data, but it also means a broken database migration,
container auto-update or configuration change can survive an OS rollback.

## Users and privilege boundaries

### `atlas`

- UID 1000;
- interactive administrative account;
- initial password `atlas`;
- member of the configured Raspberry Pi hardware groups;
- may use `sudo`, with password confirmation;
- the only account accepted by SSH.

The default password is a provisioning convenience, not a production secret.
Change it before exposing SSH.

### `atlas-kiosk`

- UID 1999 system account with a locked password and `/usr/sbin/nologin` shell;
- has no persistent home or state, supplementary groups, or sudo access;
- runs only a small TTY launcher until ENTER is pressed, then replaces it with
  the Cage compositor and Chromium;
- receives temporary access to the active DRM/input seat through logind;
- is limited by systemd to loopback networking, required display/input devices,
  and an ephemeral runtime directory.

Chromium keeps its process sandbox using unprivileged user namespaces; its
legacy setuid bootstrap is disabled. The kiosk service prevents privilege
gains, makes the host filesystem read-only, hides home directories and other
users' processes, and isolates temporary files, IPC, mounts and its keyring.
Because logind moves the graphical session into `user-1999.slice`, its user
slice repeats the service's loopback and device cgroup restrictions.

### `atlas-containers`

- UID/GID 2000;
- locked password and `/usr/sbin/nologin` shell;
- subordinate UID/GID range `100000:65536`;
- systemd linger enabled so its user manager runs without an interactive login;
- owns the rootless Podman engine, containers, images, volumes and API socket.

Container `User=` values such as 65532 or 10001 are mapped through the rootless
user namespace. They are not host root. Because every workload uses the same
Podman account, compromise of that account is compromise of the complete Atlas
container workload, even though the containers still have separate namespaces,
capability sets and filesystems.

## System and user service split

System services handle host-level policy:

- NetworkManager, systemd-resolved and network-online coordination;
- the on-demand Cage/Chromium launcher on the primary virtual terminal;
- persistent SSH state;
- one-time Raspberry Pi EEPROM boot-order policy;
- the authenticated Unix-socket management API and early-boot factory reset.

The lingering `atlas-containers` user manager handles:

- initial secret/configuration generation;
- first-boot OCI image import;
- all Quadlet-generated container and volume units;
- the rootless Podman socket;
- Podman registry auto-updates.

Quadlets depend on `atlas-container-init.service`. Networking also explicitly
pulls `network-online.target` into normal boots. Without that target symlink the
user manager can wait forever even though NetworkManager itself is running.

## Layer split

The configuration deliberately avoids one monolithic custom layer:

| Layer | Responsibility |
| --- | --- |
| `atlas-base` | Minimal Debian/Raspberry Pi base selection |
| `atlas-networking` | NetworkManager, resolver, time sync, CA certificates and basic tools |
| `atlas-users` | Locked container runtime account, subordinate IDs and linger |
| `atlas-podman` | Rootless Podman, first-boot initialization, auth-origin generation and auto-update |
| `atlas-containers-core` | PostgreSQL, API, web/Caddy and the shared application network |
| `atlas-containers-geodata` | Map, router, geocoder, geodata API/reloader and control network |
| `atlas-ab-update` | Signed inactive-slot update tooling |
| `atlas-device-policy` | Serial console, PCIe Gen 3 and EEPROM boot order |
| `atlas-ssh` | Hardened, persistent, disabled-by-default SSH |
| `atlas-management` | Privileged Unix-socket API, A/B health commit and factory reset |
| `atlas-kiosk` | On-demand TTY launcher, Cage/Chromium kiosk and localhost retry extension |
| `atlas-branding` | Console/SSH branding and `os-release` identity |

Keep future features in the narrowest sensible layer. Cross-layer ordering is
expressed through each layer's `X-Env-Layer-Requires` metadata.
