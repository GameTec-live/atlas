# Job Candidate Selection

The API exposes two ways to calculate a ranked list of drivers:

- `GET /jobs/:id/candidates` uses an existing unassigned database job.
- `POST /jobs/candidates` uses an ad-hoc job supplied in the request body.

The first element of the returned array is the recommended candidate.

## Endpoint variants

### Existing database job

```http
GET /jobs/:id/candidates
```

The job ID must be a UUID. The endpoint loads the job and rejects the request when the job:

- Does not exist (`404`).
- Is already assigned (`409`).
- Is already completed (`409`).

### Ad-hoc job

```http
POST /jobs/candidates
Content-Type: application/json

{
  "from": [48.2082, 16.3738],
  "to": [48.1947, 16.3122],
  "dueDate": "2026-08-05T12:00:00.000Z"
}
```

The ad-hoc request accepts the routing-relevant part of a job shape:

| Field | Required | Meaning |
| --- | --- | --- |
| `from` | Yes | Pickup as `[latitude, longitude]`. |
| `to` | No | Destination as `[latitude, longitude]`. |
| `dueDate` | No | Requested pickup time. Defaults to the current time. |

The supplied job is never written to the database. Omitting `dueDate` makes it an immediate job.

The POST endpoint returns `422` when the supplied shape or coordinates are invalid.
Both endpoint variants require authentication and return the identical candidate response schema.

## Job state semantics

### Assigned, not started

```text
assignedDriverId !== null
startedAt === null
completedAt === null
```

The job is in the driver's backlog. The driver has not started driving to this pickup yet. A driver can have several such jobs.

Unstarted jobs are not an immutable route prefix. They are ordered by `dueDate`, so a newly assigned job can be inserted before or after them.

### Started, driving to pickup

```text
startedAt !== null
completedAt === null
telemetry.state === "onTheWay"
```

`startedAt` is set as soon as the driver starts driving toward the pickup. This job is now active and remains fixed at the front of the schedule.

### Started, passenger picked up

```text
startedAt !== null
completedAt === null
telemetry.state === "occupied"
```

There is no separate pickup timestamp. The change from `onTheWay` to `occupied` in realtime telemetry is the indication that pickup occurred.

### Completed

```text
completedAt !== null
```

The job no longer affects candidate calculation.

## Scheduling model

Each driver's schedule has two parts:

1. At most one active job, identified by `startedAt`.
2. Zero or more unstarted backlog jobs, ordered by `dueDate`.

The active job cannot be moved because the driver has already begun serving it.
The target job is inserted into the unstarted backlog according to its due date.

If inconsistent data contains more than one unfinished started job, the job with the earliest `startedAt` is treated as active.
Normal operation should prevent this situation; the ordering only makes candidate calculation deterministic when it occurs.

Existing backlog jobs with the same due date are placed before the new job.

The comparison therefore uses:

```text
existing dueDate <= target dueDate  -> before target
existing dueDate > target dueDate   -> after target
```

This respects existing assignments when deadlines are equal.

### Example: inserting a sooner job

Suppose a free driver already has this backlog:

```text
11:00 existing preorder
```

A new job requests pickup at `10:10`. The schedule becomes:

```text
10:10 new job
11:00 existing preorder
```

The existing job is not blindly processed first merely because it was assigned first.
The algorithm routes through the new pickup and destination, then checks whether the driver can still reach the existing `11:00` pickup on time.

If travel takes only 15 minutes after the new pickup, there is still plenty of time and the driver remains a good candidate.
If the existing pickup would be late, that predicted disruption is returned and affects ranking.

## Endpoint processing

The request flow is:

```text
GET /jobs/:id/candidates                 POST /jobs/candidates
    |                                        |
Load and validate target job             Validate and normalize body
    |                                        |
    +-- missing ----------------> 404         +-- invalid shape -----> 422
    +-- assigned ---------------> 409         |
    +-- completed --------------> 409         |
    |                                        |
    +-------------------- CandidateTarget <--+
                             |
                    Shared calculation path
    |
Read all drivers from trackCache
    |
    +-- no tracked drivers --------------> []
    |
Load unfinished jobs and tracked users concurrently
    |
Group those jobs by driver ID
    |
Build and route a proposed schedule for each driver
    |
Remove candidates whose schedule cannot be predicted
    |
Rank remaining candidates
    |
Return best candidate first
```

