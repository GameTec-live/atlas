# Atlas Getting-Started

1. Unbox your Atlas appliance

    Box contents:
    
    - Atlas appliance
    - Power supply
    - Recovery USB

    Keep the recovery USB in a safe place. You will need it to fully reprovision your device if things go wrong.

2. Plug in power and ethernet
   
   Connect your Atlas appliance to the network and power. 

3. Visit [https://atlas.local](https://atlas.local) in a web-browser to start the setup-wizard

    Aternatively the setup-wizard can be started by opening the devices IP-Address in a web-browser or by connecting a monitor, mouse and keyboard to the appliance and pressing `ENTER` at the console.

## The Setup Wizard

The setup wizard will guide you through setting up your Atlas appliance and Atlas system in 10 easy steps. These steps are detailed below.

### Welcome

Congratulations on successfully aquiring and connecting to your Atlas device. Great things are ahead.

### Preferences

Choose your preferred language and theme. These settings are stored in your browser and can be adjusted any time in the top navigation bar.

### Create an admin account

To administer and secure Atlas, creating an admin account is required.

Input an email (don't worry, you will not receive any e-mails), pick a username and name and enter a secure password.

After clicking create, your admin account is ready.

### Timezone

Some things, like the role expiry (When a dispatcher becomes available again, ...), depend on time. Set the correct local timezone here.

### Network connection

Are you running a advanced deployment? Do you have special networking requirements? Configure static addresses and other settings for the network interface here.

Most users do not have to touch this menu.

### Remote access

Atlas is designed to and capable of fully operating offline. You may however want Atlas to be available outside of your network.

AtlasOS offers various settings to facilitate said remote access.

If you want to use Tailscale or Cloudflare Tunnels to expose atlas, AtlasOS automatically spins up these services given a valid authentication credential.

> [!NOTE]  
> For cloudflare tunnels you need to point the tunnel at https://localhost

If you choose to simply enable portforwarding on your router or have multiple remote addresses pointing at your deployment, do not forget to add said addresses to the additional remote origins or otherwise authentication may fail.

### General settings

Configure behaviour and general settings here. Customize the logo shown on mobile devices, set the maximum number of dispatchers at a time and the price per kilometre.

The default language is only used for routing and navigation and only gets used when no other language is supplied. Usually the language of the end device gets used (phone, web-ui).

### Connect the app

Download the Atlas App to all mobile devices that drivers and dispatchers will use. Either manually enter the appliances URl on the login screen or scan the pairing QR-Code to set it automatically.

### Users

Create accounts for additional admins and your drivers and dispatchers here. They will authenticate using username and password.

### Map data

Download a dataset (or multiple) that will be used for routing, search and maps. Note that downloading and processing can take quite a while. You are free to complete the setup, leave the page or come back later. The download will process in the background, but Atlas will not be fully functional until atleast one dataset has been downloaded and initialized.

This is it! Your Atlas deployment is ready to use. For more details on usuage, find them in the [manual-web](./manual-web.md) and [manual-mobile](./manual-mobile.md).