# X. Routing and Turn-by-Turn Navigation

## X.1 Objective and Requirements

The routing feature was implemented to integrate navigation directly into the job workflow of the Android application. Unlike a general-purpose navigation application, the route is determined by the state of the currently assigned job. A job consists of a pickup location, an optional destination, and a lifecycle that describes whether the driver is travelling to the passenger or transporting the passenger.

The implementation therefore distinguishes between two explicit navigation phases:

1. `ToPickup`: navigation from the vehicle's current location to the pickup address.
2. `ToDestination`: navigation from the vehicle's location at the time the passenger is collected to the destination address.

An additional `None` phase represents the absence of active navigation. Representing these phases explicitly was preferable to inferring them from the presence of route geometry because route geometry may temporarily be unavailable while a request is loading or failing. The explicit state also prevents an old pickup route from being displayed after the application has already entered the destination phase.

The routing feature was designed to provide:

- job-dependent route selection;
- visual route rendering on MapLibre;
- localized turn-by-turn instructions;
- live maneuver progression;
- automatic rerouting;
- direction-aware routing using vehicle heading;
- smooth camera and route movement;
- cleanup when navigation is no longer applicable;
- safe handling of network, router, and data errors.

No voice guidance was implemented. The feature focuses on visual navigation.

## X.2 Architectural Design

The routing implementation follows the existing layered architecture of the application. Networking and reusable routing algorithms are located in the `core` module. Job-specific orchestration belongs to the main screen's ViewModel, while rendering remains in the application module.

The general data flow is shown below.

```text
Job state + live location
          |
          v
NavigationRoutePlanner
          |
          v
MainScreenViewModel
          |
          v
GeoServiceRepository
          |
          v
Authenticated backend route endpoint
          |
          v
RouteResponseParser + Polyline6Decoder
          |
          v
NavigationUiState
       +--+----------------+
       |                   |
       v                   v
NavigationPanel         AtlasMap
(turn instructions)     (route and camera)
       ^                   ^
       +----- RouteProgressCalculator
                      ^
                      |
                live location
```

The main files and their responsibilities are summarized below.

| File                          | Responsibility                                                            |
| ----------------------------- | ------------------------------------------------------------------------- |
| `GeoServiceRepository.kt`     | Defines the routing API and explicit result types                         |
| `GeoServiceRepositoryImpl.kt` | Creates and executes authenticated route requests                         |
| `RouteModels.kt`              | Defines route points, maneuvers, summaries, and progress                  |
| `Polyline6Decoder.kt`         | Decodes Valhalla polyline6 geometry                                       |
| `RouteResponseParser.kt`      | Validates and converts JSON responses into domain models                  |
| `RouteProgressCalculator.kt`  | Matches the vehicle to the route and calculates navigation progress       |
| `VehicleHeadingEstimator.kt`  | Determines the vehicle's direction of travel                              |
| `NavigationRoutePlanner.kt`   | Selects the correct origin and destination for each job phase             |
| `NavigationUiState.kt`        | Represents phase, loading status, errors, route, and progress             |
| `MainScreenViewModel.kt`      | Coordinates jobs, locations, route requests, rerouting, and cleanup       |
| `NavigationPanel.kt`          | Displays the next instruction, distance, duration, and maneuver list      |
| `MainScreen.kt`               | Connects navigation state to the map and navigation controls              |
| `RouteMapLayer.kt`            | Creates and updates the MapLibre route source and line layer              |
| `AtlasMap.kt`                 | Manages MapLibre, the location puck, camera tracking, and route animation |

This separation was chosen to avoid placing networking or routing algorithms inside Compose functions. Compose recomposition can occur frequently and is not a suitable trigger for network requests. Instead, composables receive immutable navigation state and only render it.

## X.3 Backend Route Service

The Android application requests routes using the authenticated endpoint:

```http
GET /api/geoservices/route
```

The request contains the following parameters:

```text
fromlat
fromlon
tolat
tolon
heading   (optional)
lang      (optional)
```

For example:

```http
GET /api/geoservices/route
    ?fromlat=48.2082
    &fromlon=16.3738
    &tolat=48.3069
    &tolon=16.437
    &heading=93
    &lang=de-AT
```

The backend acts as a controlled gateway to the routing service. This has several advantages over contacting the router directly from Android:

- routing infrastructure remains hidden from the client;
- requests use the application's existing authentication;
- request and response schemas can be validated centrally;
- the routing provider can be replaced without changing the mobile API;
- routing language and vehicle configuration are controlled consistently.

The backend converts the query parameters into a Valhalla-style route request using automobile costing. The optional heading is added to the first route location. U-turns at the initial location are disabled, reducing the chance that the generated route immediately instructs the driver to reverse direction.

The router returns route geometry, maneuver instructions, distances, durations, and shape indexes. The backend validates this response before forwarding it to Android.

## X.4 Android Networking Implementation

The Android implementation uses the project's existing manual OkHttp architecture rather than introducing Retrofit. This avoids operating two networking stacks and allows routing requests to share the same authentication state as the rest of the application.

`AtlasApplication` creates one `NetworkClient`, which contains an `OkHttpClient` and a shared `MemoryCookieJar`. The same client is injected into the authentication, job, telemetry, and geoservice repositories. Therefore, the route request automatically includes the session cookies established during login.

The server address is retrieved from `ServerSettingsRepository`. This is necessary because the application supports a configurable server instead of relying on a compile-time URL.

`GeoServiceRepositoryImpl` constructs the request using OkHttp's `HttpUrl.Builder`. This approach safely encodes query parameters and avoids constructing URLs through string concatenation. It adds the headers:

```http
Origin: {serverAddress}
Accept: application/json
```

The request is executed on `Dispatchers.IO`. The OkHttp call is wrapped with `suspendCancellableCoroutine`. When the calling coroutine is cancelled, the underlying OkHttp call is also cancelled. This is important during rerouting or job changes because a request may no longer be relevant before the server responds.

The repository returns a sealed `RouteResult` instead of throwing general exceptions into the UI layer. The possible results include:

- success;
- unauthorized access;
- network failure;
- malformed JSON;
- schema-invalid response;
- router error;
- other server error.

For example, a router response such as:

```json
{
  "error_code": 171,
  "error": "No suitable edges near location",
  "status_code": 400,
  "status": "Bad Request"
}
```

is represented as `RouteResult.RouterError`. This distinction is useful because an invalid route near a location is different from a lost network connection or expired user session.

## X.5 Route Response Parsing

The router response is not passed directly to the UI. `RouteResponseParser` converts it into a smaller Android-specific model containing only the required information.

A successful route consists of:

- decoded route points;
- maneuver instructions;
- maneuver shape indexes;
- route distance;
- route duration;
- language and unit metadata.

The parser first distinguishes between malformed JSON, HTTP errors, router errors, and successful responses. A successful response must contain a `trip` object with at least one leg and valid geometry.

Optional maneuver fields are handled defensively. If the maneuver's normal `instruction` is missing, the verbal pre-transition instruction can be used as a fallback. If all maneuver data is missing, the route can still be drawn and its summary can still be displayed.

Distances returned in miles are converted to kilometres. If the trip summary is absent or incomplete, the parser derives the total duration and distance by summing the leg summaries.

### X.5.1 Multiple Route Legs

Although the current job workflow normally uses two locations, the parser supports responses containing multiple legs.

Each leg contains its own encoded shape and local maneuver indexes. During concatenation, the parser:

1. decodes every leg;
2. detects whether the first point of the new leg equals the last point of the previous leg;
3. removes this duplicated boundary point;
4. calculates the new global shape-index offset;
5. adjusts each maneuver's local indexes by this offset.

This is required because maneuver indexes refer to positions in a leg's geometry. Without index adjustment, the instructions of later legs would point to incorrect locations in the combined route.

## X.6 Polyline6 Decoding

Valhalla encodes route geometry using polyline precision 6. Coordinates therefore use a factor of:

$$
10^6 = 1{,}000{,}000
$$

This differs from Google's commonly used polyline precision 5. Using the wrong factor would move route points by approximately one decimal place and make the route unusable.

`Polyline6Decoder` processes the encoded string as a sequence of latitude and longitude deltas. Each character has an offset of 63. Five data bits are read from each character, while bit `0x20` indicates that another character belongs to the current value.

Signed values are restored using the least significant bit. The reconstructed delta is then added to the previous coordinate:

$$
lat_i = lat_{i-1} + \Delta lat_i
$$

$$
lon_i = lon_{i-1} + \Delta lon_i
$$

The integer results are divided by $1{,}000{,}000$ to obtain decimal degrees.

The decoder uses `Long` values and checked addition to detect overflow. It also rejects:

- unsupported characters;
- excessive bit shifting;
- truncated latitude or longitude values;
- invalid latitude or longitude ranges.

Returning an explicit failure instead of partially decoded geometry prevents malformed routes from being displayed.

## X.7 Job-Dependent Route Planning

`NavigationRoutePlanner` determines whether a route can be requested and which coordinates should be used. Its output is a sealed plan with the states:

- no navigation;
- waiting for location;
- pickup unavailable;
- waiting for destination;
- ready to request a route.

This makes coordinate validation independent of networking.

### X.7.1 Route to the Pickup Location

When the driver presses "Next Job", the application first executes the existing job-start request. It does not request a route from the queued job immediately.

After the job-start operation succeeds, the job list is reloaded. Only when the refreshed state contains the selected job as `currentJob` does `reconcileNavigation()` create the pickup route request.

The pickup route uses:

```text
Origin: latest available vehicle or device location
Destination: currentJob.from
```

This ordering prevents a route from being created for a job that failed to start or was replaced by another job.

The selected pickup origin is captured in `pickupRouteOrigin`. Normal location updates do not continuously replace it because doing so would request a new route for every GPS update. It is only updated when a genuine reroute becomes necessary.

### X.7.2 Route to the Destination

When the driver presses "Person collected", the ViewModel preserves the original business behavior:

- the collected job identifier is stored;
- the telemetry vehicle state changes to `OCCUPIED`;
- the UI state records that the passenger was collected.

At this moment, the latest vehicle position and heading are captured as `destinationRouteOrigin`. The route then uses:

```text
Origin: vehicle location captured when "Person collected" was pressed
Destination: currentJob.to
```

Using the current vehicle location is useful when the passenger is collected slightly away from the stored pickup coordinate. The generated route begins at the vehicle's actual position rather than making the driver return to the nominal pickup point.

### X.7.3 Missing or Edited Destination

A job may not yet contain destination coordinates. In this case, pressing "Person collected" opens the existing address editor and changes the navigation status to `WaitingForDestination`. No invalid route request is sent.

After the driver selects an address:

1. the selected coordinates are saved through `JobRepository`;
2. the updated jobs are requested again;
3. the refreshed `currentJob` contains the destination;
4. `applyJobs()` calls `reconcileNavigation()`;
5. the destination route is requested automatically.

If the destination is edited again, the resulting route request contains different destination coordinates. It therefore replaces the previously loaded destination route.

## X.8 Concurrency and Route Ownership

Routing requests are asynchronous, so cancellation alone is not enough to guarantee correct state. A response may arrive just as its coroutine is being replaced.

The ViewModel therefore combines several safeguards:

- `routeRequestTask` cancels the previous coroutine;
- cancelling the coroutine cancels the underlying OkHttp call;
- `routeRequestGeneration` is incremented for every new request or cleanup;
- `activeRouteRequest` identifies the currently expected inputs;
- every response is checked against the current job identifier;
- `loadedRouteRequest` prevents duplicate requests.

A response is accepted only if its generation, request data, job identifier, and navigation phase are still current. Consequently, an older pickup response cannot overwrite a destination route after "Person collected" has been pressed.

