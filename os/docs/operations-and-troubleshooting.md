# Operations and troubleshooting

## First boot sequence

A normal first boot roughly follows this order:

1. Raspberry Pi firmware selects the configured boot/system slot.
2. The encrypted storage path is opened when the device was provisioned with
   the production flow.
3. systemd mounts the immutable root and persistent storage.
4. NetworkManager obtains DHCP configuration; systemd-resolved and time sync
   become available.
5. The lingering `atlas-containers` user manager starts.
6. `atlas-container-init` imports the offline OCI archive, deletes it, creates
   secrets and generates auth origins.
7. Quadlets create networks/volumes and start containers in dependency order.
8. Caddy becomes healthy and exposes the UI over HTTPS.

First boot is slower than later boots because importing roughly 2 GiB of image
data and initializing PostgreSQL/storage are I/O intensive. Router, geocoder and
map health checks can also take much longer when data is first prepared. A unit
in `activating` is not automatically failed; inspect its logs and configured
timeout.

## Initial login and access

- Console and serial-console account: `atlas`
- Initial password: `atlas`
- Hostname: `atlas`
- UI: `https://<device-address>/`
- Friendly origin: `https://atlas.local/`, only if that name resolves on the
  client network
- SSH: disabled by default

Change the initial password before enabling remote maintenance:

```sh
passwd
sudo atlas-ssh enable
```

The local Caddy CA is normally untrusted on a new client. A browser certificate
warning does not by itself mean HTTPS is down. Use `curl -k` only for diagnostic
reachability, then install/trust the appliance CA or configure a publicly
trusted domain for normal use.

## Health checklist

### Host

```sh
cat /etc/os-release
sudo atlas-security-status
sudo atlas-ab-update status
sudo atlas-ssh status

systemctl is-active \
  NetworkManager \
  network-online.target \
  atlas-management.service
```

Expected branded identity includes:

```text
NAME="Atlas OS"
ID=debian
VARIANT_ID=atlas
IMAGE_ID=atlas
IMAGE_VERSION="<build version>"
```

`ID=debian` is intentional so Debian-aware tooling continues to work.

### Rootless workload

```sh
sudo -u atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  systemctl --user is-active \
    atlas-web atlas-api atlas-db atlas-map \
    atlas-router atlas-geocoder \
    atlas-geodata-api atlas-geodata-reloader
```

All eight should eventually report `active`.

### External endpoints

From another machine:

```sh
curl -k -sS -o /dev/null -w 'ui=%{http_code}\n' \
  https://<device-address>/
curl -k -sS -o /dev/null -w 'map=%{http_code}\n' \
  https://<device-address>/map/
curl -k -sS -o /dev/null -w 'auth=%{http_code}\n' \
  -H 'Origin: https://<device-address>' \
  https://<device-address>/api/api/auth/get-session
curl -sS -o /dev/null -w 'http=%{http_code}\n' \
  http://<device-address>/
```

Healthy unauthenticated reachability normally gives UI 200, map 200, auth 200
and HTTP 308.

## Logs and inspection

Host units:

```sh
sudo journalctl -b -u NetworkManager -u atlas-management --no-pager
sudo journalctl -b -u atlas-ssh-state -u atlas-usb-boot-order --no-pager
```

Rootless units:

```sh
sudo -u atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  journalctl --user -b -u atlas-api -n 100 --no-pager

sudo -u atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  systemctl --user --no-pager -l status atlas-web
```

Podman state:

```sh
sudo -u atlas-containers sh -c '
  cd /
  export XDG_RUNTIME_DIR=/run/user/2000
  podman ps --all
  podman images
  podman network ls
  podman volume ls
'
```

## SSH state

```sh
sudo atlas-ssh enable
sudo atlas-ssh disable
sudo atlas-ssh status
```

The command writes `/persistent/atlas/system/ssh.state`; the boot-time
`atlas-ssh-state.service` applies it. Enabling/disabling a systemd unit directly
does not update that persistent policy and can be undone on reboot.

If SSH is unexpectedly unavailable:

1. use the local or serial console;
2. run `sudo atlas-ssh status`;
3. inspect `systemctl status ssh atlas-ssh-state`;
4. validate config with `sudo sshd -t`;
5. confirm only `atlas` is being used and the account/password/key is valid;
6. enable it through `atlas-ssh`, not `systemctl enable ssh`.

## Management service

`atlas-management.service` is currently a hardened placeholder. It runs as
host root, creates the persistent trusted-origin allowlist, logs that no
external API exists, then waits. It cannot yet power-cycle the Pi or receive UI
commands.

Its current sandbox denies new privileges, private device access, kernel log/
module/tunable changes, control-group changes, non-Unix address families and
general filesystem writes. The only declared writable locations are
`/persistent/atlas/system` and
`/home/atlas-containers/.config/atlas`. Extend these narrowly when real
operations are added; broadening the service to unrestricted root would erase
most of the value of having a small management boundary.

Use:

```sh
systemctl status atlas-management.service
sudo journalctl -u atlas-management.service --no-pager
```

The current login MOTD mentions `sudo atlas-sys status`, but no `atlas-sys`
command is implemented yet. Treat that line as a placeholder; use the systemd
commands above.

When implementing the real service:

- expose an authenticated Unix-socket API, not a public root daemon;
- keep privileged operations small and explicit;
- delegate SSH state changes to `atlas-ssh`;
- validate and atomically update the trusted-origin file;
- run `atlas-auth-origins` as UID 2000 and restart the API after origin changes;
- preserve the existing systemd sandbox and add permissions only when an
  implemented operation demonstrably needs them;
