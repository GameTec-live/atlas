# Containers and networking

## Runtime ownership

Every Atlas workload is a systemd Quadlet owned by the locked
`atlas-containers` account (UID 2000). The account has systemd linger enabled,
so its user manager starts during boot without a login session.

## Host networking

NetworkManager owns Ethernet and Wi-Fi configuration and supplies the normal
DHCP client behavior. Wi-Fi uses iwd as NetworkManager's backend; the image does
not embed a site SSID or credentials. systemd-resolved owns DNS through the
`/etc/resolv.conf -> /run/systemd/resolve/stub-resolv.conf` symlink, and
systemd-timesyncd supplies time synchronization. Global IPv6 addresses use
privacy extensions (`ipv6.ip6-privacy=2`).

Connection profiles and NetworkManager's secret state are explicit slot-shared
paths, so static IP and Wi-Fi configuration survives an A/B update. Factory
reset clears both paths before NetworkManager starts.

The minimal diagnostic stack includes CA certificates, `curl`, `ip`, `ping`
and `ethtool`. nftables is installed because rootless Podman/netavark needs the
standard host networking stack, but Atlas does not maintain a separate custom
nftables ruleset for communication between the application containers.

`NetworkManager-wait-online.service` and `network-online.target` are both part
of normal boot. This matters because rootless Quadlets can otherwise start
before DHCP/DNS is usable or remain stuck behind a target which was never
pulled into the transaction.

When administering the user manager from `atlas`, always provide the target
runtime directory:

```sh
sudo -u atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  systemctl --user status atlas-api.service
```

Podman also needs a directory accessible to `atlas-containers`. Because
`/home/atlas` is private, a direct `sudo -u atlas-containers podman ...` can
print `cannot chdir to /home/atlas: Permission denied`. Change to `/` inside a
shell:

```sh
sudo -u atlas-containers sh -c '
  cd /
  XDG_RUNTIME_DIR=/run/user/2000 podman ps
'
```

That message is a working-directory problem, not a Podman permission problem.

## Topology

```text
clients
  |
  | TCP 80/443 and UDP 443
  v
atlas-web (Caddy)
  |                  shared atlas.network
  +--> atlas-api ------------------------------+
  |      |                                     |
  |      +--> atlas-db                         |
  |      +--> atlas-geodata-api --+            |
  |      +--> atlas-router        |            |
  |      +--> atlas-geocoder      |            |
  |                               |            |
  +--> atlas-map                  |            |
                                  |            |
                    internal geodata-control   |
                                  |            |
                         atlas-geodata-reloader+
                                  |
                                  +-- /run/user/2000/podman/podman.sock
```

The shared `atlas.network` deliberately preserves the Compose DNS aliases
`api`, `db`, `map`, `router`, `geocoder` and `geodata-api`. This avoids custom
Caddy configuration and host nftables rules. Podman still gives containers
separate namespaces and filesystems, but network-level separation between the
main workloads is not a design goal.

`geodata-control.network` is internal and connects only the geodata API and
reloader. The geodata API also joins `atlas.network`; the reloader does not.

## Workload reference

| Unit | Container user | Networks | Persistent storage | Noteworthy restrictions |
| --- | ---: | --- | --- | --- |
| `atlas-db` | image default | `atlas` as `db` | `atlas-db-data` | Health-gates dependents with `pg_isready` |
| `atlas-api` | 65532 | `atlas` as `api` | `atlas-api-data` | Drops all capabilities; reads generated API/auth env files |
| `atlas-web` | 10001 | `atlas` as `web` | Caddy data/config | Only published service; drops all capabilities then adds `NET_BIND_SERVICE` |
| `atlas-map` | image default | `atlas` as `map` | map data; shared geodata read-only | May take a long time to become healthy |
| `atlas-router` | image default | `atlas` as `router` | router data; geodata read-only | Labeled `geodata-consumer=router`; long start timeout |
| `atlas-geocoder` | 65532 | `atlas` as `geocoder` | shared geodata read-only | Read-only root; labeled `geodata-consumer=geocoder`; long start timeout |
| `atlas-geodata-api` | 65532 | `atlas`, `geodata-control` | shared geodata read/write | Read-only root plus a constrained `/tmp` tmpfs |
| `atlas-geodata-reloader` | 65532 with keep-id mapping | `geodata-control` | rootless Podman socket | Read-only root; all capabilities dropped; SELinux label separation disabled for the socket mount |
| `atlas-cloudflare-tunnel` | 65532 | host | none | Optional, condition-gated connector; read-only root and all capabilities dropped |
| `atlas-tailscale` | root in the user namespace | host, userspace Tailscale | Tailscale node state | Optional, condition-gated connector; read-only root and all capabilities dropped |

