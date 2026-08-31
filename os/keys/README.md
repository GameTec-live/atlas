# Update signing keys

`atlas-update.key` and `atlas-update.pub` are the public-project defaults. The
image embeds the public half and CI signs A/B update bundles with the private
half.

Customers that require authenticated updates should generate their own keypair:

```sh
openssl genpkey -algorithm EC \
  -pkeyopt ec_paramgen_curve:P-256 \
  -out customer-update.key
openssl pkey -in customer-update.key -pubout -out customer-update.pub
```

Override `IGconf_abupdate_public_key` with the public-key path and set
`ATLAS_UPDATE_SIGNING_KEY` to the private-key path while building.

These keys sign Atlas OS update bundles only. Secure-boot customer keys and OTP
policy are separate and remain the responsibility of `rpi-sb-provisioner`.
