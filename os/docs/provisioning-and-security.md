# Provisioning and security

## Choosing the first-install artifact

### Production: provisioning archive

Use `atlas-rpi5-<version>-provisioning.tar.zst` with the Raspberry Pi IDP/
`rpi-sb-provisioner` flow. This applies `provisionmap.json`, creates the
encrypted device layout and expands persistent data to the remaining storage.

The archive is not a raw image. Do not decompress it and write it to an SD card
with `dd`, Raspberry Pi Imager, Etcher or similar tools. Follow the provisioner
version's own instructions and verify that it accepts the archive before making
any irreversible secure-boot/OTP choices.

### Development: whole-disk image

Use `atlas-rpi5-<version>-development.img.zst` for a simple development SD card:

```sh
sha256sum --check SHA256SUMS
zstd -d atlas-rpi5-<version>-development.img.zst
```

Flash the resulting `.img` with Raspberry Pi Imager's custom-image option,
Etcher, or a carefully targeted `dd`. Confirm the target device twice; flashing
overwrites it.

This route is unencrypted by design. It does not execute production IDP
provisioning and should not be represented as a production full-disk-encrypted
installation.

### Never flash the update bundle

`atlas-rpi5-<version>-update.tar.zst` is only for `atlas-ab-update` on an
existing device. It has no partition table or persistent data payload.

## Encryption boundary

With production provisioning:

- system and persistent data reside inside the device-sized LUKS2 data path;
- the persistent partition expands to available space not occupied by fixed
  boot/system requirements;
- Raspberry Pi firmware-readable boot partitions remain plaintext;
- dm-verity protects the immutable EROFS system contents from undetected
  modification;
- encryption keys and policy are established per device by the provisioner,
  not embedded as a universal secret in this repository.

Use this target-side diagnostic after provisioning:

```sh
sudo atlas-security-status
```

It reports the mounted root and persistent sources and whether the expected
`/dev/disk/by-slot/osdata_crypt` device exists. It is a diagnostic, not a
cryptographic attestation.

## Secure boot and OTPs

Atlas OS does not enable secure boot, provision secure-boot keys or burn OTPs.
The image leaves that decision to the end user because OTP changes can be
irreversible and couple future boot artifacts to customer-held keys.

If secure boot is required:

1. generate and protect customer-specific secure-boot keys;
2. review the exact `rpi-sb-provisioner` policy and hardware consequences;
3. test recovery and future boot-payload signing on disposable hardware;
4. only then apply the OTP/security policy to production devices.

Secure-boot keys are unrelated to the Atlas A/B update-signing keypair.

## Update-signing trust model

The repository tracks both `keys/atlas-update.key` and
`keys/atlas-update.pub`. This is intentional for a public project and mirrors
the convenience of public default TLS material: anyone can reproduce a
compatible development update.

Consequently, the default signature provides integrity checking but no
publisher authentication. Anyone with the repository can sign a bundle which a
default image accepts.

Customers needing authenticated updates must generate a private P-256 keypair,
embed only their public key, and keep the private half outside the repository:

```sh
openssl genpkey -algorithm EC \
  -pkeyopt ec_paramgen_curve:P-256 \
  -out customer-update.key
openssl pkey -in customer-update.key -pubout \
  -out customer-update.pub
```

Build with:

```sh
ATLAS_UPDATE_SIGNING_KEY=/secure/path/customer-update.key \
  ./rpi-image-gen build \
    -S /path/to/atlas/os \
    -c atlas.yaml \
    -- IGconf_abupdate_public_key=/secure/path/customer-update.pub
```

Back up the customer private key securely. Losing it prevents creation of
future updates accepted by deployed devices; leaking it lets an attacker sign
accepted updates.

## Initial account and SSH exposure

The interactive account is `atlas` with initial password `atlas` and sudo
access. SSH is disabled by default, but console login is available for initial
administration. Change the password before enabling SSH:

```sh
passwd
sudo atlas-ssh enable
```

SSH accepts password or public-key authentication for `atlas` only. Root login,
agent forwarding, TCP forwarding, X11 forwarding, tunnels, gateway ports and
user environment injection are disabled. SSH state is persistent, so an
enabled device remains enabled after an A/B update or reboot until explicitly
disabled.

Host keys and enablement state must be treated as persistent device identity.
Cloning a populated persistent filesystem can clone identity and secrets.

## TLS behavior

Caddy is the only public HTTP endpoint. Port 80 redirects to HTTPS. Caddy first
attempts normal ACME issuance where possible and can fall back to the appliance
local CA. Browsers and `curl` will reject the local-CA certificate until that
CA is explicitly trusted.

Using `curl -k` is appropriate for a reachability diagnostic, not for normal
authenticated administration. Provision the Atlas CA root into managed clients
or configure a public domain and certificate flow.

The Caddy data/config volumes are persistent. Protect their keys as device
secrets.

## Host hardening and accepted trade-offs

- The root filesystem is read-only EROFS and verified.
- Containers are rootless and generally drop all capabilities, enable
  `NoNewPrivileges`, and use read-only filesystems where practical.
- The web container receives only `NET_BIND_SERVICE` so rootless Caddy can bind
  ports 80/443. The host lowers `net.ipv4.ip_unprivileged_port_start` to 80 for
  all unprivileged processes, which is a deliberate host-wide trade-off.
- The management placeholder runs as host root but has systemd sandboxing and
  narrow writable paths. It does not yet expose an API.
- Serial console is enabled for recovery. Physical console access is therefore
  a privileged attack surface and must be controlled operationally.
- PCIe Gen 3 is enabled on Pi 5 even though it is not the conservative certified
  mode. Revert that setting if the attached NVMe/PCIe path is unstable.
