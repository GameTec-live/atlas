# Atlas OS Management API

A small, root-only Go service for the privileged operations that must not run
inside the Atlas API container. It listens only on
`/run/atlas-management/api.sock`; it never opens a TCP port and never downloads
updates from the internet.

The rootless `atlas-api` container receives the socket read-only plus a random
256-bit bearer token generated on the device. Socket group permissions are the
first boundary and the token is a second check. Every endpoint, including the
health endpoint, requires `Authorization: Bearer <token>`.

## API

All routes are below `/api/v1`:

| Method and path | Operation |
| --- | --- |
| `GET /update` | Active, inactive and pending A/B slot plus trial-monitor state |
| `POST /update` | Stream one `bundle` file in a multipart request, install it to the inactive slot and tryboot it |
| `POST /update/rollback` | Roll back or clear a failed pending candidate |
| `POST /power/reboot` | Reboot |
| `POST /power/poweroff` | Power off |
| `POST /factory-reset` | Schedule a data/configuration reset and reboot |
| `GET /ssh` | Read the persistent SSH policy |
| `POST /ssh/enable` | Persistently enable and start SSH |
| `POST /ssh/disable` | Persistently disable and stop SSH |
| `GET /connections/adapters` | Available and placeholder connection adapters |
| `GET /connections/network-manager` | List NetworkManager connections |
| `GET /connections/network-manager/devices` | List connected and disconnected network devices |
| `GET /connections/network-manager/wifi` | Scan Wi-Fi; optional `device` query |
| `POST /connections/network-manager/wifi` | Connect using `ssid`, optional `password`, `device`, and `hidden` |
| `GET/PUT /connections/network-manager/{uuid}/ip` | Read or replace IPv4/IPv6 configuration |
| `POST /connections/network-manager/{uuid}/disconnect` | Disconnect a connection |
| `DELETE /connections/network-manager/{uuid}` | Forget a connection |
| `GET/POST/DELETE /connections/auth-origins` | List, add, or remove an external HTTPS origin |

IPv4 and IPv6 settings use `method` (`auto`, `manual`, or `disabled`), CIDR
`addresses`, an optional `gateway`, and `dns` addresses. Values are validated
before they are passed to `nmcli`, and commands are executed directly without a
shell. Wi-Fi passwords go through stdin rather than process arguments.

Example update from the host for diagnostics:

```sh
curl --unix-socket /run/atlas-management/api.sock \
  -H "Authorization: Bearer $(cat /home/atlas-containers/.config/atlas/management-token)" \
  -F bundle=@atlas-rpi5-update.tar.zst \
  http://localhost/api/v1/update
```

For normal host administration, use the installed `atlas-sys` client instead
of constructing authenticated requests by hand:

```sh
sudo atlas-sys status
sudo atlas-sys update apply /path/to/atlas-rpi5-update.tar.zst
sudo atlas-sys update rollback
sudo atlas-sys reboot
sudo atlas-sys poweroff
sudo atlas-sys ssh status
sudo atlas-sys ssh enable
sudo atlas-sys ssh disable
sudo atlas-sys factory-reset
```

The reset command requires typing `RESET` on an interactive terminal. The
explicit `factory-reset --yes` form is available for automation. The client is
root-only, reads the same per-device token, and communicates exclusively over
the Unix socket.

SSH changes delegate to the host's `atlas-ssh` controller. This keeps the
persistent policy used during boot as the single source of truth rather than
directly toggling `ssh.service` only for the current boot.

The upload is staged under `/persistent/atlas/system`, verified and installed
by `atlas-ab-update`, then the device enters the one-shot candidate slot. On
candidate startup the manager waits until all eight containers are running and
healthy, including an HTTPS API reachability check. Health must remain stable
for five continuous minutes before the slot is committed. A failed check resets
the timer; it never causes a bad candidate to be committed.

Factory reset runs before NetworkManager and the rootless user manager on the
next boot. It removes application containers, volumes, configuration, secrets,
trusted origins, Wi-Fi/IP profiles, logs, machine identity and device policy.
It preserves the existing LUKS container/key when present and retains only the
OCI image store needed for an offline fresh boot, then reboots once more. It
works the same way on the unencrypted development image.

## Development

```sh
go test -race ./...
go vet ./...
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath .
```

The adapters use small interfaces and an injectable command runner. Adding a
future connection adapter does not require widening the NetworkManager API or
introducing arbitrary command execution.
