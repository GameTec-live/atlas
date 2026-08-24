package org.gtlv.atlas.map

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.util.Log
import android.view.Gravity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.gtlv.atlas.R
import org.gtlv.atlas.location.toAndroidLocation
import org.gtlv.core.location.LocationState
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.telemetry.LiveMapUser
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

@SuppressLint("MissingPermission")
@Composable
internal fun AtlasMap(
    locationState: LocationState,
    liveMapUsers: Collection<LiveMapUser>,
    routePoints: List<RoutePoint>,
    recenterRequestId: Int,
    isFollowingLocation: Boolean,
    onUserCameraMove: () -> Unit,
    styleUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val isLandscape =
        configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

    val safeLeft = WindowInsets.safeDrawing.getLeft(
        density = density,
        layoutDirection = layoutDirection
    )

    val safeTop = WindowInsets.safeDrawing.getTop(
        density = density
    )

    val compassLeftMargin = safeLeft + with(density) {
        if (isLandscape) {
            84.dp.roundToPx()
        } else {
            20.dp.roundToPx()
        }
    }

    val compassTopMargin = safeTop + with(density) {
        if (isLandscape) {
            20.dp.roundToPx()
        } else {
            84.dp.roundToPx()
        }
    }

    val defaultMapError =
        stringResource(R.string.map_load_error)

    val locationDisplayError =
        stringResource(
            R.string.map_location_display_error
        )

    var loadState by remember {
        mutableStateOf<MapLoadState>(
            MapLoadState.Loading
        )
    }

    var readyMap by remember {
        mutableStateOf<MapLibreMap?>(null)
    }

    var hasInitiallyCentered by rememberSaveable {
        mutableStateOf(false)
    }

    var savedLatitude by rememberSaveable {
        mutableDoubleStateOf(
            MapConfiguration.INITIAL_LATITUDE
        )
    }

    var savedLongitude by rememberSaveable {
        mutableDoubleStateOf(
            MapConfiguration.INITIAL_LONGITUDE
        )
    }

    var savedZoom by rememberSaveable {
        mutableDoubleStateOf(
            MapConfiguration.INITIAL_ZOOM
        )
    }

    var savedBearing by rememberSaveable {
        mutableDoubleStateOf(0.0)
    }

    var savedTilt by rememberSaveable {
        mutableDoubleStateOf(0.0)
    }

    val currentOnUserCameraMove by
    rememberUpdatedState(onUserCameraMove)

    val initialCameraPosition =
        CameraPosition.Builder()
            .target(
                LatLng(
                    savedLatitude,
                    savedLongitude
                )
            )
            .zoom(savedZoom)
            .bearing(savedBearing)
            .tilt(savedTilt)
            .build()

    val mapView = rememberMapViewWithLifecycle(
        initialCameraPosition =
            initialCameraPosition
    )

    val routeRenderState = remember {
        RouteRenderState()
    }

    LaunchedEffect(
        readyMap,
        compassLeftMargin,
        compassTopMargin
    ) {
        readyMap?.uiSettings?.apply {
            compassGravity =
                Gravity.TOP or Gravity.START

            setCompassMargins(
                compassLeftMargin,
                compassTopMargin,
                0,
                0
            )
        }
    }

    LaunchedEffect(mapView, styleUrl) {
        loadState = MapLoadState.Loading
        readyMap = null

        mapView.addOnDidFailLoadingMapListener {
            loadState = MapLoadState.Error(
                message = defaultMapError
            )
        }

        mapView.getMapAsync { map ->
            map.uiSettings.isAttributionEnabled =
                false

            map.uiSettings.isLogoEnabled = false

            map.setStyle(
                Style.Builder().fromUri(styleUrl)
            ) { style ->
                val activationResult = runCatching {
                    val componentOptions =
                        LocationComponentOptions
                            .builder(context)
                            .pulseEnabled(true)
                            .trackingAnimationDurationMultiplier(1f)
                            .trackingGesturesManagement(true)
                            .accuracyAnimationEnabled(true)
                            .build()

                    val activationOptions =
                        LocationComponentActivationOptions
                            .builder(context, style)
                            .locationComponentOptions(
                                componentOptions
                            )
                            .useDefaultLocationEngine(false)
                            .build()

                    map.locationComponent
                        .activateLocationComponent(
                            activationOptions
                        )

                    map.locationComponent
                        .isLocationComponentEnabled =
                        true

                    map.locationComponent.cameraMode =
                        CameraMode.NONE

                    map.locationComponent.renderMode =
                        RenderMode.NORMAL

                    map.locationComponent.setMaxAnimationFps(60)
                }

                if (activationResult.isFailure) {
                    loadState = MapLoadState.Error(
                        message = locationDisplayError
                    )

                    return@setStyle
                }

                runCatching {
                    style.addRouteLayer()
                    style.addLiveMapUserLayers()
                }.onFailure { exception ->
                    Log.e(
                        TAG,
                        "Failed to add live map user layers",
                        exception
                    )
                }

                map.addOnCameraIdleListener {
                    val camera = map.cameraPosition
                    val target = camera.target

                    if (target != null) {
                        savedLatitude =
                            target.latitude

                        savedLongitude =
                            target.longitude
                    }

                    savedZoom = camera.zoom
                    savedBearing = camera.bearing
                    savedTilt = camera.tilt
                }

                map.addOnCameraMoveStartedListener {
                        reason ->
                    if (
                        reason ==
                        MapLibreMap
                            .OnCameraMoveStartedListener
                            .REASON_API_GESTURE
                    ) {
                        currentOnUserCameraMove()
                    }
                }

                readyMap = map
                loadState = MapLoadState.Loaded
            }
        }
    }

    val availableLocation =
        (locationState as? LocationState.Available)
            ?.location

    LaunchedEffect(
        readyMap,
        availableLocation
    ) {
        val map =
            readyMap ?: return@LaunchedEffect

        val location =
            availableLocation
                ?: return@LaunchedEffect

        runCatching {
            map.locationComponent
                .forceLocationUpdate(
                    location.toAndroidLocation()
                )
        }.onFailure { exception ->
            Log.e(
                TAG,
                "Failed to update user location",
                exception
            )
        }

    }

    LaunchedEffect(
        readyMap,
        isFollowingLocation,
        availableLocation != null
    ) {
        val map = readyMap ?: return@LaunchedEffect

        runCatching {
            map.locationComponent.cameraMode =
                if (isFollowingLocation) {
                    CameraMode.TRACKING
                } else {
                    CameraMode.NONE
                }

            if (
                isFollowingLocation &&
                availableLocation != null &&
                !hasInitiallyCentered
            ) {
                hasInitiallyCentered = true
                map.locationComponent.zoomWhileTracking(
                    MapConfiguration.USER_LOCATION_ZOOM,
                    INITIAL_CAMERA_TRANSITION_MILLIS
                )
            }
        }.onFailure { exception ->
            Log.e(
                TAG,
                "Failed to update location camera tracking",
                exception
            )
        }
    }

    LaunchedEffect(
        readyMap,
        recenterRequestId
    ) {
        if (recenterRequestId == 0) {
            return@LaunchedEffect
        }

        val map =
            readyMap ?: return@LaunchedEffect

        val location =
            availableLocation
                ?: return@LaunchedEffect

        hasInitiallyCentered = true
        runCatching {
            map.locationComponent.forceLocationUpdate(
                location.toAndroidLocation()
            )
            map.locationComponent.cameraMode =
                CameraMode.TRACKING
            map.locationComponent.zoomWhileTracking(
                MapConfiguration.USER_LOCATION_ZOOM,
                INITIAL_CAMERA_TRANSITION_MILLIS
            )
        }.onFailure { exception ->
            Log.e(
                TAG,
                "Failed to recenter location camera",
                exception
            )
        }
    }

    LaunchedEffect(
        readyMap,
        routePoints
    ) {
        val map = readyMap ?: return@LaunchedEffect
        val style = map.style
            ?: return@LaunchedEffect

        val previousPoints = if (routeRenderState.map === map) {
            routeRenderState.points
        } else {
            emptyList()
        }
        routeRenderState.map = map

        if (
            previousPoints.isEmpty() ||
            routePoints.isEmpty() ||
            !canAnimateRouteTransition(
                previous = previousPoints,
                target = routePoints
            )
        ) {
            updateDisplayedRoute(
                style = style,
                points = routePoints,
                state = routeRenderState
            )
            return@LaunchedEffect
        }

        for (frame in 1..ROUTE_TRANSITION_FRAME_COUNT) {
            delay(ROUTE_TRANSITION_FRAME_MILLIS)
            val fraction = frame.toDouble() /
                    ROUTE_TRANSITION_FRAME_COUNT
            val displayedPoints = interpolateRemainingRoute(
                previous = previousPoints,
                target = routePoints,
                fraction = fraction
            )
            updateDisplayedRoute(
                style = style,
                points = displayedPoints,
                state = routeRenderState
            )
        }
    }

    LaunchedEffect(
        readyMap,
        liveMapUsers
    ) {
        val style =
            readyMap?.style
                ?: return@LaunchedEffect

        runCatching {
            style.updateLiveMapUsers(
                liveMapUsers
            )
        }.onFailure { exception ->
            Log.e(
                TAG,
                "Failed to update live map users",
                exception
            )
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        when (val state = loadState) {
            MapLoadState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(
                        Alignment.Center
                    )
                )
            }

            MapLoadState.Loaded -> Unit

            is MapLoadState.Error -> {
                Surface(
                    modifier = Modifier.align(
                        Alignment.Center
                    ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .errorContainer,
                    contentColor =
                        MaterialTheme
                            .colorScheme
                            .onErrorContainer
                ) {
                    Text(text = state.message)
                }
            }
        }
    }
}

private fun updateDisplayedRoute(
    style: Style,
    points: List<RoutePoint>,
    state: RouteRenderState
) {
    runCatching {
        style.updateRoute(points)
        state.points = points
    }.onFailure { exception ->
        Log.e(
            TAG,
            "Failed to update route layer",
            exception
        )
    }
}

private class RouteRenderState(
    var map: MapLibreMap? = null,
    var points: List<RoutePoint> = emptyList()
)

private const val TAG = "AtlasMap"
private const val INITIAL_CAMERA_TRANSITION_MILLIS = 750L
private const val ROUTE_TRANSITION_FRAME_MILLIS = 50L
private const val ROUTE_TRANSITION_FRAME_COUNT = 18
