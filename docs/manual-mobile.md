# Atlas App Manual

The Atlas App is used by drivers and dispatchers during a shift. It displays live locations, manages jobs, provides navigation and creates the logbook entry at the end of the shift. The app can be used on an Android phone and, while the phone is connected to a compatible car, through Android Auto.

## Before the First Shift

Before using the app, make sure that:

- the Atlas App is installed on the phone
- an administrator has created a user account for you
- the Atlas server is fully configured and has map data installed
- the phone can reach the Atlas server, either through the same network or through the configured remote access method.

The server address can be set automatically by scanning the pairing QR code shown during the Atlas setup wizard. It can also be entered manually on the login screen.

## Connect and Log In

1. Open the Atlas App.
2. Check the server address shown at the bottom of the login screen. To change it, press the address, enter the complete HTTP or HTTPS address and press **Save**.
3. Enter your username and password.
4. Press **Login**.

The app securely saves the session and normally signs you back in when it is opened again.

> [!NOTE]
> Changing the server address signs out the current user and clears the username and password fields.

If the app cannot connect, check the server address and the phone's network connection. The server address must be reachable from the phone; an address that only works inside the company network will not work when the phone is outside that network.

## Start a Shift

After logging in, select the role for the current shift:

- **Driver** provides the assigned-job queue and the controls for performing jobs.
- **Dispatcher** provides the same job controls and also allows you to create jobs, assign unassigned jobs and monitor active drivers.

The number of dispatcher places is limited by the system settings. If all places are occupied, select **Driver** or wait until a dispatcher spot becomes available.

The selected role remains active until the end of the day.

Atlas requires location access during an active shift. When Android asks for permission, allow precise or approximate location. Precise location is recommended for accurate navigation and live map updates. Also allow notifications so that new jobs are visible while the app is in the background.

While a shift is active, Android displays an **Atlas shift active** notification. During this time Atlas stays connected, receives jobs and shares the live location. Closing the app does not end the shift. Use **Log out** and complete the shift summary when you are finished.

## Main Screen

The main screen consists of a map and several controls.

- **Map**
  
    The map shows your position, the current route and other active users. Drag or zoom the map to inspect another area. After moving the map, press the location button to return to your current position.
  
    Other users are labelled by name and coloured according to their current status:
  
  - green: free
  - blue: on the way to a pickup
  - orange: occupied
  - grey: away

- **Navigation**
  
    After a job is started, the navigation panel shows whether you are driving to the pickup or destination. It displays the next instruction, remaining distance and estimated time. Press the distance-and-time row in portrait mode to show or hide all remaining route steps.

- **Jobs**
  
    The job panel shows the current job and the next assigned job. Press it to expand or collapse the complete queue. Pull down on the panel to refresh it. In landscape mode the panel remains compact.
  
    The pencil beside the current destination opens address search. Enter an address and select one of the suggestions to update the destination. Merely typing an address without selecting a suggestion does not save it.

- **Profile**
  
    Press the profile button in the top-right corner to see the signed-in user and selected role or to end the shift with **Log out**.

## Driver Workflow

### Receive a Job

When a job is assigned, a banner appears for 10 seconds and shows the pickup, destination and optional note. The job is already assigned and is automatically added to the queue. No action is required to keep it.

Press **Decline** only if the job must be cancelled. If Atlas is in the background, the Android notification contains the same job details and a **Decline** action.

### Start the Next Job

When there is no current job and at least one job is waiting, press **Next Job**. Atlas starts the first job in the queue and calculates a route from the current location to the pickup.

Atlas needs the vehicle's starting odometer reading once per shift. If a connected vehicle supplies mileage data, it is recorded automatically. Otherwise, the app asks for the odometer when the first job is started. Enter a non-negative value and press **Continue**.

### Pick Up the Passenger

Drive to the pickup using the route on the map. When the passenger is in the vehicle, press **Person collected**. Atlas changes the vehicle status to occupied and calculates the route to the destination.

If the job has no destination, address search opens automatically. Enter the destination and select a suggestion. The destination is optional, so you can close the search bar if you do not want to enter one. The destination can also be changed at any time with the pencil in the current-job panel.

> [!NOTE]
> Always add a destination when it is known. Without one, Atlas cannot provide destination navigation and route-based planning for this and following jobs.

### Finish or Cancel the Job

At the destination, press **Job finished**. The confirmation shows the total distance, passenger distance and calculated price when the required mileage and pricing data are available. Review the information and press **Yes** to complete the job. Atlas then refreshes the queue so that the next waiting job is ready to start.

To stop a job without completing it, press the **X** button beside the job controls and confirm the cancellation. Only cancel a job when you can not complete it or if you do not have time for it. If the job is canceled it will be unassigned and the dispatcher has to assign it again.

