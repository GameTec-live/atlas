# Atlas

<p align="center">
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="50%"><path d="M17.36,2.64L15.95,4.06C17.26,5.37 18,7.14 18,9A7,7 0 0,1 11,16C9.15,16 7.37,15.26 6.06,13.95L4.64,15.36C6.08,16.8 7.97,17.71 10,17.93V20H6V22H16V20H12V17.94C16.55,17.43 20,13.58 20,9C20,6.62 19.05,4.33 17.36,2.64M11,3.5A5.5,5.5 0 0,0 5.5,9A5.5,5.5 0 0,0 11,14.5A5.5,5.5 0 0,0 16.5,9A5.5,5.5 0 0,0 11,3.5M11,5.5C12.94,5.5 14.5,7.07 14.5,9A3.5,3.5 0 0,1 11,12.5A3.5,3.5 0 0,1 7.5,9A3.5,3.5 0 0,1 11,5.5Z" /></svg>
</p>


## What is Atlas?

Atlas is a Taxi fleet management platform.

A lot of smaller taxi companies still rely on phone calls, handwritten logbooks and manual ride assignment.
Atlas aims to provide a modern, centralized solution tailored towards smaller taxi companies while remaining scalable and flexible.

Atlas is designed to be deployed in an appliance like fashion on a Raspberry Pi 5 or Compute Module 5. Other deployments like docker are possible too but may require more setup or have limited functionality.

Atlas consists of three core components:

- Mobile application with android auto support
- Web application for administration and setup
- API service and backend to tie everything together

---

## Getting started

There are multiple options for getting started with Atlas.

### Purchase a device

The simplest option is to aquire a Atlas appliance from TaxiGerhard. This not only gives you the complete, ready to go, package of encrypted Device, Manual, Recovery Media and Powersupply but also allows you to aquire various support packages when ordering.

### DIY device

Another option is to host Atlas on a dedicated appliance. AtlasOS is the custom managed OS that runs on Atlas appliances. Atlas applianes are RaspberryPi 5 based. It is highly recommended to use a Raspberry Pi 5 with 8GB of RAM or more. A 4GB Pi may suffice depending on usage but this is no guarantee.

A DIY Atlas device can either be provisioned encrypted or unencrypted.

#### Unencrypted

This is probably the simplest way to get up and running with a custom Atlas applliance.

Head over to the releases, download the latest AtlasOS image and flash the unencrpyted development image to the SSD or MicroSD of a Raspberry Pi using Raspberry Pi Imager or similar.

While this is simple and supports all features and functions, the storage media is unencrpyted and may be vulnerable to theft or other influences. This may not meet your required security standards.

#### Encrypted

If you choose to create a custom appliance and want this appliance to use encrypted media, you will need to [restore from USB](./docs/manual-recovery.md) or use the [Raspberry Pi provisioning tooling](https://github.com/raspberrypi/rpi-sb-provisioner).

To restore from USB ensure your Raspberry Pi is configured to boot from USB and then follow the instructions in the manual. In short: Flash or copy the recovery files to your USB drive, plug it into the Raspberry Pi and then power the Pi and wait until it shuts down again. Unplug the USB, apply power, enjoy.

### Docker or Podman

AtlasOS uses Podman to run Atlas. Podman or Docker can be used to run the AtlasOS containers on any host.

Running Atlas manually in Docker or Podman means that system management features are not available via the UI and to be manually performed.

Atlas container images are capable of running fully rootless and built to the highest security standards.

For testing and development, a docker compose is available in the repository root.

### Bare Metal

Technically using bare metal is possible but highly discouraged. You are on your own.

## Usage

To start using Atlas, after setting up, open a webbrowser and navigate to the management UI.

This can be done either by navigating to [atlas.local](https://atlas.local) or your configured domain or IP Address.

> [!NOTE]
> Atlas uses a self signed certificate on the local network. This certificate will show as untrusted. This is expected.

> [!TIP]
> The Atlas Appliances also offer a local console. If you prefer you can connect a Monitor, Mouse and Keyboard and use the Appliance locally.

Follow the setup wizard and complete the configuration. See the [Manual](./docs/manual-web.md) for details.

Once done, the only thing left to do is to pair the mobile devices. For distributing Atlas to end devices it is recommended to make use of Androids fleet management tooling using tools like Fleetdm.

The application can be downloaded from the [PlayStore](https://play.google.com/store/apps/details?id=org.gtlv.atlas) and the server url is configured at the bottom of the login screen. This URL must be correct for Atlas to function.

## Development

Atlas consists of various components. All components are managed in a turborepo monorepo.

The main components are the API, WebUI and Mobile application.

The API uses ElysiaJS, DrizzleORM and Better-Auth. Bun is used as the package manager, runtime and bundler.

The WebUI uses Vite, React, shadcnUI and Better-Auth as well as various libraries from the TanStack ecosystem. Vite is the bundler, bun is once again package manager.

The mobile app is a native Android application and developed using Android Studio.

`bun install`, `bun run dev` and `bun run build` should get you up and running in almost all projects.

Some assistive services are written in GO and theres a yaak workspace in the root of this repository to help test the API endpoints.

Visual Studio Code is recommended for developing the frontend as the extension support allows for helpful additions like embedding translation string previews in the source code.

GitHub Actions is used for auotmatically building all parts of the system including containers both on push as well as on PR.