The router, geocoder and map use a 12-hour systemd start timeout because first
data processing/health checks can be genuinely long. Do not replace these with
short generic service timeouts. Router and geocoder also retry every 5 seconds
without systemd start-rate limiting: missing geodata is an expected state until
the setup wizard downloads it, and must not leave either unit permanently
failed.

## Published ports

Only `atlas-web` publishes:

- TCP 80 for redirecting to HTTPS;
- TCP 443 for HTTPS;
- UDP 443 for HTTP/3 where supported.

All other ports remain on Podman bridges. The host setting
`net.ipv4.ip_unprivileged_port_start=80` lets rootless Caddy bind 80/443. It also
allows any host user to bind ports 80 and above; this is not scoped only to
Podman.

## Optional remote access

Cloudflare Tunnel and Tailscale are shipped in the offline image but remain
stopped until their credential files exist. The management API provisions and
starts either provider with one `PUT`, reports both with one `GET`, and stops
and removes one with `DELETE`. Both are still rootless Podman containers owned
by UID 2000. They use host networking so their outbound connectors can proxy
the local Atlas HTTPS listener without publishing another host port.

Tailscale runs in userspace mode, needs no `/dev/net/tun` or `NET_ADMIN`, and
applies the immutable serve policy at `/usr/share/atlas/tailscale-serve.json`.
That policy exposes `https://<node>.<tailnet>.ts.net/` and proxies it to the
local Atlas HTTPS listener. Cloudflare uses a remotely managed tunnel token;
configure its public hostname's service as `https://127.0.0.1:443` with origin
TLS verification disabled for Atlas's device-local certificate.

When provisioning, pass the connector's external HTTPS URL as the optional
`origin`. Omit it when that origin is already configured. Trusted origins and
DNS/tunnel routing remain separate policies; provisioning remote transport does
not guess a public Cloudflare hostname or a tailnet domain.

Neither connector receives the Podman socket or the OS-management socket.
Removing local configuration does not revoke provider-side credentials, so
retiring a device must also remove it in the Cloudflare or Tailscale control
plane.

## Geodata reloader and the Podman socket

The rootless socket is enabled as
`/run/user/2000/podman/podman.sock` and mounted into the reloader at
`/var/run/docker.sock`. There is exactly one socket because router, geocoder and
reloader intentionally run under the same Podman account.

Router and geocoder carry these labels:

```text
live.gametec.atlas.geodata-consumer=router
live.gametec.atlas.geodata-consumer=geocoder
```

The reloader uses the socket and labels to find and restart consumers after
data changes. Verify both the mount and labels after changing Quadlets:

```sh
sudo -u atlas-containers sh -c '
  cd /
  export XDG_RUNTIME_DIR=/run/user/2000
  podman inspect atlas-geodata-reloader
  podman inspect atlas-router atlas-geocoder
'
```

Important security boundary: possession of the socket is equivalent to control
of the complete rootless Podman account. Labels constrain well-behaved reloader
logic; they do not stop a compromised reloader from controlling other Atlas
containers.

The reloader image is scratch-based and has no shell. Keep its image-provided
exec-form health check. A Quadlet `HealthCmd=` string is interpreted as a
shell-form check and will fail even when the reloader itself is healthy.

## Offline first boot

`pre-build.sh` stores all images in a multi-image archive. The image layer
places it at:

```text
/home/atlas-containers/.cache/atlas/images.tar
```

Before any Quadlet starts, `atlas-container-init.service`:

1. imports the archive into the rootless store;
2. deletes the archive to recover persistent space;
3. generates database/auth/job secrets when absent;
4. writes root-only environment files;
5. generates current Better Auth origins.

Quadlets use `Pull=missing`, so a captured image starts without network after
the import. Do not change this to `Pull=always` if offline boot remains a
requirement.

An A/B update contains only boot/system payloads. It cannot place a new archive
into the already-existing persistent home, so updating Atlas OS does not
guarantee that the newly captured images are installed on an existing device.
Existing devices receive container versions through normal registry
auto-update, a future explicit container-bundle mechanism, or a fresh
provisioning image.

## Container auto-updates

Each application container has `AutoUpdate=registry`.
`atlas-container-init.service` pulls `podman-auto-update.timer` into the
lingering user manager on every boot. This immutable dependency remains intact
when factory reset clears the user's persistent configuration. Updates are
independent of Atlas OS A/B updates and use mutable image tags.

