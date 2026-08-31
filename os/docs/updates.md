# A/B updates

## What an update contains

The signed update bundle contains exactly:

```text
boot
system
SHA256SUMS
SHA256SUMS.sig
```

The installer rejects missing required members and all unexpected members. It
accepts Android sparse or raw boot/system payloads and verifies their sizes
before writing the inactive block devices.

The bundle does not include the partition table, provisioning map, persistent
filesystem, Podman volumes, current container images, secrets, SSH state or
EEPROM policy state.

## Normal update procedure

Copy the update to the device and verify its release checksum before invoking
the installer:

```sh
sha256sum atlas-rpi5-<version>-update.tar.zst
sudo atlas-ab-update status
sudo atlas-ab-update install --reboot \
  /path/to/atlas-rpi5-<version>-update.tar.zst
```

Option order is flexible; only one bundle path is accepted.

The SSH connection should close after installation. Wait for the trial system,
then validate it before committing:

```sh
sudo atlas-ab-update status
cat /etc/os-release

sudo -u atlas-containers \
  XDG_RUNTIME_DIR=/run/user/2000 \
  systemctl --user is-active \
    atlas-web atlas-api atlas-db atlas-map \
    atlas-router atlas-geocoder \
    atlas-geodata-api atlas-geodata-reloader

curl -k -o /dev/null -w '%{http_code}\n' https://127.0.0.1/
```

`status` must show the running slot as `pending`. Only then commit:

```sh
sudo atlas-ab-update commit
sudo atlas-ab-update status
```

Expected final state is the new active slot and `pending=none`. Perform one
ordinary reboot and repeat the health checks to prove the committed selection
survives.

## State model

| Running state | `pending` | Meaning | Safe action |
| --- | --- | --- | --- |
| Existing slot | `none` | No trial exists | Install an update |
| Candidate slot | Candidate label | One-shot tryboot succeeded | Validate, then `commit`; or `rollback`/ordinary reboot to return |
| Previous slot | Candidate label | Candidate failed or was rebooted without commit | Run `rollback` to clear the stale marker before reinstalling |
| Any slot | Different unexpected label | State/device mapping inconsistency | Stop and inspect slot links and persistent state; do not force a write |

Only one update may be pending. The guard prevents accidentally overwriting the
known-good slot while a trial is unresolved.

## Commit behavior

`commit` succeeds only when the active system partition label matches the
persistent pending marker. It writes normal autoboot selection, removes the
marker and syncs persistent state.

Do not commit merely because SSH works. Verify the UI, API/auth, database,
geodata workloads, management service and any device-specific integrations
first. Committing removes automatic fallback as the normal next boot path.

## Rollback behavior

`sudo atlas-ab-update rollback` has two behaviors:

- when currently running the pending candidate, it performs an ordinary reboot;
  Raspberry Pi tryboot then returns to the prior normal slot;
- when already running the previous slot, it removes the stale failed-candidate
  marker without another reboot.

This second case is easy to miss. After a failed or deliberately abandoned
tryboot, `status` can show the old active slot and still show the candidate as
pending. A new install will fail with `an update is already pending` until
`rollback` clears that marker.

## Why the reboot command is unusual

Raspberry Pi firmware recognizes tryboot only when the kernel reboot command
contains the exact request:

```sh
reboot '0 tryboot'
```

`systemctl reboot '0 tryboot'` does not pass that argument in the required way.
Changing this line can make the updater write a valid inactive slot but boot the
wrong slot. Source validation deliberately guards this invariant.

## Power-loss properties

The updater writes in this order:

1. inactive system;
2. inactive boot;
3. full sync;
4. pending marker and tryboot metadata.

Power loss before step 4 leaves the normal slot selected. Power loss after
tryboot metadata can still lead to a failed trial and firmware fallback. The
known-good slot is not intentionally written by the updater.

This does not make application data transactional. `/persistent` remains
writable throughout and is shared by both OS slots.

## Signature behavior

If `/etc/atlas/update-public.pem` exists, the target requires
`SHA256SUMS.sig` and verifies it with ECDSA/SHA-256. `--insecure` does not bypass
an installed public key; it is only useful for a development image built with
no public key at all.

The default public-project private key is public, so replace it for deployments
that need publisher authentication. See
[Provisioning and security](provisioning-and-security.md#update-signing-trust-model).

## Updating containers versus updating Atlas OS

These mechanisms are intentionally independent:

| Mechanism | Changes | Rollback mechanism |
| --- | --- | --- |
| `atlas-ab-update` | Boot firmware/kernel/initramfs and immutable OS root | Raspberry Pi A/B slot fallback |
| `podman-auto-update` | Rootless application image selected by a mutable registry tag | Podman/systemd behavior; not tied to OS slot |
| Application/data migration | Podman named volumes and other persistent data | Application-specific backup/restore |

Plan releases accordingly. An OS candidate can boot with container images that
were updated before or after the OS artifact was built.
