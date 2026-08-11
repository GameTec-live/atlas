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

@SuppressLint("MissingPermission")
@Composable
internal fun AtlasMap(
    locationState: LocationState,
    recenterRequestId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val defaultMapError =
        stringResource(R.string.map_load_error)

    val locationDisplayError =
        stringResource(R.string.map_location_display_error)

    var loadState by remember {
        mutableStateOf<MapLoadState>(MapLoadState.Loading)
    }

    var readyMap by remember {
        mutableStateOf<MapLibreMap?>(null)
    }

    /*
     * These values preserve the camera after rotation. They are also
     * updated whenever the user manually moves the map.
     */
    var savedLatitude by rememberSaveable {
        mutableDoubleStateOf(MapConfiguration.INITIAL_LATITUDE)
    }

    var savedLongitude by rememberSaveable {
        mutableDoubleStateOf(MapConfiguration.INITIAL_LONGITUDE)
    }

    var savedZoom by rememberSaveable {
        mutableDoubleStateOf(MapConfiguration.INITIAL_ZOOM)
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

    LaunchedEffect(mapView) {
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
                    .fromUri(MapConfiguration.STYLE_URL)
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

                    // Manual following keeps pan and zoom gestures enabled.
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

                readyMap = map
                loadState = MapLoadState.Loaded
            }
        }
    }

    val availableLocation =
        (locationState as? LocationState.Available)?.location

    // Every valid update moves both the location puck and the camera.
    LaunchedEffect(
        readyMap,
        availableLocation
    ) {
        val map = readyMap ?: return@LaunchedEffect
        val location =
            availableLocation ?: return@LaunchedEffect

        runCatching {
            map.locationComponent.forceLocationUpdate(
                location.toAndroidLocation()
            )
        }

        map.followLocation(location)
    }

    /*
     * Camera movement caused by the recenter button. Location updates
     * themselves do not trigger this effect.
     */
    LaunchedEffect(
        readyMap,
        recenterRequestId
    ) {
        if (recenterRequestId == 0) {
            return@LaunchedEffect
        }

        val map = readyMap ?: return@LaunchedEffect
        val location =
            availableLocation ?: return@LaunchedEffect

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
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            MapLoadState.Loaded -> Unit

            is MapLoadState.Error -> {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    color =
                        MaterialTheme.colorScheme.errorContainer,
                    contentColor =
                        MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(text = state.message)
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

@Composable
private fun rememberMapViewWithLifecycle(
    initialCameraPosition: CameraPosition
): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)

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
