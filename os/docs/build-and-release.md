# Build and release

## Reproducibility boundary

The build pins `rpi-image-gen` to commit
`33e98d9aca74b83002ab01ff760ae5d4fa8e99a5`, but it deliberately does not pin
the Atlas container tags. `pre-build.sh` resolves every entry in `images.txt`
at build time and captures the then-current ARM64 manifests.

This means:

- the operating-system generator behavior is pinned;
- Debian snapshot selection and the Atlas version identify the OS build;
- two builds from the same Atlas commit can still contain different container
  images if a `latest` tag moved between builds;
- publish the desired container images before starting an OS release;
- avoid moving any participating tag while `pre-build.sh` is pulling its list,
  otherwise one archive can contain images from two different publish waves;
- retain the build artifacts and checksums if exact reconstruction matters.

## Prerequisites

The canonical local environment is Debian under WSL 2. Native Debian or Ubuntu
also works. The build needs substantial free space: it temporarily creates a
12.5 GiB raw disk image, a roughly 2 GiB OCI archive, sparse images, compressed
release files and a complete target root filesystem.

Install WSL and the Debian distribution from Windows if needed, then run the
remaining commands inside Debian. Avoid building directly on a Windows-mounted
filesystem: Linux ownership, subordinate IDs, symlinks and executable bits are
part of the image. Keep the generator and its work directory inside the WSL
filesystem; only the out-of-tree source may live at `/mnt/c/...`.

Required host tools are installed by `rpi-image-gen/install_deps.sh` plus:

```sh
sudo apt-get update
sudo apt-get install --no-install-recommends \
  podman ripgrep shellcheck zstd
```

ARM64 builds on an x86-64 WSL host use QEMU/binfmt and are much slower than the
native ARM64 GitHub runner.

## Local WSL build

The example assumes the repository is visible at
`/mnt/c/Users/<name>/Desktop/atlas` and keeps all generated data in WSL:

```sh
set -eu

build_root=/var/tmp/atlas-os-build
git clone https://github.com/raspberrypi/rpi-image-gen.git \
  "$build_root/rpi-image-gen"
git -C "$build_root/rpi-image-gen" checkout \
  33e98d9aca74b83002ab01ff760ae5d4fa8e99a5

sudo "$build_root/rpi-image-gen/install_deps.sh"
sudo apt-get install --no-install-recommends \
  podman ripgrep shellcheck zstd

source_root=/mnt/c/Users/<name>/Desktop/atlas/os
work_root="$build_root/work"
mkdir -p "$work_root"

"$source_root/ci/validate.sh" \
  "$source_root" "$build_root/rpi-image-gen"

cd "$build_root/rpi-image-gen"
ATLAS_UPDATE_SIGNING_KEY="$source_root/keys/atlas-update.key" \
  ./rpi-image-gen build \
    -S "$source_root" \
    -c atlas.yaml \
    -B "$work_root" \
    -- IGconf_artefact_version=development

"$source_root/ci/audit-build.sh" "$work_root"
```

Use a meaningful immutable version for release builds. It becomes
`IMAGE_VERSION` in `/etc/os-release` and appears in artifact metadata.

Do not run the full generator as Windows PowerShell against the NTFS workspace.
PowerShell is only a convenient launcher for `wsl -d Debian -- ...`.

## Expected WSL warnings

An unprivileged WSL build can print warnings such as:

- failure to mount `/proc` or `/sys`, followed by a bind-mount fallback;
- inability to communicate with the host device-mapper driver while generating
  the target initramfs;
- inability to resolve target-only `/dev/disk/by-slot/...` paths;
- a warning that 16 KiB ext4/verity blocks exceed the x86-64 host page size;
- a missing `fsck.erofs` hook warning.

These are expected only when the generator continues past them and completes
the image. Never dismiss a non-zero build exit, a missing artifact, a failed
hash/signature check, or a failed artifact audit as a WSL quirk.

### Shifted ownership during local audit

Rootless build tools may represent target UID 2000 as a shifted host UID such
as 101999. If the artifact audit fails only on expected target ownership when
run from the host namespace, run it in the Podman user namespace:

```sh
podman unshare ./os/ci/audit-build.sh /path/to/work
```

