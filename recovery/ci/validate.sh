#!/bin/sh
set -eu

project_dir=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)

for script in \
    "$project_dir/configurations/atlas-recovery/installer_scripts.list" \
    "$project_dir/configurations/atlas-recovery/atlas-recover" \
    "$project_dir/configurations/atlas-recovery/post_creation.sh" \
    "$project_dir/configurations/atlas-recovery/rcS" \
    "$project_dir/scripts/build-fastbootd.sh" \
    "$project_dir/scripts/build-boot.sh" \
    "$project_dir/scripts/assemble-image.sh" \
    "$project_dir/scripts/build.sh" \
    "$project_dir/scripts/set-artifact.sh"; do
    [ -x "$script" ] || {
        echo "Recovery script is not executable: $script" >&2
        exit 1
    }
    sh -n "$script"
done

if command -v shellcheck >/dev/null 2>&1; then
    shellcheck -s sh \
        "$project_dir/configurations/atlas-recovery/installer_scripts.list" \
        "$project_dir/configurations/atlas-recovery/atlas-recover" \
        "$project_dir/configurations/atlas-recovery/post_creation.sh" \
        "$project_dir/configurations/atlas-recovery/rcS" \
        "$project_dir/scripts/"*.sh
fi

recovery=$project_dir/configurations/atlas-recovery/atlas-recover
packages=$project_dir/configurations/atlas-recovery/packages.list
dpkg_args=$project_dir/configurations/atlas-recovery/dpkg_extra_args
kernel_modules=$project_dir/configurations/atlas-recovery/kernel_modules.list
post_creation=$project_dir/configurations/atlas-recovery/post_creation.sh
fastbootd_patch=$project_dir/patches/rpi-fastbootd-persistent-tcp.patch

# pi-gen-micro prefixes every non-comment line with `--`; an empty line would
# consequently become dpkg's end-of-options marker before its action argument.
! grep -q '^[[:space:]]*$' "$dpkg_args"
grep -qx 'SYSTEMD=0' "$project_dir/configurations/atlas-recovery/components.parameters"
grep -qx 'UDEV=0' "$project_dir/configurations/atlas-recovery/components.parameters"
grep -q '^/usr/local/sbin/atlas-recover$' \
    "$project_dir/configurations/atlas-recovery/rcS"
[ ! -e "$project_dir/configurations/atlas-recovery/atlas-recovery.service" ]
[ ! -e "$project_dir/configurations/atlas-recovery/udebs.list" ]

# These checks protect the most important destructive-operation ordering and
# ensure the recovery source can never be selected as a target.
extract_line=$(grep -n 'extract_idp$' "$recovery" | cut -d: -f1)
erase_line=$(grep -n 'fb 60 erase' "$recovery" | cut -d: -f1)
flash_line=$(grep -n 'fb 900 flash' "$recovery" | cut -d: -f1)
grow_line=$(grep -n 'grow_idp_ext4_filesystem "$block_device" "$sparse_image"' "$recovery" | cut -d: -f1)
done_line=$(grep -n 'fb 120 oem idpdone' "$recovery" | cut -d: -f1)
raw_partition_line=$(grep -n 'parted --script --fix "$target" resizepart' "$recovery" | cut -d: -f1)
raw_filesystem_line=$(grep -n 'grow_ext4_filesystem "$persistent_device"' "$recovery" | cut -d: -f1)
raw_done_line=$(grep -n 'Unencrypted Atlas restore completed' "$recovery" | cut -d: -f1)
[ "$extract_line" -lt "$erase_line" ]
[ "$flash_line" -lt "$grow_line" ]
[ "$grow_line" -lt "$done_line" ]
[ "$raw_partition_line" -lt "$raw_filesystem_line" ]
[ "$raw_filesystem_line" -lt "$raw_done_line" ]
grep -q '\[ "$target" != "$media_disk" \]' "$recovery"
grep -q 'artifact_count" -eq 1' "$recovery"
grep -q 'fastbootd -v -i tcp' "$recovery"
grep -q 'ifconfig lo 127\.0\.0\.1' "$recovery"
grep -qx 'ipv6' "$kernel_modules"
grep -q 'kill -USR1 "\$dd_pid"' "$recovery"
grep -q 'oem fwcrypto init' "$recovery"
grep -q 'oem idpdone' "$recovery"
grep -q 'resize2fs "$local_device"' "$recovery"
! grep -q 'blkid -p' "$recovery"
grep -qx 'e2fsprogs' "$packages"
grep -qx 'parted' "$packages"
! grep -q 'secure.boot\|program_pubkey\|CUSTOMER_KEY' "$recovery"
grep -q 'build/usr/lib/modules-load.d/fastbootd.conf' "$post_creation"
grep -q "s|/sbin/getty|/bin/getty|g" "$post_creation"
grep -q 'FastbootDevice device("tcp")' "$fastbootd_patch"
grep -q 'device.ExecuteCommands()' "$fastbootd_patch"
grep -q 'BlockDevReady(target_path)' "$fastbootd_patch"

echo "Atlas recovery source validation passed."