The endpoint captures `now` once. Every driver is evaluated relative to the same timestamp.

Candidate routes are calculated concurrently with `Promise.all`.
Routing is the most expensive part of the operation, so running independent requests in parallel avoids multiplying endpoint latency by the number of drivers.

## Data sources

### Realtime cache

`trackCache` supplies:

- Driver ID through the map key.
- Current latitude and longitude.
- Current state: `free`, `onTheWay`, `occupied`, or `away`.

Only tracked drivers are considered. Without a current position, a route and ETA would not be reliable.

Telemetry is authoritative for the phase of a started job:

- `onTheWay` means its pickup still needs to be visited.
- `occupied` means its pickup has already occurred.

### Jobs database

The database supplies:

- The target job.
- Every unfinished job assigned to a tracked driver.
- `startedAt`, which identifies the active job.
- `completedAt`, which removes completed jobs from the schedule.
- `dueDate`, which orders the unstarted backlog.
- Pickup and optional destination coordinates.

### Users database

The shared calculation also loads `user.id` and `user.name` for all tracked driver IDs.

## Driver eligibility

### Away

An `away` driver is always excluded. This is an explicit declaration that the driver is unavailable, regardless of database state.

### Free

A driver without a started job must report `free` to be considered.

The driver may still have unstarted backlog jobs. Those jobs do not make the driver currently `onTheWay`; they are inserted into the proposed schedule by due date.

### On the way or occupied

A driver reporting `onTheWay` or `occupied` must have an unfinished job with a non-null `startedAt`.
Otherwise the remaining itinerary cannot be reconstructed reliably, so the driver is excluded.

Likewise, if a started job exists but telemetry reports `free`, the data sources disagree about whether the job is active. The conservative choice is to exclude that candidate until state becomes consistent.

## Destination requirements

Job destinations are optional, but a destination becomes necessary whenever the algorithm must predict a later location.

A candidate is excluded when:

- The active job has no destination, because another job must follow it.
- A job before the target has no destination.
- The target has no destination and the driver has later backlog jobs.
- A later backlog job other than the final one has no destination.

The final job in the simulated schedule does not require a destination because only its pickup time must be predicted; nothing is scheduled after it.

This rule avoids inventing a future driver location. A missing destination is acceptable when it cannot affect another pickup, but not when the route must continue afterward.

## Route construction

Job points use this tuple convention:

```text
[latitude, longitude]
```

### Active job while on the way

When `startedAt` exists and telemetry is `onTheWay`:

```text
current position
    -> active job.from
    -> active job.to
    -> due-date-ordered backlog and target
```

The active pickup remains in the route because the customer is not in the car yet.

### Active occupied job

When `startedAt` exists and telemetry is `occupied`:

```text
current position
    -> active job.to
    -> due-date-ordered backlog and target
```

The active pickup is omitted because telemetry indicates it already happened.

### No active job

For a free driver:

```text
current position
    -> all unstarted jobs and target ordered by dueDate
```

### Complete proposed itinerary

The routing request continues beyond the target job when later backlog jobs exist:

```text
[active job, if any]
    -> jobs due before target
    -> target pickup
    -> target destination
    -> jobs due after target
```

Continuing after the target is what allows the endpoint to verify that accepting the new assignment leaves enough time for scheduled future pickups.

## Routing-engine integration

`requestRoute` accepts an ordered list of points and sends one multi-point request to the configured routing engine.

One routing request is made per eligible driver. A schema-valid no-route result excludes that driver. 
A network failure or schema-invalid response fails the endpoint rather than silently turning an infrastructure problem into an empty candidate list.

## Time simulation

The routing engine returns one leg for every pair of consecutive points. After routing, the algorithm walks those legs in schedule order.

For each leg:

```text
arrivalTime = previousTime + legDrivingTime
```

At each pickup:

```text
pickupTime = max(arrivalTime, job.dueDate)
waitingTime = max(0, job.dueDate - arrivalTime)
lateness = max(0, arrivalTime - job.dueDate)
```

If the vehicle arrives early, simulation waits until the due time before driving the next leg. This is important for preorders: arriving at a 10:00 pickup at 09:30 does not mean the next trip can start at 09:30.