This is not permission repair. Do not recursively `chown` the generated root;
that can corrupt the ownership which should appear on the target.

## What the build hooks do

### `pre-build.sh`

1. builds `apps/osManagementAPI` as a static Linux/ARM64 host binary;
2. reads non-empty, non-comment entries from `images.txt`;
3. pulls each image explicitly for `linux/arm64`;
4. saves all ten tagged images into one deduplicated Docker-format archive;
5. exposes the binary and archive to their image layers.

The build must have registry access, including access to any private image if a
future image list introduces one.

### `post-image.sh`

The generated A/B update initially contains boot and system payloads. The hook:

1. extracts the bundle;
2. writes `SHA256SUMS` for `boot` and `system`;
3. signs that checksum file with ECDSA/SHA-256;
4. repacks only the two payloads, checksum and signature.

`ATLAS_UPDATE_SIGNING_KEY` overrides the tracked project key. The public key
embedded in the image must match the private signing key or target installation
will reject the update.

## Validation and audit

`ci/validate.sh` is a fast source-level gate. It checks:

- every layer's rpi-image-gen metadata;
- the composed configuration;
- shell syntax and ShellCheck;
- exact tryboot invocation behavior;
- generation of every Quadlet;
- the single Podman socket mount and required reloader labels;
- management socket/token mounts only on `atlas-api`;
- factory-reset ordering and slot-shared NetworkManager state;
- dynamic Better Auth configuration wiring;
- every image listed in `images.txt` has a matching Quadlet.

`ci/audit-build.sh` is intentionally separate and needs a completed build. It
checks the assembled root filesystem and output artifacts, including:

- required networking/security binaries and symlinks;
- the static management binary, socket configuration and reset unit;
- branded `os-release` fields;
- auth-origin helper and migration behavior;
- ownership of persistent rootless directories;
- serial, PCIe and SSH enablement policy;
- provisioning-map expansion flags;
- exactly ten expected ARM64 images in the offline archive;
- update payload hashes and signature against the embedded public key.

Keep both scripts. Validation catches cheap source mistakes before a long
build; audit catches errors that only exist after layer composition.

## Output locations

For `work_root=/path/to/work`, the important raw outputs are:

| Path | Meaning |
| --- | --- |
| `work/image-atlas-rpi5/update.tar.zst` | Signed A/B update bundle |
| `work/deploy-<version>/atlas-rpi5.img.zst` | Compressed unencrypted development disk image |
| `work/deploy-<version>/atlas-rpi5-<version>.tar.zst` | IDP provisioning archive |
| `work/atlas-images.tar` | Intermediate offline OCI archive used in the initial persistent filesystem |

The GitHub workflow renames and stages the first three into the public names
listed in [the documentation index](README.md#artifact-cheat-sheet). It then
builds the recovery boot environment once and combines it separately with the
encrypted provisioning archive and unencrypted development image. Each
recovery variant is published as a compressed disk image and as a file-only ZIP.
Finally, the workflow creates one `SHA256SUMS` for all release files.

## GitHub Actions behavior

`.github/workflows/os.yml` lives at the repository root, not inside `os`.

- Pull requests to `main` that touch `os/**`, `recovery/**` or the workflow run
  validation only on `ubuntu-24.04`.
- Pushes to `main` run validation and a full native ARM64 build.
- Manual dispatch runs validation and a full native ARM64 build.
- `v*` tags build and also create/update the matching GitHub Release.
- Re-running an existing tag replaces same-named release assets with
  `gh release upload --clobber`; release tags should still be treated as
  immutable operationally.
- Non-tag builds use the first 12 characters of the commit SHA as the version.
- Every full build uploads a 14-day Actions artifact without additional
  Actions compression because the release files are already compressed.
- The build job has a 180-minute timeout to accommodate the OS build and the
  shared recovery boot-environment build.

The workflow intentionally has permission to write release contents only in
the build job.

## Cleaning local builds

Generated roots can contain files owned through a rootless user namespace. A
normal `rm -rf` may fail with `Permission denied`. Verify the exact path, then
remove the build directory through the same namespace:

```sh
realpath /var/tmp/atlas-os-build
podman unshare rm -rf -- /var/tmp/atlas-os-build
```

Never substitute an unresolved variable, `/`, `~` or a repository root into
that command.
