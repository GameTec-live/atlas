package org.gtlv.atlas.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

@Composable
internal fun rememberMapViewWithLifecycle(
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

            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            if (created) mapView.onDestroy()
        }
    }

    return mapView
}