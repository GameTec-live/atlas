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

while IFS= read -r image; do
    rg -q -F "Image=$image" "$quadlet_dir"
done < "$source_root/images.txt"

echo "Atlas configuration validation passed."
