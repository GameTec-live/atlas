package org.gtlv.atlas.map

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
internal fun AtlasMap(
    modifier: Modifier = Modifier
) {
    var loadState by remember {
        mutableStateOf<MapLoadState>(MapLoadState.Loading)
    }

    val mapView = rememberMapViewWithLifecycle()

    LaunchedEffect(mapView) {
        loadState = MapLoadState.Loading

        mapView.addOnDidFailLoadingMapListener { message ->
            loadState = MapLoadState.Error(
                message = message.ifBlank {
                    "The map could not be loaded."
                }
            )
        }

        mapView.getMapAsync { map ->
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false

            map.setStyle(
                Style.Builder()
                    .fromUri(MapConfiguration.STYLE_URL)
            ) {
                loadState = MapLoadState.Loaded
            }
        }
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
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor =
                        MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(text = state.message)
                }
            }
        }
    }
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)

        val options = MapLibreMapOptions
            .createFromAttributes(context)
            .camera(
                CameraPosition.Builder()
                    .target(
                        LatLng(
                            MapConfiguration.INITIAL_LATITUDE,
                            MapConfiguration.INITIAL_LONGITUDE
                        )
                    )
                    .zoom(MapConfiguration.INITIAL_ZOOM)
                    .build()
            )

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