For an immediate target whose due date is already in the past, its effective deadline is:

```text
targetDeadline = max(now, target.dueDate)
```

This prevents the age of an overdue request from distorting comparison between drivers. The relevant immediate-job question is who can arrive fastest from now.

### Target arrival versus pickup

The response distinguishes:

- `estimatedArrivalAt`: when the driver reaches the target pickup location.
- `estimatedPickupAt`: when service can start after any preorder waiting.

For an on-time preorder, `estimatedArrivalAt` can be earlier than
`estimatedPickupAt`. For a late pickup, both timestamps are equal.

### Waiting duration

`waitingDurationSeconds` is the accumulated waiting time at scheduled pickups up to and including the target.
Waiting after the target is not included because this field describes the path to the target job.

## Downstream commitment checks

Every backlog job ordered after the target receives a pickup estimate:

```json
{
  "jobId": "later-job-id",
  "estimatedPickupAt": "2026-08-05T11:00:00.000Z",
  "lateBySeconds": 0
}
```

These estimates are returned in `followingJobs`.

`maximumFollowingLatenessSeconds` is the largest predicted lateness among these jobs. It is zero when there are no later jobs or every later pickup stays on time.

The maximum is used instead of summing delays because one severely disrupted existing commitment should remain visible rather than being averaged across several on-time jobs.

## Candidate metrics

### Route to the target

Although the routing request may continue through later jobs, `routeDurationSeconds` and `routeDistanceKilometers` include only legs up to the target pickup.

They therefore answer:

> How much driving is required before reaching the new customer?

### Final approach

`approachDurationSeconds` and `approachDistanceKilometers` describe only the last leg into the target pickup.

For example:

```text
previous job.to -> target job.from
```

or, when nothing precedes the target:

```text
current position -> target job.from
```

The final approach is used as a deadhead measurement once all deadlines are safe.

### Target lateness

```text
lateBySeconds = max(0, estimatedArrivalAt - targetDeadline)
```

Zero means the target can be served on time. A positive value is the predicted delay.

## Exact ranking algorithm

Candidates are sorted using these rules, in order:

1. A candidate that keeps every later assigned job on time ranks before one that makes a later job late.

2. If both disrupt later jobs, the smaller maximumFollowingLatenessSeconds ranks first.

3. A target pickup that is on time ranks before a late target pickup.

4. If both target pickups are late, the earlier estimatedPickupAt ranks first.

5. If both target pickups are on time, the shorter final approach ranks first.

6. If final approaches are equal, the earlier estimatedArrivalAt ranks first.

## Ranking trace

Every returned candidate contains a `rankingTrace`. Ranking is relative, so the trace compares a candidate with an adjacent candidate in the final sorted list:

- Rank 1 is compared with rank 2 and explains why it is ahead.
- Every later rank is compared with the candidate immediately above it and explains why it is behind.
- A sole eligible candidate has no comparison and is described as the only eligible driver.

The trace is generated by the same comparison function that sorts the array.

Each trace step is one rule evaluated by the comparator.
Rules appear in policy order and have an `outcome` of `better`, `equal`, or `worse` from the current candidate's perspective.
Evaluation stops at the first non-equal rule, exactly as sorting does.
`decisiveCriterion` exposes that final step directly so a UI does not need to derive it from the step array.

For example, two on-time preorder candidates may produce:

```json
{
  "rank": 1,
  "summaryCode": "rankedAhead",
  "summaryValues": {
    "rank": 1,
    "comparedToDriverId": "free-driver",
    "comparedToDriverName": "Free Driver",
    "decisiveCriterion": "approachDuration"
  },
  "summary": "Ranked ahead Free Driver. Final approach takes 50 seconds, shorter than Free Driver at 300 seconds.",
  "comparedTo": {
    "driverId": "free-driver",
    "driverName": "Free Driver",
    "relation": "ahead"
  },
  "decisiveCriterion": "approachDuration",
  "steps": [
    {
      "criterion": "followingJobDisruption",
      "outcome": "equal",
      "code": "followingJobDisruption.equal",
      "values": {
        "candidate": false,
        "comparedTo": false,
        "unit": "boolean"
      },
      "message": "Like Free Driver, keeps all following jobs on time."
    },
    {
      "criterion": "maximumFollowingLateness",
      "outcome": "equal",
      "code": "maximumFollowingLateness.equal",
      "values": {
        "candidate": 0,
        "comparedTo": 0,
        "unit": "seconds"
      },
      "message": "Worst following-job delay matches Free Driver at 0 seconds."
    },
    {
      "criterion": "targetLateness",
      "outcome": "equal",
      "code": "targetLateness.equal",
      "values": {
        "candidate": false,
        "comparedTo": false,
        "unit": "boolean"
      },
      "message": "Like Free Driver, can pick up the target job on time."
    },
    {
      "criterion": "approachDuration",
      "outcome": "better",
      "code": "approachDuration.better",
      "values": {
        "candidate": 50,
        "comparedTo": 300,
        "unit": "seconds"
      },
      "message": "Final approach takes 50 seconds, shorter than Free Driver at 300 seconds."
    }
  ]
}
```

`summaryCode` selects the localized summary template and `summaryValues` contains its raw interpolation values.
Each step similarly exposes a stable `code` and raw `values`.
A frontend can therefore use translation keys such as `ranking.approachDuration.better` without parsing English or extracting numbers from sentences.

`summary` and each step's `message` remain ready-to-display English fallbacks.
The structured fields are the language-independent contract for UI logic, icons, formatting, and localization.

Raw step values have an explicit unit:

- `boolean` is used by `followingJobDisruption` and `targetLateness`.
  `true` means the candidate disrupts at least one following job or is late for the target, respectively.
- `seconds` is used for maximum following lateness and approach duration.
- `dateTime` is an ISO 8601 string used for estimated pickup and arrival times.

When every ranking criterion is equal, `decisiveCriterion` is omitted, `comparedTo.relation` is `tied`, and the summary states that list order is only being used for presentation.
The algorithm does not invent a business reason for an otherwise arbitrary tie.

### Why later commitments have priority

Later backlog jobs are already assigned and cannot be reassigned casually.
A new job should not make an existing promised pickup late when another driver can accept the new work without causing that disruption.

This means a driver who reaches the new customer slightly later can rank ahead of a driver who would reach the new customer on time but cause a severe delay to an existing preorder.

### Immediate jobs

When no future commitments are disrupted, immediate jobs reduce to earliest reachable pickup.
Every candidate is measured from the same `now`, so sorting late immediate pickups by timestamp is equivalent to sorting by the simulated time needed to reach the customer.

### Preorders

For preorders, the algorithm first protects later commitments, then prefers drivers who reach the new pickup on time. Among on-time candidates it minimizes the final empty approach.

This allows an occupied driver to be a strong candidate when the current trip finishes near the new pickup and the remaining schedule still fits.

## Response schema

```json
[
  {
    "driverId": "driver-123",
    "driverName": "Jane Driver",
    "state": "onTheWay",
    "latitude": 48.2082,
    "longitude": 16.3738,
    "currentJobId": "active-job-id",
    "precedingJobIds": ["active-job-id", "earlier-job-id"],
    "followingJobs": [
      {
        "jobId": "later-job-id",
        "estimatedPickupAt": "2026-08-05T11:00:00.000Z",
        "lateBySeconds": 0
      }
    ],
    "estimatedArrivalAt": "2026-08-05T10:07:00.000Z",
    "estimatedPickupAt": "2026-08-05T10:10:00.000Z",
    "routeDurationSeconds": 900,
    "waitingDurationSeconds": 180,
    "routeDistanceKilometers": 9,
    "approachDurationSeconds": 180,
    "approachDistanceKilometers": 2,
    "lateBySeconds": 0,
    "maximumFollowingLatenessSeconds": 0,
    "rankingTrace": {
      "rank": 1,
      "summaryCode": "onlyEligibleDriver",
      "summaryValues": {
        "rank": 1
      },
      "summary": "Only eligible driver.",
      "steps": []
    }
  }
]
```

`currentJobId` is present only when a started job is active.