When a route is refreshed for the same job and phase, the last valid route remains visible while the replacement is loading. If a new job or phase takes ownership, the old route is cleared immediately.

Navigation is also cleared when:

- the current job is cancelled;
- the current job is completed or disappears;
- a different job becomes current;
- the user logs out;
- `clearJobs()` is called;
- the required coordinates disappear.

## X.9 Vehicle Heading

Route origin coordinates alone may be ambiguous when the vehicle is on a road that can be travelled in both directions. The optional heading parameter helps the router choose the correct initial road direction.

A phone compass was not used as the primary heading source because the physical orientation of the phone does not necessarily match the direction of the vehicle. The phone may lie sideways, be rotated in a holder, or be carried by a passenger.

Instead, `VehicleHeadingEstimator` estimates course over ground.

It first prefers the bearing supplied by the location provider, but only when:

- the vehicle speed is at least $2\,m/s$;
- the location accuracy is acceptable;
- the bearing is finite.

If no reliable provider bearing is available, the estimator calculates a bearing from two timestamped coordinates. A movement sample is accepted only if:

- the time difference is between 0.5 and 15 seconds;
- the movement is at least 8 metres;
- the movement is larger than 1.5 times the reported GPS uncertainty;
- the inferred speed is at least $1.5\,m/s$.

These conditions prevent small GPS fluctuations while stationary from being interpreted as vehicle direction.

The initial bearing is calculated from the two geographic positions. Heading values are then smoothed as unit vectors:

$$
x = (1-w)\cos(\theta_{old}) + w\cos(\theta_{new})
$$

$$
y = (1-w)\sin(\theta_{old}) + w\sin(\theta_{new})
$$

$$
\theta_{smooth} = \operatorname{atan2}(y,x)
$$

Using vector-based smoothing correctly handles the transition between $359^\circ$ and $0^\circ$. A normal arithmetic average would incorrectly rotate through $180^\circ$. The current smoothing weight is 0.5.

A heading older than 15 seconds is not blended into new movement. If no reliable heading is available, the parameter is omitted rather than sending an invented direction.

The same estimator is reused for route requests, the direction-up camera, and the direction indicator on the location puck.

## X.10 Maneuver Progress Calculation

Location updates do not cause new route requests under normal conditions. Instead, `RouteProgressCalculator` advances navigation locally.

### X.10.1 Matching the Vehicle to the Route

The current GPS position is projected onto candidate line segments of the decoded route.

For a segment from $A$ to $B$ and vehicle position $P$, the projection fraction is calculated as:

$$
t =
\operatorname{clamp}
\left(
\frac{(P-A)\cdot(B-A)}
{\lVert B-A\rVert^2},
0,
1
\right)
$$

Longitude differences are scaled by the cosine of the local latitude. The distance between the projected point and the GPS position is then calculated using the haversine formula.

The search is limited to 40 segments behind and 250 segments ahead of the previous route position. This provides two benefits:

- processing is faster than scanning the entire route;
- nearby parallel roads or later parts of a looping route are less likely to be selected accidentally.

### X.10.2 Monotonic Progress

Progress is monotonic within a route. If GPS noise produces a point behind the previously accepted position, the displayed progress does not move backwards. This prevents completed maneuver instructions and consumed route geometry from reappearing.

The raw backwards projection is still retained as a wrong-way signal. If the projected movement is at least 50 metres behind the previously snapped position, `isMovingAgainstRoute` becomes true.

### X.10.3 Selecting the Next Instruction

Every maneuver contains `begin_shape_index` and `end_shape_index`. These indexes connect the instruction to the decoded route geometry.

The calculator distinguishes between:

- the maneuver whose segment is currently being travelled;
- the next actionable maneuver whose begin index lies ahead.

The prominent navigation instruction uses the next actionable maneuver where possible. This distinction is important for instructions such as:

```text
Drive north.
Turn right.
Turn left onto B125.
```

"Drive north" describes the current segment. Its maneuver length describes how long that segment continues; it is not the distance to perform the following right turn. Therefore, the UI displays "Turn right" together with the calculated distance from the current snapped position to that maneuver's `begin_shape_index`.

