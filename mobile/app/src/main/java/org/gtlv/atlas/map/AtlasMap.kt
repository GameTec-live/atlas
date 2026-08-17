package org.gtlv.atlas.map

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.gtlv.atlas.R
import org.gtlv.atlas.location.toAndroidLocation
import org.gtlv.core.location.AtlasLocation
import org.gtlv.core.location.LocationState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import android.content.res.Configuration
import android.view.Gravity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

@SuppressLint("MissingPermission")
@Composable
internal fun AtlasMap(
    locationState: LocationState,
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
        stringResource(R.string.map_location_display_error)

    var loadState by remember {
        mutableStateOf<MapLoadState>(
            MapLoadState.Loading
        )
    }

    var readyMap by remember {
        mutableStateOf<MapLibreMap?>(null)
    }

    LaunchedEffect(
        readyMap,
        compassLeftMargin,
        compassTopMargin
    ) {
        readyMap?.uiSettings?.apply {
            compassGravity = Gravity.TOP or Gravity.START

            setCompassMargins(
                compassLeftMargin,
                compassTopMargin,
                0,
                0
            )
        }
    }

    var hasInitiallyCentered by rememberSaveable {
        mutableStateOf(false)
    }

    /*
     * The MapLibre listener is registered outside normal
     * recomposition. This ensures that it always calls the latest
     * callback supplied by MainScreen.
     */
    val currentOnUserCameraMove by rememberUpdatedState(
        onUserCameraMove
    )

    /*
     * These values preserve the camera after configuration changes.
     */
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

    val initialCameraPosition = CameraPosition.Builder()
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
        initialCameraPosition = initialCameraPosition
    )

    LaunchedEffect(mapView, styleUrl) {
        loadState = MapLoadState.Loading
        readyMap = null

        mapView.addOnDidFailLoadingMapListener {
            loadState = MapLoadState.Error(
                message = defaultMapError
            )
        }

        mapView.getMapAsync { map ->
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false

            map.setStyle(
                Style.Builder()
                    .fromUri(styleUrl)
            ) { style ->
                val activationResult = runCatching {
                    val componentOptions =
                        LocationComponentOptions
                            .builder(context)
                            .pulseEnabled(true)
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
                        .isLocationComponentEnabled = true

                    /*
                     * The app controls camera following manually.
                     */
                    map.locationComponent.cameraMode =
                        CameraMode.NONE

                    map.locationComponent.renderMode =
                        RenderMode.NORMAL
                }

                if (activationResult.isFailure) {
                    loadState = MapLoadState.Error(
                        message = locationDisplayError
                    )

                    return@setStyle
                }

                map.addOnCameraIdleListener {
                    val camera = map.cameraPosition
                    val target = camera.target

                    if (target != null) {
                        savedLatitude = target.latitude
                        savedLongitude = target.longitude
                    }

                    savedZoom = camera.zoom
                    savedBearing = camera.bearing
                    savedTilt = camera.tilt
                }

                map.addOnCameraMoveStartedListener { reason ->
                    if (
                        reason == MapLibreMap
                            .OnCameraMoveStartedListener
                            .REASON_API_GESTURE
                    ) {
                        /*
                         * Only user interaction stops camera
                         * following. Programmatic movement does not.
                         */
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

    /*
     * Every location update moves the puck.
     *
     * The first available location also uses the same center and
     * zoom operation as the recenter button. Later updates follow
     * the puck without resetting the zoom.
     *
     * After the user moves the camera, isFollowingLocation becomes
     * false. The puck continues moving, but the camera stays where
     * the user placed it.
     */
    LaunchedEffect(
        readyMap,
        availableLocation
    ) {
        val map = readyMap
            ?: return@LaunchedEffect

        val location = availableLocation
            ?: return@LaunchedEffect

        runCatching {
            map.locationComponent.forceLocationUpdate(
                location.toAndroidLocation()
            )
        }

        if (isFollowingLocation) {
            if (!hasInitiallyCentered) {
                hasInitiallyCentered = true
                map.centerOnLocation(location)
            } else {
                map.followLocation(location)
            }
        }
    }

    /*
     * Pressing the recenter button centers and zooms the camera.
     * MainScreen also changes isFollowingLocation to true, so future
     * location updates continue following the puck.
     */
    LaunchedEffect(
        readyMap,
        recenterRequestId
    ) {
        if (recenterRequestId == 0) {
            return@LaunchedEffect
        }

        val map = readyMap
            ?: return@LaunchedEffect

        val location = availableLocation
            ?: return@LaunchedEffect

        hasInitiallyCentered = true
        map.centerOnLocation(location)
    }

    Box(
        modifier = modifier
    ) {
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
                        MaterialTheme.colorScheme.errorContainer,
                    contentColor =
                        MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(
                        text = state.message
                    )
                }
            }
        }
    }
}

private fun MapLibreMap.centerOnLocation(
    location: AtlasLocation
) {
    animateCamera(
        CameraUpdateFactory.newLatLngZoom(
            LatLng(
                location.latitude,
                location.longitude
            ),
            MapConfiguration.USER_LOCATION_ZOOM
        )
    )
}

private fun MapLibreMap.followLocation(
    location: AtlasLocation
) {
    animateCamera(
        CameraUpdateFactory.newLatLng(
            LatLng(
                location.latitude,
                location.longitude
            )
        )
    )
}

@Composable
private fun rememberMapViewWithLifecycle(
    initialCameraPosition: CameraPosition
): MapView {
    val context = LocalContext.current
    val lifecycle =
        LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        MapLibre.getInstance(
            context.applicationContext
        )

        val options = MapLibreMapOptions
            .createFromAttributes(context)
            .camera(initialCameraPosition)

        MapView(context, options)
    }

    DisposableEffect(lifecycle, mapView) {
        var created = false
        var started = false
        var resumed = false

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    if (!created) {
                        mapView.onCreate(null)
                        created = true
                    }
                }

                Lifecycle.Event.ON_START -> {
                    if (!started) {
                        mapView.onStart()
                        started = true
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (!resumed) {
                        mapView.onResume()
                        resumed = true
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    if (resumed) {
                        mapView.onPause()
                        resumed = false
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    if (started) {
                        mapView.onStop()
                        started = false
                    }
                }

                Lifecycle.Event.ON_DESTROY -> {
                    if (created) {
                        mapView.onDestroy()
                        created = false
                    }
                }

                Lifecycle.Event.ON_ANY -> Unit
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)

            if (resumed) {
                mapView.onPause()
            }

            if (started) {
                mapView.onStop()
            }

            if (created) {
                mapView.onDestroy()
            }
        }
    }

    return mapView
}
