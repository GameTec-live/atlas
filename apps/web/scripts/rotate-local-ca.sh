#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
pki_dir="${script_dir}/../pki"
mkdir -p "$pki_dir"
pki_dir=$(CDPATH= cd -- "$pki_dir" && pwd)
archive_dir="${pki_dir}/archive/$(date -u +%Y%m%dT%H%M%SZ)"
days=${ATLAS_ROOT_CA_DAYS:-36500}

command -v openssl >/dev/null 2>&1 || {
	echo "openssl is required" >&2
	exit 1
}

case "$days" in
	''|*[!0-9]*)
		echo "ATLAS_ROOT_CA_DAYS must be a positive integer" >&2
		exit 1
		;;
esac

if [ "$days" -le 0 ]; then
	echo "ATLAS_ROOT_CA_DAYS must be a positive integer" >&2
	exit 1
fi

umask 077
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

openssl ecparam -name secp384r1 -genkey -noout \
	-out "${tmp_dir}/atlas-local-root.key"

openssl req -x509 -new -sha384 \
	-key "${tmp_dir}/atlas-local-root.key" \
	-out "${tmp_dir}/atlas-local-root.crt" \
	-days "$days" \
	-subj "/CN=Atlas Local CA Root/O=Atlas Development" \
	-addext "basicConstraints=critical,CA:TRUE,pathlen:1" \
	-addext "keyUsage=critical,keyCertSign,cRLSign" \
	-addext "subjectKeyIdentifier=hash" \
	-addext "authorityKeyIdentifier=keyid:always"

openssl x509 -in "${tmp_dir}/atlas-local-root.crt" -noout -text >/dev/null
openssl pkey -in "${tmp_dir}/atlas-local-root.key" -check -noout >/dev/null

if [ -f "${pki_dir}/atlas-local-root.crt" ] || [ -f "${pki_dir}/atlas-local-root.key" ]; then
	mkdir -p "$archive_dir"
	[ ! -f "${pki_dir}/atlas-local-root.crt" ] || cp "${pki_dir}/atlas-local-root.crt" "$archive_dir/"
	[ ! -f "${pki_dir}/atlas-local-root.key" ] || cp "${pki_dir}/atlas-local-root.key" "$archive_dir/"
	chmod 700 "$archive_dir"
fi

mv "${tmp_dir}/atlas-local-root.crt" "${pki_dir}/atlas-local-root.crt"
mv "${tmp_dir}/atlas-local-root.key" "${pki_dir}/atlas-local-root.key"
chmod 644 "${pki_dir}/atlas-local-root.crt"
chmod 600 "${pki_dir}/atlas-local-root.key"

echo "Generated Atlas local CA root (valid for ${days} days)."
if [ -d "$archive_dir" ]; then
	echo "Previous root archived at ${archive_dir}."
fi
echo "Rebuild and redeploy the web image, remove /data/caddy/pki/authorities/local from Caddy's persisted data, and distribute the new root certificate."
