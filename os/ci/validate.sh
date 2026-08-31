#!/bin/bash
set -euo pipefail

source_root=${1:?usage: validate.sh SOURCE_ROOT RPI_IMAGE_GEN_ROOT}
generator_root=${2:?usage: validate.sh SOURCE_ROOT RPI_IMAGE_GEN_ROOT}
ig="$generator_root/rpi-image-gen"

for layer in "$source_root"/layer/*.yaml; do
    "$ig" metadata --lint "$layer"
done

"$ig" config "$source_root/config/atlas.yaml" >/dev/null

mapfile -t source_files < <(
    find "$source_root" -path "$source_root/dist" -prune -o -type f -print
)

for script in "${source_files[@]}"; do
    case "$(head -n 1 "$script")" in
        '#!/bin/bash'*) bash -n "$script" ;;
        '#!/bin/sh'*) sh -n "$script" ;;
    esac
done

mapfile -t shell_scripts < <(
    for script in "${source_files[@]}"; do
        head -n 1 "$script" | grep -q '^#!.*/\(ba\)\?sh' && printf '%s\n' "$script"
    done
)
if [[ ${#shell_scripts[@]} -gt 0 ]]; then
    shellcheck -x "${shell_scripts[@]}"
fi

rg -q "^[[:space:]]*reboot '0 tryboot'$" \
    "$source_root/layer/atlas-ab-update.rootfs-overlay/usr/local/sbin/atlas-ab-update"
if rg -q -F "systemctl reboot '0 tryboot'" \
    "$source_root/layer/atlas-ab-update.rootfs-overlay/usr/local/sbin/atlas-ab-update"; then
    echo "atlas-ab-update must pass tryboot through reboot(8), not systemctl" >&2
    exit 1
fi

quadlet_dir=$(mktemp -d)
trap 'rm -rf "$quadlet_dir"' EXIT
cp "$source_root"/layer/atlas-containers-core.rootfs-overlay/etc/containers/systemd/users/2000/* "$quadlet_dir/"
cp "$source_root"/layer/atlas-containers-geodata.rootfs-overlay/etc/containers/systemd/users/2000/* "$quadlet_dir/"

QUADLET_UNIT_DIRS="$quadlet_dir" /usr/libexec/podman/quadlet --user --dryrun >/dev/null

socket_mounts=$(rg -l 'podman\.sock:/var/run/docker\.sock' "$quadlet_dir" | wc -l)
[[ "$socket_mounts" -eq 1 ]]
rg -q 'geodata-consumer=router' "$quadlet_dir/atlas-router.container"
rg -q 'geodata-consumer=geocoder' "$quadlet_dir/atlas-geocoder.container"
rg -q -F 'EnvironmentFile=%h/.config/atlas/trusted-origins.env' \
    "$quadlet_dir/atlas-api.container"
rg -q -F 'Volume=/run/atlas-management:/run/atlas-management:ro' \
    "$quadlet_dir/atlas-api.container"
rg -q -F 'management-token:/run/secrets/atlas-management-token:ro' \
    "$quadlet_dir/atlas-api.container"
rg -q -F 'GroupAdd=keep-groups' "$quadlet_dir/atlas-api.container"
[[ "$(rg -l '/run/atlas-management' "$quadlet_dir" | wc -l)" -eq 1 ]]

management_unit="$source_root/layer/atlas-management.rootfs-overlay/usr/lib/systemd/system/atlas-management.service"
reset_unit="$source_root/layer/atlas-management.rootfs-overlay/usr/lib/systemd/system/atlas-factory-reset.service"
rg -q -F 'RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6 AF_NETLINK' "$management_unit"
rg -q -F 'ConditionPathExists=/persistent/atlas/system/factory-reset-pending' "$reset_unit"
rg -q -F 'Before=multi-user.target NetworkManager.service user@2000.service atlas-management.service' "$reset_unit"
rg -q -F 'SuccessAction=reboot' "$reset_unit"
rg -q -F 'factory-reset [--yes]' \
    "$source_root/layer/atlas-management.rootfs-overlay/usr/local/sbin/atlas-sys"
management_cli="$source_root/layer/atlas-management.rootfs-overlay/usr/local/sbin/atlas-sys"
rg -q -F 'ATLAS_SYS_SOCKET:-/run/atlas-management/api.sock' "$management_cli"
rg -q -F 'Authorization: Bearer $(token)' "$management_cli"
rg -q -F 'request GET /api/v1/update' "$management_cli"
rg -q -F 'request POST /api/v1/update --form "bundle=@$bundle"' "$management_cli"
rg -q -F 'request POST /api/v1/update/rollback' "$management_cli"
rg -q -F 'request POST /api/v1/factory-reset' "$management_cli"
rg -q -F 'Path=/etc/NetworkManager/system-connections' \
    "$source_root/layer/atlas-networking.rootfs-overlay/etc/rpi-image-gen/slot-shared.d/atlas-networkmanager.conf"
rg -q -F 'Path=/var/lib/NetworkManager' \
    "$source_root/layer/atlas-networking.rootfs-overlay/etc/rpi-image-gen/slot-shared.d/atlas-networkmanager.conf"

container_init="$source_root/layer/atlas-podman.rootfs-overlay/usr/local/libexec/atlas-container-init"
if rg -q '^BETTER_AUTH_URL=' "$container_init"; then
    echo "atlas-container-init must not generate a fixed Better Auth base URL" >&2
    exit 1
fi
rg -q -F "sed -i '/^BETTER_AUTH_URL=https:\\/\\/atlas\\.local$/d'" "$container_init"
rg -q -F "printf 'BETTER_AUTH_URL=%s\\n'" \
    "$source_root/layer/atlas-podman.rootfs-overlay/usr/local/libexec/atlas-auth-origins"

while IFS= read -r image; do
    rg -q -F "Image=$image" "$quadlet_dir"
done < "$source_root/images.txt"

echo "Atlas configuration validation passed."