The timer is dependency-started rather than directly enabled, so its loaded
state may say `disabled`. The runtime invariant is `Active: active (waiting)`
with a future trigger in `systemctl --user list-timers`.

Consequences:

- an OS rollback does not roll back a container image update;
- a container update can migrate persistent data which the old image cannot
  read;
- first boot is offline-capable, but registry auto-update requires working
  network, DNS, CA trust and registry access;
- release operators should publish containers before starting the OS build;
- multi-platform images must use Docker schema 2 media types. Podman 5.4 does
  not expose Docker `Healthcheck` metadata from OCI image configs, so
  `Notify=healthy` units cannot start after such an update;
- use `journalctl --user` and `podman auto-update --dry-run` when diagnosing the
  timer rather than assuming an OS update changed the image.

## Generated secrets and environment

Persistent files under `/home/atlas-containers/.config/atlas` are owned by
`atlas-containers`. They are mode 0600 except for the group-readable management
token:

| File | Purpose |
| --- | --- |
| `database-password` | Random PostgreSQL password source |
| `db.env` | PostgreSQL user/password/database |
| `api.env` | Better Auth secret, database URL, service URLs and job token |
| `trusted-origins` | Management-owned list of additional HTTPS origins |
| `trusted-origins.env` | Generated Better Auth base URL and complete trusted-origin list |
| `management-token` | Per-device bearer token for the host management socket (mode 0640) |
| `cloudflare-tunnel.env` | Cloudflare Tunnel token; created only when provisioned |
| `tailscale.env` | Tailscale auth key and optional hostname; created only when provisioned |

Initialization is conservative: secrets and primary env files are generated
only when missing. Do not delete them casually; deletion rotates credentials
without coordinating existing database/application state.

Older images wrote `BETTER_AUTH_URL=https://atlas.local` into `api.env`. Current
initialization removes only that exact legacy default. A deliberately custom
value is left untouched, but the later `trusted-origins.env` file also supplies
the generated value and is listed second in the API Quadlet, so the generated
value is the effective one.

## Better Auth origins and changing addresses

The API container currently rejects startup when `BETTER_AUTH_URL` is absent.
`atlas-auth-origins` therefore writes:

- `BETTER_AUTH_URL`: the first global address on an allowed up interface,
  formatted as HTTPS; fallback `https://atlas.local`;
- `BETTER_AUTH_TRUSTED_ORIGINS`: a sorted, deduplicated comma-separated list.

Trusted origins always include:

- `https://atlas.local`;
- `https://localhost`;
- `https://127.0.0.1`;
- `https://[::1]`;
- global IPv4/IPv6 addresses on physical or VPN interfaces;
- valid entries from the persistent management allowlist.

Interfaces starting with `br-`, `cni`, `docker`, `lo`, `podman`, `veth` or
`virbr` are excluded so Podman and virtualization addresses do not become
public auth origins.

NetworkManager runs the helper and tries to restart `atlas-api.service` after
interface up/down, DHCPv4/DHCPv6 changes and VPN up/down. The dispatcher exits
quietly if the lingering user D-Bus socket is not ready; normal boot generation
still occurs in `atlas-container-init.service`.

With multiple physical/VPN addresses, “first” follows `ip -o addr` ordering and
may not be the externally preferred hostname. Additional names can be trusted
through the management API. An explicit canonical-URL policy is still needed
if redirects/callbacks must prefer a public domain.

To add domains manually:

```sh
sudoedit /home/atlas-containers/.config/atlas/trusted-origins
```

Use one HTTPS origin per line, with no path, query, fragment, whitespace or
comma. Then regenerate and restart as the rootless user:

```sh
sudo -u atlas-containers env \
  HOME=/home/atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  /usr/local/libexec/atlas-auth-origins

sudo -u atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  systemctl --user try-restart atlas-api.service
```

The helper ignores invalid and non-HTTPS entries with a diagnostic.

Trusting `atlas.local` does not itself guarantee DNS/mDNS resolution. Ensure the
client and network can resolve that name, or use the current IP address/external
DNS name.

## Caddy path behavior

Caddy strips one leading `/api` before proxying to `atlas-api`. The default web
client uses an API base under `/api` and appends Better Auth's own `/api/auth`
path. Consequently, the externally visible session endpoint is normally:

```text
/api/api/auth/get-session
```

The apparently duplicated segment is intentional with the current client and
Caddyfile. Testing `/api/auth/get-session` reaches backend `/auth/get-session`
and returns 404. Do not “fix” the Caddy strip rule without updating and testing
the web client's URL construction and all other API routes together.
