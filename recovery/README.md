# AtlasOS USB recovery

This project builds a Pi 5 USB-bootable recovery drive. It boots a tiny
BusyBox-based `pi-gen-micro` initramfs and automatically replaces one selected internal
storage device with the single Atlas artifact stored on the USB's FAT32
partition.

It supports two deliberately distinct artifact formats:

| Artifact on USB | Result |
| --- | --- |
| `*.img.zst` | The uncompressed disk image is streamed directly to SD or NVMe. The persistent partition and ext4 filesystem are expanded to the target size, producing a normal, unencrypted Atlas installation. |
| `*provision*.tar.zst` | The `rpi-image-gen` IDP archive is extracted to tmpfs and applied with the on-device IDP engine. Atlas' `pmap: crypt` creates its device-bound LUKS2 layout, and ext4 filesystems marked `expand-to-fit` are grown to their resulting partition size. |

Secure boot is never enabled. FDE provisioning does initialize and lock the
Pi firmware-crypto device key used to derive the LUKS key; it does not program a
customer secure-boot public-key hash.

BusyBox is PID 1. A small synchronous `rcS` mounts the pseudo-filesystems,
loads the required modules, and runs recovery. Device discovery uses devtmpfs
and bounded polling, so no udev daemon is required. No systemd service manager,
journal, networking stack, SSH, or interactive UI is included. If recovery
returns, BusyBox starts automatic root shells on HDMI and serial for diagnosis;
a successful recovery powers off before those shells are started.

> **Destructive by design:** booting this USB immediately validates its payload
> and then erases the selected SD card or NVMe. It has no confirmation prompt.

## Safety model

The installer refuses to write unless all of these are true:

- it is running on a Pi 5-family device;
- its boot filesystem has the `ATLASRECOV` label and resides on USB storage;
- exactly one supported compressed artifact is present;
- the artifact matches `SHA256SUMS`, or at least passes a full zstd integrity check;
- the target is an unmounted whole SD/NVMe device and is not the recovery USB;
- `TARGET=auto` resolves to exactly one device, unless the IDP descriptor names its required storage type.

For IDP, archive extraction, JSON validation and sparse-image presence checks
all happen before target erasure. Extraction uses a tmpfs capped at 75% of RAM.
On an 8 GB Pi this provides roughly 6 GB while leaving memory for the recovery
OS and fastboot buffers. An oversized archive therefore fails without touching
the target.

FAT32 limits each compressed artifact to less than 4 GiB. Atlas' approximately
2 GiB artifacts fit comfortably.

## Build

Build on Debian, Ubuntu, or Debian under WSL 2. The generated roots must live
on a Linux filesystem; the scripts therefore keep the pi-gen-micro checkout and
work tree below `${XDG_CACHE_HOME:-$HOME/.cache}/atlas-recovery` by default.

Install the host dependencies:

```sh
sudo apt-get update
sudo apt-get install --no-install-recommends \
  binfmt-support dosfstools fdisk git mtools qemu-user-static rsync \
  uidmap unzip util-linux zip zstd
```

The pi-gen-micro sysroot also needs unprivileged user namespaces and configured
`/etc/subuid` and `/etc/subgid` ranges, as documented by upstream.

Build the boot environment and assemble a 6 GiB FAT32 recovery image in one
command:

```sh
./scripts/build.sh \
  ../os-build/atlas-rpi5-v1-provisioning.tar.zst
```

Or build an unencrypted recovery USB:

```sh
./scripts/build.sh \
  ../os-build/atlas-rpi5-v1-development.img.zst
```

Outputs are written to `out/`:

```text
atlas-recovery-usb.img
atlas-recovery-usb.img.zst
atlas-recovery-usb.zip
SHA256SUMS.recovery
```

The raw image is sparse. The compressed copy is usually much smaller because
unused FAT32 space compresses well. The ZIP contains the files from the FAT32
partition without a disk-image container. Extract its contents directly into
the root of an empty FAT32-formatted flash drive labelled `ATLASRECOV`,
including the hidden `.atlas-recovery-media` marker.

