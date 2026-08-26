package org.gtlv.car_common.screen

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.util.Log
import android.view.Surface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import org.gtlv.core.location.AtlasLocation
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
import kotlin.math.abs
import kotlin.math.ln

/** Renders MapLibre into the surface supplied by the Android Auto host. */
internal class MapLibreSurfaceRenderer(
    private val carContext: CarContext,
) : SurfaceCallback {
    private val displayManager = carContext.getSystemService(
        Context.DISPLAY_SERVICE,
    ) as DisplayManager

    private var outputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var rootView: FrameLayout? = null
    private var jobCardView: LinearLayout? = null
    private var jobQueueView: TextView? = null
    private var jobSummaryView: TextView? = null
    private var styleUrl: String? = null
    private var lastLocation: AtlasLocation? = null
    private var isStyleReady = false
    private var isFollowingLocation = true
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var visibleArea = Rect()
    private var stableArea = Rect()
    private var appliedMapPadding: IntArray? = null
    private var jobSummary = carContext.getString(
        org.gtlv.car_common.R.string.driver_loading_job,
    )
    private var queuedJobCount = 0

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        if (
            !surface.isValid ||
            surfaceContainer.width <= 0 ||
            surfaceContainer.height <= 0 ||
            surfaceContainer.dpi <= 0
        ) {
            if (surface === outputSurface) {
                destroyDisplay()
            } else {
                surface.release()
            }
            return
        }

        // Hosts may report size or DPI changes using the same Surface.
        destroyDisplay(releaseSurface = outputSurface !== surface)
        outputSurface = surface
        surfaceWidth = surfaceContainer.width
        surfaceHeight = surfaceContainer.height

        MapLibre.getInstance(carContext.applicationContext)

        val newVirtualDisplay = displayManager.createVirtualDisplay(
            DISPLAY_NAME,
            surfaceContainer.width,
            surfaceContainer.height,
            surfaceContainer.dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        ) ?: run {
            destroyDisplay()
            return
        }

        val display = newVirtualDisplay.display ?: run {
            newVirtualDisplay.release()
            destroyDisplay()
            return
        }
        virtualDisplay = newVirtualDisplay

        val mapContext = carContext.createDisplayContext(display)
        val options = MapLibreMapOptions
            .createFromAttributes(mapContext)
            .textureMode(true)
            .camera(
                CameraPosition.Builder()
                    .target(LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE))
                    .zoom(INITIAL_ZOOM)
                    .build(),
            )
        val newMapView = MapView(mapContext, options)
        val newPresentation = Presentation(carContext, display)
        val newRootView = createMapLayout(mapContext, newMapView)

        mapView = newMapView
        rootView = newRootView
        presentation = newPresentation
        newMapView.onCreate(null)
        newPresentation.setContentView(newRootView)
        newPresentation.show()
        newMapView.onStart()
        newMapView.onResume()

        newMapView.getMapAsync { readyMap ->
            if (mapView !== newMapView) return@getMapAsync

            map = readyMap
            jobCardView?.bringToFront()
            readyMap.uiSettings.apply {
                isAttributionEnabled = false
                isLogoEnabled = false
                isCompassEnabled = false
            }
            applyVisibleArea()
            styleUrl?.let(::loadStyle)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        val destroyedSurface = surfaceContainer.surface
        if (destroyedSurface == null || destroyedSurface === outputSurface) {
            destroyDisplay()
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = Rect(visibleArea)
        applyVisibleArea()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        this.stableArea = Rect(stableArea)
        applyOverlayInsets()
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        stopFollowingLocation()
        map?.scrollBy(-distanceX, -distanceY)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        stopFollowingLocation()
        map?.scrollBy(
            -velocityX * FLING_SECONDS,
            -velocityY * FLING_SECONDS,
            FLING_DURATION_MILLIS.toLong(),
        )
    }

    override fun onScale(
        focusX: Float,
        focusY: Float,
        scaleFactor: Float,
    ) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
        stopFollowingLocation()
        map?.moveCamera(
            CameraUpdateFactory.zoomBy(
                ln(scaleFactor.toDouble()) / ln(2.0),
            ),
        )
    }

    fun setStyleUrl(styleUrl: String) {
        if (this.styleUrl == styleUrl) return
        this.styleUrl = styleUrl
        map?.let { loadStyle(styleUrl) }
    }

    fun updateLocation(location: AtlasLocation) {
        lastLocation = location
        val readyMap = map ?: return
        if (!isStyleReady) return

        updateLocationPuck(readyMap, location)
        if (isFollowingLocation) {
            enableLocationTracking(readyMap)
        }
    }

    fun updateJobSummary(summary: String) {
        jobSummary = summary
        jobSummaryView?.text = summary
    }

    fun updateQueuedJobCount(count: Int) {
        queuedJobCount = count.coerceAtLeast(0)
        jobQueueView?.text = queuedJobText()
    }

    fun zoomIn() {
        stopFollowingLocation()
        map?.animateCamera(CameraUpdateFactory.zoomIn())
    }

    fun zoomOut() {
        stopFollowingLocation()
        map?.animateCamera(CameraUpdateFactory.zoomOut())
    }

    fun recenter() {
        isFollowingLocation = true
        val location = lastLocation ?: return
        map?.let { readyMap ->
            updateLocationPuck(readyMap, location)
            enableLocationTracking(readyMap)
        }
    }

    fun stopFollowingLocation() {
        isFollowingLocation = false
        runCatching {
            map?.locationComponent?.cameraMode = CameraMode.NONE
        }
        applyVisibleArea()
    }

    fun release() {
        destroyDisplay()
    }

    private fun loadStyle(url: String) {
        val readyMap = map ?: return
        isStyleReady = false

        readyMap.setStyle(Style.Builder().fromUri(url)) { style ->
            if (map !== readyMap || styleUrl != url) return@setStyle

            activateLocationPuck(readyMap, style)
            isStyleReady = true
            lastLocation?.let { location ->
                updateLocationPuck(readyMap, location)
                if (isFollowingLocation) {
                    enableLocationTracking(readyMap)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun activateLocationPuck(
        readyMap: MapLibreMap,
        style: Style,
    ) {
        runCatching {
            val context = mapView?.context ?: carContext
            val options = LocationComponentOptions
                .builder(context)
                .pulseEnabled(true)
                .minZoomIconScale(LOCATION_MARKER_SCALE)
                .maxZoomIconScale(LOCATION_MARKER_SCALE)
                .build()
            val activationOptions = LocationComponentActivationOptions
                .builder(context, style)
                .locationComponentOptions(options)
                .useDefaultLocationEngine(false)
                .build()

            readyMap.locationComponent.activateLocationComponent(
                activationOptions,
            )
            readyMap.locationComponent.isLocationComponentEnabled = true
            readyMap.locationComponent.cameraMode = CameraMode.NONE
            readyMap.locationComponent.renderMode = RenderMode.GPS
            readyMap.locationComponent.setMaxAnimationFps(60)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationPuck(
        readyMap: MapLibreMap,
        location: AtlasLocation,
    ) {
        runCatching {
            readyMap.locationComponent.forceLocationUpdate(
                location.toAndroidLocation(),
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationTracking(readyMap: MapLibreMap) {
        runCatching {
            val component = readyMap.locationComponent
            val bearing = lastLocation?.bearingDegrees?.toDouble()
            val cameraMode = if (bearing != null) {
                CameraMode.TRACKING_GPS
            } else {
                CameraMode.TRACKING
            }
            component.renderMode = RenderMode.GPS

            applyVisibleArea()
            if (component.cameraMode != cameraMode) {
                component.setCameraMode(
                    cameraMode,
                    RECENTER_DURATION_MILLIS,
                    FOLLOW_ZOOM,
                    bearing,
                    FOLLOW_TILT,
                    null,
                )
                return@runCatching
            }

            if (readyMap.cameraPosition.zoom < FOLLOW_ZOOM) {
                component.zoomWhileTracking(FOLLOW_ZOOM, RECENTER_DURATION_MILLIS)
            }
            if (abs(readyMap.cameraPosition.tilt - FOLLOW_TILT) > 0.5) {
                component.tiltWhileTracking(FOLLOW_TILT, RECENTER_DURATION_MILLIS)
            }
        }
    }

    private fun applyVisibleArea() {
        val readyMap = map ?: return
        val area = visibleArea
        if (area.isEmpty || surfaceWidth <= 0 || surfaceHeight <= 0) return
        val followTopPadding = if (
            isFollowingLocation && isStyleReady && lastLocation != null
        ) {
            (area.height() * FOLLOW_TOP_PADDING_FRACTION).toInt()
        } else {
            0
        }

        val padding = intArrayOf(
            area.left.coerceAtLeast(0),
            (area.top + followTopPadding).coerceAtLeast(0),
            (surfaceWidth - area.right).coerceAtLeast(0),
            (surfaceHeight - area.bottom).coerceAtLeast(0),
        )
        if (appliedMapPadding?.contentEquals(padding) == true) return

        readyMap.setPadding(padding[0], padding[1], padding[2], padding[3])
        appliedMapPadding = padding
    }

    private fun createMapLayout(
        context: Context,
        newMapView: MapView,
    ): FrameLayout {
        val root = FrameLayout(context)
        root.clipChildren = false
        root.addView(
            newMapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val jobCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = roundedBackground(
                color = Color.argb(230, 32, 33, 36),
                radiusDp = 18,
            )
            elevation = dp(12).toFloat()
        }
        jobCardView = jobCard
        val queueView = TextView(context).apply {
            text = queuedJobText()
            setTextColor(Color.rgb(210, 213, 218))
            textSize = 20f
        }
        jobQueueView = queueView
        jobCard.addView(queueView)
        jobCard.addView(
            TextView(context).apply {
                text = carContext.getString(
                    org.gtlv.car_common.R.string.driver_current_job,
                )
                setTextColor(Color.WHITE)
                textSize = 26f
            },
        )
        val summaryView = TextView(context).apply {
            text = jobSummary
            setTextColor(Color.rgb(210, 213, 218))
            textSize = 22f
        }
        jobSummaryView = summaryView
        jobCard.addView(summaryView)

        newMapView.addView(
            jobCard,
            FrameLayout.LayoutParams(
                dp(360),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ).apply {
                leftMargin = dp(24)
                bottomMargin = dp(24)
            },
        )

        root.post {
            applyOverlayInsets()
            jobCard.bringToFront()
            Log.d(
                LOG_TAG,
                "overlay root=${root.width}x${root.height} " +
                    "job=${jobCard.left},${jobCard.top}-${jobCard.right},${jobCard.bottom}",
            )
        }
        return root
    }

    private fun applyOverlayInsets() {
        val area = stableArea
        if (area.isEmpty || surfaceWidth <= 0 || surfaceHeight <= 0) return

        (jobSummaryView?.parent as? LinearLayout)?.layoutParams
            ?.let { it as? FrameLayout.LayoutParams }
            ?.apply {
                leftMargin = area.left.coerceAtLeast(0) + dp(24)
                bottomMargin = (surfaceHeight - area.bottom).coerceAtLeast(0) + dp(24)
                (jobSummaryView?.parent as? LinearLayout)?.layoutParams = this
            }
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Int,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * carContext.resources.displayMetrics.density).toInt()

    private fun queuedJobText(): String =
        carContext.resources.getQuantityString(
            org.gtlv.car_common.R.plurals.driver_jobs_in_queue,
            queuedJobCount,
            queuedJobCount,
        )

    private fun destroyDisplay(releaseSurface: Boolean = true) {
        val oldMapView = mapView
        map = null
        mapView = null
        rootView = null
        jobCardView = null
        jobQueueView = null
        jobSummaryView = null
        appliedMapPadding = null
        isStyleReady = false

        if (oldMapView != null) {
            runCatching { oldMapView.onPause() }
            runCatching { oldMapView.onStop() }
            runCatching { oldMapView.onDestroy() }
        }

        runCatching { presentation?.dismiss() }
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        if (releaseSurface) {
            outputSurface?.release()
        }
        outputSurface = null
        surfaceWidth = 0
        surfaceHeight = 0
    }

    private fun AtlasLocation.toAndroidLocation(): Location =
        Location("atlas-car").also { androidLocation ->
            androidLocation.latitude = latitude
            androidLocation.longitude = longitude
            androidLocation.time = timestampMillis
            accuracyMeters?.let { androidLocation.accuracy = it }
            bearingDegrees?.let { androidLocation.bearing = it }
            speedMetersPerSecond?.let { androidLocation.speed = it }
        }

    private companion object {
        const val DISPLAY_NAME = "Atlas Android Auto map"
        const val LOG_TAG = "AtlasCarMap"
        const val INITIAL_LATITUDE = 48.500
        const val INITIAL_LONGITUDE = 14.580
        const val INITIAL_ZOOM = 13.0
        const val FOLLOW_ZOOM = 16.5
        const val FOLLOW_TILT = 45.0
        const val FOLLOW_TOP_PADDING_FRACTION = 0.35f
        const val LOCATION_MARKER_SCALE = 1.5f
        const val RECENTER_DURATION_MILLIS = 500L
        const val FLING_SECONDS = 0.18f
        const val FLING_DURATION_MILLIS = 400
    }
}
