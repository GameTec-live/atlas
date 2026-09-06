# How Atlas Recommends Drivers

When a dispatcher creates or assigns a job, Atlas compares the available drivers and displays them in a recommended order. The first driver is the best match according to the current schedule and route information.

> [!NOTE]
> The recommendation is a planning aid, not a restriction. A dispatcher can choose another available driver when local knowledge or an operational need makes that the better choice.

## What Atlas Considers

Atlas uses the information that affects whether a driver can complete the new pickup without disrupting existing work:

- the driver's current location and availability
- the progress of the driver's active job, if any
- already assigned jobs and their pickup times
- the pickup, destination and due time of the new job
- the driving time and distance between the required stops

Only drivers with a current location are considered. This prevents Atlas from presenting an arrival estimate based on an unknown starting point.

## How the Proposed Schedule Is Built

An active job always stays first because the driver has already started it. Jobs that have not started are ordered by their due time. Atlas inserts the new job into that queue at the appropriate position instead of simply placing it at the end.

Atlas then simulates the complete route. If a driver reaches a preorder early, the simulation waits until its scheduled pickup time before continuing. It also checks jobs after the new one to make sure an apparently good assignment does not cause a later customer to be picked up late.

## Ranking Priorities

Atlas applies the following priorities in order:

1. Protect already assigned jobs. A driver who keeps later pickups on time is preferred over one who would make them late.
2. If every option delays later work, prefer the driver with the smallest worst delay.
3. Prefer a driver who can reach the new pickup on time.
4. If every driver would be late, prefer the earliest possible pickup.
5. When the new pickup is on time and later work is safe, prefer the shortest final empty drive to the customer.
6. If the final approaches are equal, prefer the earlier arrival.

For an immediate job with no affected future commitments, this usually means recommending the driver who can reach the customer first. For a preorder, Atlas first protects later commitments and then minimizes the empty approach among drivers who can arrive on time.

## Why a Driver May Not Appear

A driver is omitted when Atlas cannot make a reliable prediction. Common reasons include:

- the driver is marked as away
- no current location is available
- the reported driver status and active job do not agree
- a required destination is missing
- the routing service cannot find a route

A destination is particularly important when another job follows. Without it, Atlas does not know where the driver will be when the next journey begins. A missing destination may be acceptable only for the final job in a schedule.

## Reading the Recommendation

The candidate list places the recommended driver first. The displayed details can include:

- when the driver is expected to reach the pickup
- when the pickup can actually begin after any preorder waiting
- the distance and driving time to the new customer
- whether accepting the job is expected to delay later pickups

Atlas also explains the factor that decided the order. This allows the dispatcher to see whether a recommendation is based on protecting a later booking, avoiding lateness or reducing the final empty approach.

## Getting Useful Recommendations

For the most reliable result:

- enter both the pickup and destination whenever they are known
- set the correct due date and time, especially for preorders
- keep driver location access enabled during the shift
- make sure active jobs are started, collected and completed in the app at the correct time
- investigate missing candidates instead of assuming that an empty list means no drivers exist

## Current Limitations

The recommendation focuses on predictable schedules and routes. It does not currently balance workload or fairness between drivers, preserve geographic fleet coverage, consider fuel level, add time for loading or payment, or account for an unexpected destination change. Traffic is considered only to the extent supported by the configured routing service.

Atlas evaluates the predicted schedule after adding the new job. If a driver's existing schedule is already late, that existing delay can affect the recommendation even when the new job adds little extra delay.