Override the FAT partition capacity when needed:

```sh
RECOVERY_IMAGE_SIZE_MIB=7168 ./scripts/build.sh /path/to/artifact
```

The image size must fit the physical USB drive. The default 6144 MiB image fits
nominal 8 GB drives and leaves substantial room for replacing the payload.

The Atlas OS GitHub Actions build publishes encrypted and unencrypted recovery
variants in both formats:

```text
atlas-rpi5-<version>-recovery-encrypted.img.zst
atlas-rpi5-<version>-recovery-encrypted.zip
atlas-rpi5-<version>-recovery-unencrypted.img.zst
atlas-rpi5-<version>-recovery-unencrypted.zip
```

The build pins pi-gen-micro and rpi-fastbootd in `versions.env`. It rebuilds the
daemon with a local recovery patch that preserves IDP state across the separate
fastboot client calls and polls devtmpfs directly because recovery has no udev
daemon. Keep both revisions and the patch together because IDP protocol behavior
is shared between them.

The Raspberry Pi archive key currently has SHA-1 self-certifications which
current Debian Sequoia policy rejects. Upstream pi-gen-micro attempts to relax
that policy inside its sysroot, but the initial `mmdebstrap` verification runs
on the host. `build-boot.sh` therefore scopes `apt-sequoia.config` to that build
command. This permits the archive key binding through 2030; packages are still
authenticated by apt and recovery artifacts use SHA-256.

## Flash and run

Verify and flash the image to the complete USB device, not one of its
partitions:

```sh
cd out
sha256sum --check SHA256SUMS.recovery
zstd -dc atlas-recovery-usb.img.zst | \
  sudo dd of=/dev/disk/by-id/usb-YOUR_DRIVE bs=16M conv=fsync status=progress
```

Confirm the `of=` device carefully. This command overwrites it.

Then:

1. Shut down the target Pi.
2. Insert the recovery USB.
3. Power it on and watch HDMI or the 115200-baud serial console.
4. Wait for the Pi to power itself off after the `SUCCESS` message.
5. Remove the USB and power it on again.

Atlas configures the EEPROM with USB first in its boot order, which makes this
a usable disaster-recovery path after a normal installation. A new Pi whose
EEPROM does not try USB first must have its boot order configured once before
this drive can start.

## Select SD or NVMe

The FAT32 partition contains `recovery.conf`:

```ini
TARGET=auto
```

For raw-image restoration, change this to `sd` or `nvme` if both are installed.
`auto` intentionally refuses an ambiguous machine rather than erasing one by
guessing.

An IDP archive declares its storage type in its JSON descriptor. That value is
authoritative and must match the archive produced by `rpi-image-gen`; the
recovery tool will not silently rewrite a storage-specific provisioning map.

## Replace the Atlas artifact without rebuilding

After flashing the recovery image, mount its `ATLASRECOV` FAT32 partition on
a development machine and run:

```sh
./scripts/set-artifact.sh /media/$USER/ATLASRECOV \
  /path/to/new-atlas-rpi5-provisioning.tar.zst
```

The helper removes the previous supported payload, copies the new one, and
regenerates `SHA256SUMS`. It checks for the recovery-media marker before making
changes. You can perform the same three file operations manually from Windows
or macOS; keep exactly one supported artifact and update its checksum line.

Replacing the artifact does not rebuild or reflash the recovery environment.

## Validation

Run the source checks and focused disk-assembly test with:

```sh
./ci/validate.sh
./ci/test-assemble.sh
```

## Failure behavior

On failure the Pi stays in the recovery initramfs and exposes an automatic root
console on HDMI and serial. Relevant output is in:

```text
/run/atlas-recovery/recovery.log
/run/atlas-recovery/fastbootd.log
```

Power-cycle to retry. An interrupted raw write or an IDP failure after erasure
can leave the target unbootable, which is expected for recovery media; the USB
remains independently bootable so the operation can be repeated.
