# Atlas Web-UI Manual

The Atlas Web-UI consists of various pages that offer the various functions of the system. Every page will be detailed below.

## Dashboard

The dashboard is the default view right after logging in. It gives you a overview over the current state of things and consists of various widgets.

- Drivers and status
    
    This widget displays all currently active drivers and their status as well as current job.
- Next due for maintenance

    This widget displays the cars that are next due for maintenance orderd by urgency.
- Map

    This is a small map widget showing live position updates.
- KM driven

    This widget shows a statistic calculated from the logbook entries of the km driven in the last 5 days.
- Unassigned jobs

    This widget gives a overview over all unassigned jobs. Clicking on a job opens the assignment screen. Deleting a job deletes it forever. Clicking "new job" opens the new job screen.

## Map

This page consists of a large map. Every currently active driver gets displayed with their current status and location. Hovering over a driver opens a tooltip with a few more details.
A new job button is present in the bottom right corner to quickly create a new job.

## Jobs

This page lists all jobs that have ever been processed or are assigned and allows easy inspection by clicking on a job. A export can be downloaded by clicking on the "download" icon in the top right.

The unassigned jobs sidebar shows all jobs that still need to be assigned to a driver. Only unassigned jobs can be deleted. Clicking on a unassigned job opens the assign job page and shows more details. Again, a export option can be found in the top right as well as a "hidden page" for easily sharing the unassigned jobs list.

### New / Assign Job

This page can be accessed either by selecting a unassigned job or by pressing "new job".

If this page was accessed via a unassigned job, the "From" field and maybe the "To" field will already be populated. The "From" field can not be edited on unassigned jobs.

This page allows for entering a starting location and a optional destination as well as a due date.
By default the due date is now.
A note can also be added for additional information for the driver.

Once both a "From" and a "To" are entered, a route gets drawn indicating the route the routing server has found for this job.

> [!WARNING] 
> It is highly recommended to always enter a "From" and a "To". Not entering a "To" is possible but breaks route planning and time prediction / candidate calculation for that and any following jobs until a "To" is set or the job is completed.

If the page was opened via a unassigned job, you may save changes and leave the page at any time. If the page was opened by the new job button, the job must either be assigned or created as unassigned.

The right hand side features driver candidates. Based on every drivers location and job backlog, the system calculates the driver best fit for the job. The ideal candidate is listed at the top with a estimated arrival and other details like how the ranking was achived.

Should the user want to override the system because the candidate is not listed or for any other reason, every possible driver is listed under "All drivers".

Assigning a job nearly instantly sends out a notification to the corresponding driver and updates the records.

## Fleet

This page allows you to manage your fleet of vehicles. Every vehicle should be registered here.

Odometer and Fuel level do not need to be kept up to date as the system updates these values on a best effort basis.

The fleet management system provides a simple way to keep track of maintenance records. Simply press the wrench icon to create a new record. Click on a "Last maintenance" or "Next maintenance" record to bring up the history.

The fingerprint is used to identify the vehicle with the mobile application. This does not have to be set manually. The first time a admin user signs into the mobile application and connects a new vehicle, a popup is triggered to pair the new vehicles fingerprint to a vehicle in the fleet database.

> [!TIP]
> Grap a phone and sign in as a admin user. Go to every vehicle after creating them in the UI, plug in, establish a android auto connection and pair them to the fingerprint. Easy and quick.

## Logbook

The logbok page allows you to view the logbook of any driver and download the logbook as a csv.

A logbook entry can not be deleted, only invalidated. A invalidated entry is greyed out and excluded from calculations.

A logbook entry gets created after every shift in the mobile application.

## Settings

### Shortnames

To make address entry easier for repeating customers, Atlas offers address shortnames.

Simply press into the "New shortname" cell and enter the shortname, then enter a corresponding address. Address search now resolves the shortname to the real address.

To delete a shortname, simply click into the cell and delete the shortname.

### General

This page consists of core system settings, most of which have already been configured in the setup wizard.

- Users

    Create accounts for additional admins and your drivers and dispatchers here. They will authenticate using username and password.
- General settings

    Configure behaviour and general settings here. Customize the logo shown on mobile devices, set the maximum number of dispatchers at a time and the price per kilometre.

    The default language is only used for routing and navigation and only gets used when no other language is supplied. Usually the language of the end device gets used (phone, web-ui).
- System

    If Atlas has been deployed via docker or not on AtlasOS, this card only shows some API version information.

    This card shows detailed information and offers system management options.

    Detailed information allows you to see various details like exact version numbers and system health.

    Manage connections allows for managing the network connection and remote access options.
    
    - Network connection

        Are you running a advanced deployment? Do you have special networking requirements? Configure static addresses and other settings for the network interface here.

        Most users do not have to touch this menu.

    - Remote access

        Atlas is designed to and capable of fully operating offline. You may however want Atlas to be available outside of your network.

        AtlasOS offers various settings to facilitate said remote access.

        If you want to use Tailscale or Cloudflare Tunnels to expose atlas, AtlasOS automatically spins up these services given a valid authentication credential.

        > [!NOTE]  
        > For cloudflare tunnels you need to point the tunnel at https://localhost

        If you choose to simply enable portforwarding on your router or have multiple remote addresses pointing at your deployment, do not forget to add said addresses to the additional remote origins or otherwise authentication may fail.
    
    Last but not least theres the option to change the systems timezone (which affects time scheduled actions like driver role reset, etc) and the option power off, restart or factory reset the device.
    
- Map data

    Download a dataset (or multiple) that will be used for routing, search and maps. Note that downloading and processing can take quite a while. You are free to complete the setup, leave the page or come back later. The download will process in the background, but Atlas will not be fully functional until atleast one dataset has been downloaded and initialized.