- carefully design authorization, replay protection and UI-to-host request
  boundaries before adding reboot/power operations.

## Adding external auth origins

Edit:

```text
/home/atlas-containers/.config/atlas/trusted-origins
```

The file is owned by UID 2000 and mode 0600. Use one HTTPS origin per line:

```text
# Customer ingress
https://atlas.example.com
https://atlas.example.com:8443
```

Paths such as `https://atlas.example.com/app`, HTTP URLs, commas, queries and
fragments are rejected. Regenerate/restart with the commands in
[Containers and networking](containers-and-networking.md#better-auth-origins-and-changing-addresses).

Confirm the effective values:

```sh
sudo cat /home/atlas-containers/.config/atlas/trusted-origins.env
sudo -u atlas-containers sh -c '
  cd /
  XDG_RUNTIME_DIR=/run/user/2000 podman inspect atlas-api
' | grep BETTER_AUTH
```

Adding an origin does not create DNS, open a firewall, configure port
forwarding or guarantee a trusted certificate. Those are separate ingress
tasks.

## Device policy

### Serial console

Kernel command line and systemd enable `serial0` at 115200 baud. This is the
primary recovery path when networking or SSH fails.

### PCIe Gen 3

`dtparam=pciex1_gen=3` is scoped to `[pi5]`. Raspberry Pi 5 is not conservatively
certified for every Gen 3 peripheral/cable/layout. If NVMe or another PCIe
device is unreliable, remove/override the setting and retest at Gen 2 before
blaming storage encryption or filesystem code.

### USB-first boot order

On Pi 5, a one-time service schedules EEPROM `BOOT_ORDER=0xf164`, evaluated as:

1. USB mass storage (`4`);
2. NVMe (`6`);
3. SD card (`1`);
4. restart the scan (`f`).

The service backs up the previous EEPROM configuration to
`/persistent/atlas/system/eeprom-config.before-usb-order` and writes
`usb-boot-order.applied`. The EEPROM update is scheduled and takes effect on a
subsequent reboot. Removing the marker deliberately causes policy re-evaluation
and should not be part of routine troubleshooting.

## Common failures

### `curl` reports code `000`

Code `000` means no HTTP response was obtained. Check link/DHCP, port 443,
reboot progress and `atlas-web`. It is different from a TLS verification error
and different from an HTTP 4xx/5xx response.

### Browser cannot open HTTPS, but HTTP responds

Check whether HTTP returns 308, then use `curl -k` to separate certificate trust
from service reachability. If `-k` succeeds, install the local CA or configure a
trusted external certificate. If it does not, inspect `atlas-web` and its Caddy
logs.

### `atlas-web` stays `activating`

The Quadlet uses `Notify=healthy` and waits on API/map. Inspect all dependency
units. Do not assume Caddy process existence means systemd considers the unit
healthy.

### API repeatedly exits with invalid `BETTER_AUTH_URL`

The current API schema requires a URL string. Confirm
`trusted-origins.env` exists and contains both generated variables. Run
`atlas-auth-origins` as `atlas-containers`, reset the failed unit and restart:

```sh
sudo -u atlas-containers env \
  HOME=/home/atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  /usr/local/libexec/atlas-auth-origins
sudo -u atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  systemctl --user reset-failed atlas-api atlas-web
sudo -u atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  systemctl --user restart atlas-api
```

### Better Auth diagnostic returns 404

Use `/api/api/auth/get-session`, not `/api/auth/get-session`, with the current
web client/Caddy routing. See the path explanation in
[Containers and networking](containers-and-networking.md#caddy-path-behavior).

### `cannot chdir to /home/atlas: Permission denied`

The target rootless account cannot enter the administrator's private home.
Change to `/` before invoking Podman as shown under
[Runtime ownership](containers-and-networking.md#runtime-ownership).

### Reloader cannot restart router/geocoder

Verify:

- `podman.socket` is active in the UID 2000 user manager;
- `/run/user/2000/podman/podman.sock` exists;
- reloader mount destination is `/var/run/docker.sock` and writable;
- router and geocoder have their exact consumer labels;
- all three containers belong to `atlas-containers`;
- the reloader retained its image-provided exec health check.

Do not create separate Podman accounts/sockets for these consumers without also
redesigning the reloader's access model.

### Rootless units wait forever for networking

Confirm both `NetworkManager-wait-online.service` and
`network-online.target` are active, and that
`multi-user.target.wants/network-online.target` exists. Merely enabling
NetworkManager does not necessarily pull the target into the boot transaction.

### First boot runs out of persistent space

The compressed archive and imported store coexist until import succeeds. Check
the provisioning expansion result and free space under the rootless home. The
8 GiB configured data size is a minimum, not arbitrary overhead. Do not reduce
it without measuring current image archive/import sizes.

### Update refuses because one is already pending

Run `sudo atlas-ab-update status`. If the current slot differs from the pending
candidate, the trial has already fallen back; run
`sudo atlas-ab-update rollback` once to clear the marker. See
[A/B updates](updates.md#rollback-behavior).

### Production device reports encryption not detected

Confirm the production provisioning archive was used. A directly flashed
development image is intentionally unencrypted. If production provisioning was
used, inspect boot/initramfs and slot-mapper logs before writing data or
reprovisioning.
