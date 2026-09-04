# AtlasOS Recovery

If your AtlasOS appliance is severely malfunctioning or not starting, theres the option to completly wipe and reprovision the device.

To do this, grab a USB stick or the "Recovery USB" from the original package.

If the "Recovery USB" is availalbe, skip the following step.

If the USB is brand new, either format it as Fat32 and extract the `atlas-recovery-usb.zip` to the root of the USB drive or use a tool like Rufus, Raspberry Pi Imager, dd or Balena Etcher to flash the `atlas-recovery-usb.img.zst`. The download comes in two variants: provision and development. Provision is the default installation that securely encrypts the flash storage and development is a simpler image that does not encrypt the disk and is primarly intended for development.

Once the USB drive is ready, insert it into the Appliance and power on the device. The device will start from the USB and reprovision the device (typically the fans will ramp up to full speed). DO NOT REMOVE THE USB!

Once done the Appliance will shut down and is ready for powering up again with a fresh installation. You should then, after a few minutes, be back at the setup wizard.