After the begin or end index is passed, the calculator selects the next applicable maneuver. Maneuvers are not completed based on elapsed time.

The remaining route distance is calculated from the snapped point along the remaining geometry. It is scaled to match the server's route summary. Remaining route time is estimated proportionally from the remaining geometry. If summary time is unavailable, maneuver times are used as a fallback.

## X.11 Automatic Rerouting

The application reroutes when the vehicle leaves the route or travels in the opposite direction.

The off-route threshold is dynamic:

$$
d_{threshold} =
\max(30\,m,\;2.5 \times GPSAccuracy)
$$

A fixed small threshold would cause unnecessary reroutes when GPS accuracy is poor. A threshold based only on accuracy could become too small under ideal conditions. Combining both produces more stable behavior.

A reroute is initiated after either:

- two consecutive off-route samples; or
- two consecutive wrong-way samples.

A 15-second cooldown prevents repeated requests when the vehicle remains in a difficult GPS area.

When rerouting starts, the current location and current estimated heading become the new origin. The destination and navigation phase remain unchanged. The previous valid route stays visible until the new response arrives because it still belongs to the same job and phase.

The route endpoint is therefore not called for every location update. In normal operation, progress is entirely local. Network routing is used only for initial routes, changed destinations, changed phases, or confirmed deviations.

This design also explains why rerouting is not instantaneous. The application deliberately waits for multiple location samples to distinguish an actual deviation from temporary GPS noise, after which server and network latency are added.

## X.12 MapLibre Route Rendering

`RouteMapLayer.kt` creates a dedicated `GeoJsonSource` and `LineLayer`.

The geometry is converted into a GeoJSON `LineString`. GeoJSON and MapLibre use longitude/latitude ordering, so each coordinate is created as:

```kotlin
Point.fromLngLat(longitude, latitude)
```

Reversing these values would place the route in an incorrect geographic location.

The route line uses:

- a high-contrast blue colour;
- six-pixel line width;
- 92% opacity;
- rounded joins;
- rounded caps.

The layer is inserted below the first symbol layer where possible. This keeps map labels and live-user markers readable above the route.

The source and layer are recreated whenever a new map style finishes loading. When the route is cleared, the source receives an empty `FeatureCollection`. The source itself remains registered, which simplifies later updates and style lifecycle handling.

### X.12.1 Removing the Travelled Route

Only the untravelled part of the geometry is passed to the map. `remainingRoutePoints()` begins at the current snapped route point and removes all earlier points. The route line therefore disappears behind the vehicle as it advances.

To avoid visible two-second jumps when location updates arrive, the route head is interpolated over 18 frames with 50 milliseconds between frames. Related geometry is animated progressively. A completely different reroute is replaced directly instead of morphing through unrelated roads.

## X.13 Navigation Interface

`NavigationPanel` is a Material 3 card displayed above the map. It shows:

- the current navigation phase;
- the next actionable maneuver;
- a directional icon;
- "In ..." distance to the maneuver;
- total remaining route time;
- total remaining route distance;
- an expandable list of remaining maneuvers.

The backend's localized instruction is displayed directly. Android does not attempt to reconstruct street names or translate router instructions.

Directional icons are derived from the Valhalla maneuver type. The base arrow is rotated for right turns, left turns, and U-turns. Unknown maneuver types fall back to a forward arrow.

The expanded list uses "Continue for ..." for each maneuver's segment length. The prominent instruction instead uses "In ..." because it represents the distance until the maneuver begins. Keeping these concepts separate prevents users from interpreting the length of the road after a turn as the distance before the turn.

If maneuver information is unavailable, the route and summary remain usable. Loading, missing-location, missing-destination, router-error, network-error, and authentication states are represented through `NavigationStatus` and localized string resources.

## X.14 Location Puck and Camera Behaviour

The MapLibre location component uses the application's location provider instead of MapLibre's default engine. This is necessary because Atlas can prefer a car-provided position and fall back to the phone position through `CarAwareLocationProvider`.

