package org.gtlv.atlas.map

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import org.gtlv.atlas.R
import org.gtlv.atlas.location.toAndroidLocation
import org.gtlv.core.location.AtlasLocation
import org.gtlv.core.location.LocationState
import org.gtlv.core.telemetry.LiveMapUser
import org.gtlv.core.telemetry.TelemetryVehicleState
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import androidx.core.graphics.toColorInt

@SuppressLint("MissingPermission")
@Composable
internal fun AtlasMap(
    locationState: LocationState,
    liveMapUsers: Collection<LiveMapUser>,
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

    var hasInitiallyCentered by rememberSaveable {
        mutableStateOf(false)
    }

    val currentOnUserCameraMove by
    rememberUpdatedState(onUserCameraMove)

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
                            .useDefaultLocationEngine(
                                false
                            )
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
                }

                if (activationResult.isFailure) {
                    loadState = MapLoadState.Error(
                        message =
                            locationDisplayError
                    )

                    return@setStyle
                }

                val layerResult = runCatching {
                    addLiveMapUserLayers(style)
                }

                layerResult.exceptionOrNull()?.let {
                        exception ->
                }

                map.addOnCameraIdleListener {
                    val camera =
                        map.cameraPosition

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
        map.centerOnLocation(location)
    }

    LaunchedEffect(
        readyMap,
        liveMapUsers
    ) {
        val style =
            readyMap?.style
                ?: return@LaunchedEffect

        val users = liveMapUsers.toList()

        updateLiveMapSource(
            style = style,
            sourceId =
                LIVE_MAP_USERS_FREE_SOURCE_ID,
            users = users.filter {
                it.state ==
                        TelemetryVehicleState.FREE
            }
        )

        updateLiveMapSource(
            style = style,
            sourceId =
                LIVE_MAP_USERS_ON_THE_WAY_SOURCE_ID,
            users = users.filter {
                it.state ==
                        TelemetryVehicleState.ON_THE_WAY
            }
        )

        updateLiveMapSource(
            style = style,
            sourceId =
                LIVE_MAP_USERS_OCCUPIED_SOURCE_ID,
            users = users.filter {
                it.state ==
                        TelemetryVehicleState.OCCUPIED
            }
        )

        updateLiveMapSource(
            style = style,
            sourceId =
                LIVE_MAP_USERS_AWAY_SOURCE_ID,
            users = users.filter {
                it.state ==
                        TelemetryVehicleState.AWAY
            }
        )

        updateLiveMapSource(
            style = style,
            sourceId =
                LIVE_MAP_USERS_LABEL_SOURCE_ID,
            users = users
        )
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
                        MaterialTheme
                            .colorScheme
                            .errorContainer,
                    contentColor =
                        MaterialTheme
                            .colorScheme
                            .onErrorContainer
                ) {
                    Text(
                        text = state.message
                    )
                }
            }
        }
    }
}

private fun addLiveMapUserLayers(
    style: Style
) {
    addLiveMapSource(
        style = style,
        sourceId =
            LIVE_MAP_USERS_FREE_SOURCE_ID
    )

    addLiveMapSource(
        style = style,
        sourceId =
            LIVE_MAP_USERS_ON_THE_WAY_SOURCE_ID
    )

    addLiveMapSource(
        style = style,
        sourceId =
            LIVE_MAP_USERS_OCCUPIED_SOURCE_ID
    )

    addLiveMapSource(
        style = style,
        sourceId =
            LIVE_MAP_USERS_AWAY_SOURCE_ID
    )

    addLiveMapSource(
        style = style,
        sourceId =
            LIVE_MAP_USERS_LABEL_SOURCE_ID
    )

    addCircleLayer(
        style = style,
        layerId =
            LIVE_MAP_USERS_FREE_LAYER_ID,
        sourceId =
            LIVE_MAP_USERS_FREE_SOURCE_ID,
        color = "#10B981".toColorInt()
    )

    addCircleLayer(
        style = style,
        layerId =
            LIVE_MAP_USERS_ON_THE_WAY_LAYER_ID,
        sourceId =
            LIVE_MAP_USERS_ON_THE_WAY_SOURCE_ID,
        color = "#3B82F6".toColorInt()
    )

    addCircleLayer(
        style = style,
        layerId =
            LIVE_MAP_USERS_OCCUPIED_LAYER_ID,
        sourceId =
            LIVE_MAP_USERS_OCCUPIED_SOURCE_ID,
        color = "#F59E0B".toColorInt()
    )

    addCircleLayer(
        style = style,
        layerId =
            LIVE_MAP_USERS_AWAY_LAYER_ID,
        sourceId =
            LIVE_MAP_USERS_AWAY_SOURCE_ID,
        color = "#94A3B8".toColorInt()
    )

    style.addLayer(
        SymbolLayer(
            LIVE_MAP_USERS_LABEL_LAYER_ID,
            LIVE_MAP_USERS_LABEL_SOURCE_ID
        ).withProperties(
            PropertyFactory.textField(
                Expression.toString(
                    Expression.get(
                        MAP_USER_NAME_PROPERTY
                    )
                )
            ),
            PropertyFactory.textFont(
                arrayOf("Noto Sans Regular")
            ),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor(
                Color.BLACK
            ),
            PropertyFactory.textHaloColor(
                Color.WHITE
            ),
            PropertyFactory.textHaloWidth(2f),
            PropertyFactory.textAnchor(
                Property.TEXT_ANCHOR_TOP
            ),
            PropertyFactory.textOffset(
                arrayOf(0f, 1.4f)
            ),
            PropertyFactory.textAllowOverlap(
                true
            ),
            PropertyFactory.textIgnorePlacement(
                true
            ),
            PropertyFactory.textOptional(true)
        )
    )
}

