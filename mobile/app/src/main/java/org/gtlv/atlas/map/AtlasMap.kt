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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.gtlv.atlas.R
import org.gtlv.atlas.location.toAndroidLocation
import org.gtlv.core.location.LocationState
import org.gtlv.core.location.VehicleHeadingEstimator
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.telemetry.LiveMapUser
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
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
    showRouteEndpoints: Boolean = false,
    recenterRequestId: Int,
    isFollowingLocation: Boolean,
    onUserCameraMove: () -> Unit,
    onMapClick: () -> Unit = {},
    styleUrl: String,
    cameraFocusPoints: List<RoutePoint> = emptyList(),
    cameraFocusRequestId: Int = 0,
    cameraFocusPadding: Dp = 72.dp,
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

    val currentOnMapClick by
    rememberUpdatedState(onMapClick)

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

    var mapHeightPixels by remember {
        mutableIntStateOf(0)
    }

    val hasActiveNavigationRoute = routePoints.size >= 2

    val navigationCameraTopPaddingPixels =
        navigationCameraTopPaddingPixels(
            mapHeightPixels = mapHeightPixels,
            isLandscape = isLandscape,
            hasActiveRoute = hasActiveNavigationRoute
        )

    val navigationCameraTiltDegrees =
        if (hasActiveNavigationRoute) {
            NAVIGATION_CAMERA_TILT_DEGREES
        } else {
            FLAT_CAMERA_TILT_DEGREES
        }

    val cameraHeadingEstimator = remember {
        VehicleHeadingEstimator()
    }

    LaunchedEffect(
        readyMap,
        compassLeftMargin,
        compassTopMargin,
        isFollowingLocation
    ) {
        readyMap?.uiSettings?.apply {
            isCompassEnabled = !isFollowingLocation
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
            map.uiSettings.isScrollGesturesEnabled = true
            map.uiSettings.isZoomGesturesEnabled = true
            map.uiSettings.isRotateGesturesEnabled = true
            map.uiSettings.isTiltGesturesEnabled = true

            map.setStyle(
                Style.Builder().fromUri(styleUrl)
            ) { style ->
                val activationResult = runCatching {
                    val componentOptions =
                        LocationComponentOptions
                            .builder(context)
                            .gpsDrawable(
                                R.drawable.ic_navigation_puck
                            )
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
                        RenderMode.GPS

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

                map.addOnMapClickListener {
                    currentOnMapClick()
                    false
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
        val displayBearingDegrees = cameraHeadingEstimator
            .update(location)
            ?.toFloat()

        runCatching {
            map.locationComponent
                .forceLocationUpdate(
                    location.toAndroidLocation(
                        displayBearingDegrees
                    )
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
        availableLocation != null,
        navigationCameraTopPaddingPixels,
        navigationCameraTiltDegrees
    ) {
        val map = readyMap ?: return@LaunchedEffect

        runCatching {
            map.locationComponent.cameraMode =
                if (isFollowingLocation) {
                    CameraMode.TRACKING_GPS
                } else {
                    CameraMode.NONE
                }

            if (
                isFollowingLocation &&
                availableLocation != null
            ) {
                map.locationComponent.paddingWhileTracking(
                    navigationCameraPadding(
                        navigationCameraTopPaddingPixels
                    ),
                    CAMERA_PADDING_TRANSITION_MILLIS
                )
                map.locationComponent.tiltWhileTracking(
                    navigationCameraTiltDegrees,
                    CAMERA_TILT_TRANSITION_MILLIS
                )

                if (!hasInitiallyCentered) {
                    hasInitiallyCentered = true
                    map.locationComponent.zoomWhileTracking(
                        MapConfiguration.USER_LOCATION_ZOOM,
                        INITIAL_CAMERA_TRANSITION_MILLIS
                    )
                }
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
        val displayBearingDegrees = cameraHeadingEstimator
            .update(location)
            ?.toFloat()

        hasInitiallyCentered = true
        runCatching {
            map.locationComponent.forceLocationUpdate(
                location.toAndroidLocation(
                    displayBearingDegrees
                )
            )
            map.locationComponent.cameraMode =
                CameraMode.TRACKING_GPS
            map.locationComponent.paddingWhileTracking(
                navigationCameraPadding(
                    navigationCameraTopPaddingPixels
                ),
                CAMERA_PADDING_TRANSITION_MILLIS
            )
            map.locationComponent.tiltWhileTracking(
                navigationCameraTiltDegrees,
                CAMERA_TILT_TRANSITION_MILLIS
            )
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

    val cameraFocusPaddingPixels = with(density) {
        cameraFocusPadding.roundToPx()
    }

    LaunchedEffect(
        readyMap,
        cameraFocusRequestId,
        cameraFocusPoints,
        cameraFocusPaddingPixels
    ) {
        if (cameraFocusRequestId == 0) {
            return@LaunchedEffect
        }

        val map = readyMap ?: return@LaunchedEffect
        val points = cameraFocusPoints
            .filter(RoutePoint::isValid)
            .distinct()

        if (points.isEmpty()) {
            return@LaunchedEffect
        }

        runCatching {
            map.locationComponent.cameraMode =
                CameraMode.NONE

            val cameraUpdate = if (points.size == 1) {
                val point = points.single()

                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        point.latitude,
                        point.longitude
                    ),
                    ADDRESS_FOCUS_ZOOM
                )
            } else {
                val boundsBuilder =
                    LatLngBounds.Builder()

                points.forEach { point ->
                    boundsBuilder.include(
                        LatLng(
                            point.latitude,
                            point.longitude
                        )
                    )
                }

                CameraUpdateFactory.newLatLngBounds(
                    boundsBuilder.build(),
                    cameraFocusPaddingPixels
                )
            }

            map.animateCamera(
                cameraUpdate,
                CAMERA_FOCUS_TRANSITION_MILLIS
            )
        }.onFailure { exception ->
            Log.e(
                TAG,
                "Failed to focus map camera",
                exception
            )
        }
    }

    LaunchedEffect(
        readyMap,
        routePoints,
        showRouteEndpoints
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
                showEndpoints = showRouteEndpoints,
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
                showEndpoints = showRouteEndpoints,
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
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    mapHeightPixels = size.height
                }
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
    showEndpoints: Boolean,
    state: RouteRenderState
) {
    runCatching {
        style.updateRoute(
            routePoints = points,
            showEndpoints = showEndpoints
        )
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

internal fun navigationCameraTopPaddingPixels(
    mapHeightPixels: Int,
    isLandscape: Boolean,
    hasActiveRoute: Boolean
): Double {
    if (!hasActiveRoute || mapHeightPixels <= 0) {
        return 0.0
    }

    val topPaddingFraction = if (isLandscape) {
        LANDSCAPE_NAVIGATION_TOP_PADDING_FRACTION
    } else {
        PORTRAIT_NAVIGATION_TOP_PADDING_FRACTION
    }

    return mapHeightPixels * topPaddingFraction
}

private fun navigationCameraPadding(
    topPaddingPixels: Double
): DoubleArray = doubleArrayOf(
    0.0,
    topPaddingPixels,
    0.0,
    0.0
)

private const val TAG = "AtlasMap"
private const val INITIAL_CAMERA_TRANSITION_MILLIS = 750L
private const val CAMERA_PADDING_TRANSITION_MILLIS = 650L
private const val CAMERA_FOCUS_TRANSITION_MILLIS = 650
private const val ADDRESS_FOCUS_ZOOM = 15.0
private const val CAMERA_TILT_TRANSITION_MILLIS = 650L
private const val ROUTE_TRANSITION_FRAME_MILLIS = 50L
private const val ROUTE_TRANSITION_FRAME_COUNT = 18
private const val PORTRAIT_NAVIGATION_TOP_PADDING_FRACTION = 0.42
private const val LANDSCAPE_NAVIGATION_TOP_PADDING_FRACTION = 0.24
private const val NAVIGATION_CAMERA_TILT_DEGREES = 50.0
private const val FLAT_CAMERA_TILT_DEGREES = 0.0