| Field | Meaning |
| --- | --- |
| `driverId` | Driver to use for assignment. |
| `driverName` | Current `user.name` value for the driver. |
| `state` | Realtime state used to interpret the active job. |
| `latitude`, `longitude` | Latest cached driver position. |
| `currentJobId` | Started job fixed at the front of the route, if any. |
| `precedingJobIds` | Existing jobs scheduled before the target. Includes the active job. |
| `followingJobs` | Pickup estimates for existing jobs scheduled after the target. |
| `estimatedArrivalAt` | Time the driver reaches the target pickup location. |
| `estimatedPickupAt` | Time the target pickup can begin after waiting. |
| `routeDurationSeconds` | Driving time through the target pickup. |
| `waitingDurationSeconds` | Waiting time before and at the target. |
| `routeDistanceKilometers` | Driving distance through the target pickup. |
| `approachDurationSeconds` | Final-leg driving time to the target. |
| `approachDistanceKilometers` | Final-leg distance to the target. |
| `lateBySeconds` | Target pickup delay. |
| `maximumFollowingLatenessSeconds` | Worst predicted delay among later existing jobs. |
| `rankingTrace` | Structured explanation of this candidate's position in the returned list. |

### Ranking trace fields

| Field | Meaning |
| --- | --- |
| `rank` | One-based position in the response array. |
| `summaryCode` | Stable localization key: `onlyEligibleDriver`, `rankedAhead`, `rankedBehind`, or `tied`. |
| `summaryValues` | Raw values for constructing the localized summary. |
| `summary` | Concise, ready-to-display English explanation. |
| `comparedTo` | Adjacent driver used to explain this position. Omitted when there is only one candidate. |
| `comparedTo.relation` | Whether this candidate is `ahead`, `behind`, or `tied` relative to that driver. |
| `decisiveCriterion` | First non-equal ranking rule. Omitted for a complete tie or sole candidate. |
| `steps` | Ranking rules actually evaluated, in order, through the decisive rule. |
| `steps[].criterion` | Stable machine-readable ranking-rule identifier. |
| `steps[].outcome` | `better`, `equal`, or `worse` from this candidate's perspective. |
| `steps[].code` | Stable localization key formed as `<criterion>.<outcome>`. |
| `steps[].values.candidate` | Current candidate's raw value for this rule. |
| `steps[].values.comparedTo` | Comparison driver's raw value for this rule. |
| `steps[].values.unit` | Value type/unit: `boolean`, `seconds`, or `dateTime`. |
| `steps[].message` | Ready-to-display English explanation of that comparison step. |

The endpoint exposes concrete schedule metrics instead of an opaque score so a dispatcher can understand and verify the order.

## Complexity and performance

For `D` tracked drivers and `J` unfinished assigned jobs:

- One query loads the target job.
- One query loads all relevant unfinished jobs.
- One query loads the names of tracked drivers. It runs in parallel with the
  unfinished-jobs query.
- Grouping costs `O(J)`.
- Each driver's backlog ordering costs `O(Jd log Jd)`, where `Jd` is that
  driver's small backlog.
- Ranking costs `O(D log D)`.
- At most one routing request is made per eligible driver.

The operating assumption is that each driver has only a small backlog.

## Deliberate simplifications

The algorithm does not currently include:

- Driver fairness or workload balancing.
- Fleet coverage by geographic area.
- Fuel level or vehicle telemetry other than location and state.
- Pickup/drop-off service-time allowances.
- Historical endpoint confidence.
- A fixed uncertainty buffer.
- Probabilistic customer destination changes.
- Traffic corrections beyond the routing engine.

These factors were omitted to keep the initial policy deterministic and simple.

## Known limitations

### Raw versus incremental downstream lateness

The endpoint ranks using predicted downstream lateness after inserting the target.
It does not separately calculate the driver's baseline schedule without the target.
Consequently, a driver whose existing schedule was already late can be penalized even when the new job adds little additional delay.

Calculating incremental disruption accurately would require an additional baseline route or cached schedule per driver. -> Resource overhead and complexity

### Destination uncertainty

When a destination exists, it is treated as accurate. Passengers may still change their destination or leave early. No uncertainty buffer currently exist.

### No service time

Only driving and due-time waiting are simulated. Time spent locating a customer, loading luggage, assisting passengers, or completing payment is not included.

### Inconsistent state is excluded

Brief synchronization windows can cause a started database job and `free` telemetry to disagree. The endpoint excludes that driver rather than guessing whether pickup occurred.