private fun addLiveMapSource(
    style: Style,
    sourceId: String
) {
    style.addSource(
        GeoJsonSource(
            sourceId,
            emptyFeatureCollection()
        )
    )
}

private fun addCircleLayer(
    style: Style,
    layerId: String,
    sourceId: String,
    color: Int
) {
    style.addLayer(
        CircleLayer(
            layerId,
            sourceId
        ).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(color),
            PropertyFactory.circleStrokeColor(
                Color.WHITE
            ),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleOpacity(1f)
        )
    )
}

private fun updateLiveMapSource(
    style: Style,
    sourceId: String,
    users: Collection<LiveMapUser>
) {
    val source =
        style.getSourceAs<GeoJsonSource>(
            sourceId
        )

    if (source == null) {
        return
    }

    source.setGeoJson(
        users.toFeatureCollection()
    )
}

private fun Collection<LiveMapUser>
        .toFeatureCollection():
        FeatureCollection {
    val features = map { mapUser ->
        val properties = JsonObject().apply {
            addProperty(
                MAP_USER_NAME_PROPERTY,
                mapUser.userName
            )
        }

        Feature.fromGeometry(
            Point.fromLngLat(
                mapUser.longitude,
                mapUser.latitude
            ),
            properties
        )
    }

    return FeatureCollection.fromFeatures(
        features.toTypedArray()
    )
}

private fun emptyFeatureCollection():
        FeatureCollection {
    return FeatureCollection.fromFeatures(
        emptyArray<Feature>()
    )
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

        val options =
            MapLibreMapOptions
                .createFromAttributes(context)
                .camera(initialCameraPosition)

        MapView(context, options)
    }

    DisposableEffect(lifecycle, mapView) {
        var created = false
        var started = false
        var resumed = false

        val observer =
            LifecycleEventObserver { _, event ->
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

private const val TAG = "AtlasMap"

private const val LIVE_MAP_USERS_FREE_SOURCE_ID =
    "atlas-live-map-users-free-source"

private const val LIVE_MAP_USERS_ON_THE_WAY_SOURCE_ID =
    "atlas-live-map-users-on-the-way-source"

private const val LIVE_MAP_USERS_OCCUPIED_SOURCE_ID =
    "atlas-live-map-users-occupied-source"

private const val LIVE_MAP_USERS_AWAY_SOURCE_ID =
    "atlas-live-map-users-away-source"

private const val LIVE_MAP_USERS_LABEL_SOURCE_ID =
    "atlas-live-map-users-label-source"

private const val LIVE_MAP_USERS_FREE_LAYER_ID =
    "atlas-live-map-users-free-layer"

private const val LIVE_MAP_USERS_ON_THE_WAY_LAYER_ID =
    "atlas-live-map-users-on-the-way-layer"

private const val LIVE_MAP_USERS_OCCUPIED_LAYER_ID =
    "atlas-live-map-users-occupied-layer"

private const val LIVE_MAP_USERS_AWAY_LAYER_ID =
    "atlas-live-map-users-away-layer"

private const val LIVE_MAP_USERS_LABEL_LAYER_ID =
    "atlas-live-map-users-label-layer"

private const val MAP_USER_NAME_PROPERTY =
    "userName"