## Dispatcher Workflow

Dispatchers use the same main map, job queue and job controls described above. Two additional buttons are shown near the job controls:

- the unassigned-jobs button, with a badge showing the current number of unassigned jobs
- the **+** button for creating a new job

### Create a New Job

1. Press the **+** button.
2. Press **From**, enter the pickup address and select a suggestion. A pickup is required.
3. If known, press **To**, enter the destination and select a suggestion.
4. Press the date and time beside the pickup to change when the job is due. The default is the current time.
5. Optionally enter a note for the driver. Notes can contain up to 100 characters.

When both addresses are selected, Atlas draws the calculated route on the map. On a phone in portrait mode, drag the bottom sheet upwards to see the complete driver list.

**Recommended drivers** are ordered by the candidate calculation. **All drivers** lists the remaining active drivers so that the recommendation can be overridden. Press a driver and confirm **Create** to create and assign the job immediately.

To save the job without a driver, press **Create unassigned**, select its due date and time, and confirm **Create**. The job then appears in the unassigned-jobs list.

### Manage Unassigned Jobs

Press the unassigned-jobs button to open the list. Jobs are grouped by due date and show their pickup, destination, due time and note. Pull down to refresh the list.

- Press a job card to open the assignment screen.
- Press the bin button and confirm **Delete** to permanently delete an unassigned job.
- Press the back arrow to return to the dispatcher map.

On the assignment screen, the pickup cannot be changed. The destination and due date can be edited. Press **Save changes** to keep edits without assigning the job, or select a recommended driver or an entry under **All drivers** and confirm **Assign**. Unsaved destination and due-date changes are saved automatically when the assignment is confirmed.

> [!CAUTION]
> Deleting an unassigned job cannot be undone.

When a new unassigned job arrives, Atlas shows a 10-second banner. Press **Assign Now** to open it directly. If the banner disappears, the job remains available in the unassigned-jobs list.

## Connect a Vehicle and Android Auto

Connect the phone to the vehicle using Android Auto. Atlas on Android Auto uses the login, role and active shift from the phone. If the car display says **Continue on your phone**, open the phone app, log in and select a role.

The first connection may ask for access to connected Bluetooth devices, fuel level and mileage. Park the vehicle, press **Allow vehicle data** and approve the requested permissions. Atlas still works if some vehicle data is unavailable, but automatic fuel or odometer values may be missing. The phone will ask for a manual odometer reading when one is required.

### Pair a New Vehicle

Every vehicle must first be created in the Fleet page of the Web-UI. The first time an unregistered car is connected, an administrator signed in to the phone app receives a **Pair this car** dialog.

Select the matching vehicle name and license plate. Atlas saves the car's fingerprint so that it is recognised automatically on later connections. If the list cannot be loaded, press **Retry**. A non-administrator cannot pair an unknown vehicle.

> [!IMPORTANT]
> Check the vehicle name and license plate carefully. Selecting the wrong vehicle associates future telemetry and logbook data with the wrong fleet record.

## End a Shift and Log Out

1. Open the profile from the main screen and press **Log out**.
2. If Atlas cannot read the ending odometer from the connected vehicle, enter it manually. It must not be lower than the starting value.
3. Review the mileage, selected vehicle and shift time. If no vehicle was detected automatically, select the vehicle used for the shift.
4. Enter the total cash revenue for the shift. Enter `0` if there was no cash revenue.
5. Enable **I confirm this data is correct**.
6. Press **Log off**.

Atlas submits the shift as a new logbook entry and then signs the user out. Press the back arrow before submission to return to the active shift without logging out.

> [!IMPORTANT]
> Do not close the app instead of logging out. A shift remains active until the shift summary is successfully submitted.

## Common Problems

- **The app cannot connect to the server**
  
  Check the complete server address, the phone's network connection and whether remote access is required. If the address was changed, log in again.

- **The map, address search or route does not load**
  
  Check the connection to the server and ask an administrator to confirm that map data has finished downloading and processing.

- **The main screen does not open after denying location access**
  
  Press **Allow location**. If Android no longer shows the permission dialog, press **Open settings**, enable location permission for Atlas and return to the app.

- **New-job notifications do not appear outside the app**
  
  Enable notifications for Atlas in Android settings. Jobs are still added to the queue or unassigned list and can be found by opening the app and refreshing.

- **Dispatcher cannot be selected**
  
  All configured dispatcher places are currently occupied. Wait for a dispatcher to log out or select the driver role.

- **A job or logbook action fails**
  
  Keep the app open, confirm that the server is reachable and try again. Atlas only leaves the shift after the logbook has been submitted successfully.
