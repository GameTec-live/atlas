#!/bin/sh
set -eu

project_dir=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)

for script in \
    "$project_dir/configurations/atlas-recovery/installer_scripts.list" \
    "$project_dir/configurations/atlas-recovery/atlas-recover" \
    "$project_dir/configurations/atlas-recovery/post_creation.sh" \
    "$project_dir/configurations/atlas-recovery/rcS" \
    "$project_dir/scripts/build-boot.sh" \
    "$project_dir/scripts/assemble-image.sh" \
    "$project_dir/scripts/build.sh" \
    "$project_dir/scripts/set-artifact.sh"; do
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
dpkg_args=$project_dir/configurations/atlas-recovery/dpkg_extra_args

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
[ "$extract_line" -lt "$erase_line" ]
grep -q '\[ "$target" != "$media_disk" \]' "$recovery"
grep -q 'artifact_count" -eq 1' "$recovery"
grep -q 'fastbootd -v -i tcp' "$recovery"
grep -q 'kill -USR1 "\$dd_pid"' "$recovery"
grep -q 'oem fwcrypto init' "$recovery"
grep -q 'oem idpdone' "$recovery"
! grep -q 'secure.boot\|program_pubkey\|CUSTOMER_KEY' "$recovery"
grep -q 'build/usr/lib/modules-load.d/fastbootd.conf' \
    "$project_dir/configurations/atlas-recovery/post_creation.sh"

echo "Atlas recovery source validation passed."