Locations are supplied to MapLibre with `forceLocationUpdate()`. The component uses 60 animation frames per second and MapLibre's tracking animations to interpolate between location samples.

A custom location puck contains:

- a compact blue centre;
- a white outline;
- a subtle shadow;
- a translucent cone representing driving direction.

The puck uses the movement-derived vehicle heading, not the physical orientation of the phone.

While follow mode is active, `CameraMode.TRACKING_GPS` rotates the map into the direction of travel. The standard compass button is disabled in this state because direction-up tracking already communicates orientation and a second orientation control would be redundant.

If the driver manually moves the map, follow mode is disabled and a recenter button becomes available. Pressing it restores:

- vehicle-centred tracking;
- direction-up orientation;
- a moderate zoom level of 16.25.

A newly loaded or rerouted route activates follow mode automatically.

During navigation, the puck is placed below the visual centre of the screen. MapLibre tracking padding uses 42% of map height in portrait mode and 24% in landscape mode. This places the puck at approximately 71% of screen height in portrait and 62% in landscape. The space above the vehicle therefore shows more of the road and upcoming intersections.

When navigation ends, the padding returns to zero and normal centred map behaviour is restored.

## X.15 Verification

The routing implementation contains focused unit tests for its most error-prone calculations and decisions.

`RouteProgressCalculatorTest` verifies:

- advancing to the next maneuver;
- distance to a maneuver's begin index;
- final arrival instructions;
- monotonic progress;
- route geometry without maneuvers;
- bounded route matching;
- off-route distance;
- wrong-way detection;
- removal of consumed geometry.

`VehicleHeadingEstimatorTest` verifies:

- use of provider travel bearing;
- fallback calculation from timestamped coordinates;
- rejection of slow movement and GPS jitter;
- handling of GPS uncertainty;
- smoothing across north;
- removal of stale heading influence.

Additional tests verify:

- heading parameters in HTTP requests;
- omission of unavailable headings;
- rejection of invalid headings;
- pickup and destination heading capture;
- route-line interpolation;
- replacement of unrelated route geometry;
- portrait and landscape camera padding;
- restoration of centred padding without navigation.

The relevant unit tests, debug APK assembly, Kotlin compilation, and Android lint checks were executed during implementation.

## X.16 Limitations and Future Improvements

The implemented solution provides practical visual navigation, but it is not a full replacement for a dedicated navigation SDK.

First, map matching is based on nearest-segment projection rather than a probabilistic map-matching algorithm. Complex junctions, tunnels, and closely parallel roads can therefore remain challenging.

Second, remaining time is estimated from route geometry and the server summary. It does not account for live traffic, temporary road closures, or changing driving speed.

Third, rerouting deliberately waits for consecutive deviation samples. This improves stability but introduces a short delay before a new route appears.

Further improvements could include:

- voice instructions;
- lane guidance;
- speed-dependent maneuver warning thresholds;
- traffic-aware travel times;
- offline route storage;
- more advanced map matching;
- instrumentation tests for complete job-navigation flows;
- additional direct tests for response parsing and malformed polyline data.

## X.17 Summary

The routing feature extends the Atlas Android application from a job-display system into an integrated driver navigation interface. The implementation combines authenticated backend routing, defensive response parsing, polyline6 decoding, job-dependent route planning, local maneuver progression, movement-based heading estimation, automatic rerouting, and MapLibre visualisation.

The most important architectural decision was to keep responsibilities separated. The repository obtains route data, domain classes interpret it, the ViewModel owns job and route lifecycle, and Compose and MapLibre only render immutable state. This separation makes the feature testable and ensures that frequent location updates or UI recompositions do not accidentally create repeated network requests.

The resulting navigation remains tied to the operational job workflow while offering behaviour familiar from conventional navigation applications: a direction-aware puck, route line removal behind the vehicle, next-turn instructions, automatic recentering, direction-up camera tracking, and automatic rerouting after confirmed deviations.
