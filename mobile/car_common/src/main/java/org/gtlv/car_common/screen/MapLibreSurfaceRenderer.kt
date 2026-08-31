package org.gtlv.car_common.screen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import org.gtlv.core.location.AtlasLocation
import org.gtlv.core.map.addLiveMapUserLayers
import org.gtlv.core.map.liveMapMarkerColor
import org.gtlv.core.map.updateLiveMapUsers
import org.gtlv.core.geoservice.RoutePoint
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
import org.maplibre.android.maps.widgets.CompassView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

/** Renders MapLibre into the surface supplied by the Android Auto host. */
internal class MapLibreSurfaceRenderer(
    private val carContext: CarContext,
    private val showDispatcherDriverList: Boolean = false,
) : SurfaceCallback {
    private val displayManager = carContext.getSystemService(
        Context.DISPLAY_SERVICE,
    ) as DisplayManager

    private var outputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var mapCompassView: CompassView? = null
    private var map: MapLibreMap? = null
    private var rootView: FrameLayout? = null
    private var jobCardView: LinearLayout? = null
    private var jobCardToggleView: ImageView? = null
    private var jobQueueView: TextView? = null
    private var jobTitleView: TextView? = null
    private var jobSummaryView: TextView? = null
    private var dispatcherSidebarView: LinearLayout? = null
    private var dispatcherSidebarToggleView: TextView? = null
    private var dispatcherUserScrollView: ScrollView? = null
    private var dispatcherUserRowsView: LinearLayout? = null
    private var dispatcherUserCountView: TextView? = null
    private var styleUrl: String? = null
    private var lastLocation: AtlasLocation? = null
    private var isStyleReady = false
    private var isFollowingLocation = true
    private var isNorthUp = false
    private var selectedFollowZoom = FOLLOW_ZOOM
    private var selectedFollowTilt = FOLLOW_TILT
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var renderDensity = 1f
    private var visibleArea = Rect()
    private var stableArea = Rect()
    private var appliedMapPadding: IntArray? = null
    private var interactionTarget = InteractionTarget.MAP
    private var isDispatcherSidebarExpanded = false
    private var dispatcherSidebarAnimator: ValueAnimator? = null
    private var isJobCardExpanded = true
    private var jobCardAnimator: ValueAnimator? = null
    private var jobSummary = carContext.getString(
        org.gtlv.car_common.R.string.driver_loading_job,
    )
    private var queuedJobCount = 0
    private var sidebarUsers: List<SidebarUser> = emptyList()
    private var liveMapUsers: List<LiveMapUser> = emptyList()
    private var routePoints: List<RoutePoint> = emptyList()

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
        val renderDpi = responsiveRenderDpi(
            width = surfaceContainer.width,
            height = surfaceContainer.height,
            hostDpi = surfaceContainer.dpi,
        )
        renderDensity = renderDpi / BASE_DENSITY_DPI.toFloat()

        MapLibre.getInstance(carContext.applicationContext)

        val newVirtualDisplay = displayManager.createVirtualDisplay(
            DISPLAY_NAME,
            surfaceContainer.width,
            surfaceContainer.height,
            renderDpi,
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
            mapCompassView = newMapView.findCompassView()
            jobCardView?.bringToFront()
            jobCardToggleView?.bringToFront()
            readyMap.uiSettings.apply {
                isAttributionEnabled = false
                isLogoEnabled = false
                isCompassEnabled = true
                compassGravity = Gravity.TOP or Gravity.START
                setCompassFadeFacingNorth(false)
            }
            configureMapCompass()
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
        val driverScrollView = dispatcherUserScrollView
        if (
            driverScrollView != null &&
            interactionTarget == InteractionTarget.SIDEBAR &&
            isDispatcherSidebarScrollable()
        ) {
            driverScrollView.scrollBy(0, distanceY.toInt())
            return
        }

        stopFollowingLocation()
        map?.scrollBy(-distanceX, -distanceY)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        val driverScrollView = dispatcherUserScrollView
        if (
            driverScrollView != null &&
            interactionTarget == InteractionTarget.SIDEBAR &&
            isDispatcherSidebarScrollable()
        ) {
            driverScrollView.fling(-velocityY.toInt())
            return
        }

        stopFollowingLocation()
        map?.scrollBy(
            -velocityX * FLING_SECONDS,
            -velocityY * FLING_SECONDS,
            FLING_DURATION_MILLIS.toLong(),
        )
    }

    override fun onClick(x: Float, y: Float) {
        if (dispatcherSidebarAnimator != null || jobCardAnimator != null) return

        if (isMapCompassClick(x, y)) {
            resetBearingNorth()
            return
        }

        if (isJobCardToggleClick(x, y)) {
            setJobCardExpanded(!isJobCardExpanded)
            return
        }

        if (isDispatcherSidebarToggleClick(x, y)) {
            setDispatcherSidebarExpanded(!isDispatcherSidebarExpanded)
            return
        }

        val target = if (
            showDispatcherDriverList &&
            isDispatcherSidebarExpanded &&
            isDispatcherSidebarScrollable() &&
            x >= 0f &&
            x < dispatcherSidebarWidth().toFloat()
        ) {
            InteractionTarget.SIDEBAR
        } else {
            InteractionTarget.MAP
        }

        interactionTarget = target
    }

    private fun isDispatcherSidebarScrollable(): Boolean =
        dispatcherUserScrollView?.let { scrollView ->
            scrollView.canScrollVertically(-1) ||
                scrollView.canScrollVertically(1)
        } == true

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

    fun updateRoute(points: List<RoutePoint>) {
        routePoints = points.filter(RoutePoint::isValid)
        if (!isStyleReady) return
        map?.style?.updateAutomotiveRoute(routePoints)
    }

    fun updateJobSummary(summary: String) {
        jobSummary = summary
        jobSummaryView?.text = summary
        jobCardView?.post(::positionJobCardToggle)
    }

    fun updateQueuedJobCount(count: Int) {
        queuedJobCount = count.coerceAtLeast(0)
        jobQueueView?.text = queuedJobText()
        jobCardView?.post(::positionJobCardToggle)
    }

    fun updateLiveUsers(users: Collection<LiveMapUser>) {
        liveMapUsers = users.toList()
        if (isStyleReady) {
            map?.style?.updateLiveMapUsers(liveMapUsers)
        }

        val updatedUsers = users
            .map { user ->
                SidebarUser(
                    userId = user.userId,
                    name = user.userName,
                    state = user.state,
                )
            }
            .sortedWith(
                compareBy<SidebarUser>(
                    { user -> user.state.sidebarSortOrder() },
                    { user -> user.name.lowercase() },
                ),
            )

        if (sidebarUsers == updatedUsers) return
        sidebarUsers = updatedUsers
        renderDispatcherUsers()
    }

    fun zoomIn() {
        changeZoom(ZOOM_STEP)
    }

    fun zoomOut() {
        changeZoom(-ZOOM_STEP)
    }

    @SuppressLint("MissingPermission")
    private fun changeZoom(change: Double) {
        val readyMap = map ?: return
        val currentZoom = if (isFollowingLocation) {
            selectedFollowZoom
        } else {
            readyMap.cameraPosition.zoom
        }
        val targetZoom = (currentZoom + change).coerceIn(
            MIN_USER_ZOOM,
            MAX_USER_ZOOM,
        )
        selectedFollowZoom = targetZoom

        if (isFollowingLocation && isStyleReady && lastLocation != null) {
            runCatching {
                readyMap.locationComponent.zoomWhileTracking(
                    targetZoom,
                    ZOOM_DURATION_MILLIS.toLong(),
                )
            }.onFailure {
                readyMap.animateCamera(
                    CameraUpdateFactory.zoomTo(targetZoom),
                    ZOOM_DURATION_MILLIS,
                )
            }
        } else {
            readyMap.animateCamera(
                CameraUpdateFactory.zoomTo(targetZoom),
                ZOOM_DURATION_MILLIS,
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun cycleTilt() {
        val readyMap = map ?: return
        val currentIndex = TILT_LEVELS.indices.minByOrNull { index ->
            abs(TILT_LEVELS[index] - selectedFollowTilt)
        } ?: 0
        val targetTilt = TILT_LEVELS[(currentIndex + 1) % TILT_LEVELS.size]
        selectedFollowTilt = targetTilt

        if (isFollowingLocation && isStyleReady && lastLocation != null) {
            runCatching {
                readyMap.locationComponent.tiltWhileTracking(
                    targetTilt,
                    TILT_DURATION_MILLIS,
                )
            }.onFailure {
                animateTilt(readyMap, targetTilt)
            }
        } else {
            animateTilt(readyMap, targetTilt)
        }
    }

    private fun animateTilt(readyMap: MapLibreMap, targetTilt: Double) {
        readyMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(readyMap.cameraPosition)
                    .tilt(targetTilt)
                    .build(),
            ),
            TILT_DURATION_MILLIS.toInt(),
        )
    }

    fun recenter() {
        isFollowingLocation = true
        isNorthUp = false
        val location = lastLocation ?: return
        map?.let { readyMap ->
            updateLocationPuck(readyMap, location)
            enableLocationTracking(readyMap)
        }
    }

    private fun resetBearingNorth() {
        isNorthUp = true
        isFollowingLocation = false
        val readyMap = map ?: return
        runCatching {
            readyMap.locationComponent.cameraMode = CameraMode.NONE
        }
        readyMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(readyMap.cameraPosition)
                    .bearing(NORTH_BEARING_DEGREES)
                    .build(),
            ),
            COMPASS_RESET_DURATION_MILLIS,
        )
        applyVisibleArea()
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
            style.addAutomotiveRouteLayers()
            style.updateAutomotiveRoute(routePoints)
            style.addLiveMapUserLayers()
            style.updateLiveMapUsers(liveMapUsers)
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
            val bearing = if (isNorthUp) {
                NORTH_BEARING_DEGREES
            } else {
                lastLocation?.bearingDegrees?.toDouble()
            }
            val cameraMode = if (!isNorthUp && bearing != null) {
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
                    selectedFollowZoom,
                    bearing,
                    selectedFollowTilt,
                    null,
                )
                return@runCatching
            }

            if (
                abs(readyMap.cameraPosition.zoom - selectedFollowZoom) >
                ZOOM_COMPARISON_TOLERANCE
            ) {
                component.zoomWhileTracking(
                    selectedFollowZoom,
                    RECENTER_DURATION_MILLIS,
                )
            }
            if (
                abs(readyMap.cameraPosition.tilt - selectedFollowTilt) >
                TILT_COMPARISON_TOLERANCE
            ) {
                component.tiltWhileTracking(
                    selectedFollowTilt,
                    RECENTER_DURATION_MILLIS,
                )
            }
        }
    }

    private fun applyVisibleArea() {
        val readyMap = map ?: return
        val area = visibleArea
        if (area.isEmpty || surfaceWidth <= 0 || surfaceHeight <= 0) return
        val sidebarWidth = dispatcherSidebarWidth()
        val mapContentWidth = (surfaceWidth - sidebarWidth).coerceAtLeast(1)
        val localVisibleLeft =
            (area.left - sidebarWidth).coerceIn(0, mapContentWidth)
        val localVisibleRight =
            (area.right - sidebarWidth).coerceIn(0, mapContentWidth)
        val padding = intArrayOf(
            localVisibleLeft,
            area.top.coerceAtLeast(0),
            (mapContentWidth - localVisibleRight).coerceAtLeast(0),
            (surfaceHeight - area.bottom).coerceAtLeast(0),
        )
        if (appliedMapPadding?.contentEquals(padding) == true) return

        readyMap.setPadding(padding[0], padding[1], padding[2], padding[3])
        appliedMapPadding = padding
        configureMapCompass()
    }

    private fun configureMapCompass() {
        val readyMap = map ?: return
        readyMap.uiSettings.setCompassMargins(
            dp(COMPASS_MARGIN_DP),
            dp(COMPASS_MARGIN_DP),
            0,
            0,
        )
        mapCompassView?.bringToFront()
    }

    private fun isMapCompassClick(x: Float, y: Float): Boolean {
        val compass = mapCompassView ?: return false
        val root = rootView ?: return false
        if (!compass.isShown || compass.width <= 0 || compass.height <= 0) {
            return false
        }
        val bounds = Rect().also(compass::getDrawingRect)
        root.offsetDescendantRectToMyCoords(compass, bounds)
        return bounds.contains(x.toInt(), y.toInt())
    }

    private fun createMapLayout(
        context: Context,
        newMapView: MapView,
    ): FrameLayout {
        val root = FrameLayout(context)
        root.clipChildren = true
        root.addView(
            newMapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply {
                if (showDispatcherDriverList) {
                    leftMargin = dispatcherSidebarWidth()
                }
            },
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
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        jobQueueView = queueView
        jobCard.addView(queueView)
        val titleView = TextView(context).apply {
            text = carContext.getString(
                org.gtlv.car_common.R.string.driver_current_job,
            )
            setTextColor(Color.WHITE)
            textSize = 26f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        jobTitleView = titleView
        jobCard.addView(titleView)
        val summaryView = TextView(context).apply {
            text = jobSummary
            setTextColor(Color.rgb(210, 213, 218))
            textSize = 22f
            maxLines = Int.MAX_VALUE
            ellipsize = null
            includeFontPadding = false
        }
        jobSummaryView = summaryView
        jobCard.addView(summaryView)

        root.addView(
            jobCard,
            FrameLayout.LayoutParams(
                responsiveJobCardWidth(),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ).apply {
                leftMargin = jobCardStartMargin()
                bottomMargin = dp(OVERLAY_MARGIN_DP)
            },
        )
        applyResponsiveJobCardLayout()

        val jobToggle = createJobCardToggle(context)
        jobCardToggleView = jobToggle
        root.addView(
            jobToggle,
            FrameLayout.LayoutParams(
                dp(JOB_CARD_TOGGLE_WIDTH_DP),
                dp(JOB_CARD_TOGGLE_HEIGHT_DP),
                Gravity.BOTTOM or Gravity.START,
            ),
        )

        if (showDispatcherDriverList) {
            val sidebar = createDispatcherSidebar(context).apply {
                visibility = if (isDispatcherSidebarExpanded) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            dispatcherSidebarView = sidebar

            root.addView(
                sidebar,
                FrameLayout.LayoutParams(
                    expandedDispatcherSidebarWidth(),
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.TOP or Gravity.START,
                ),
            )

            val toggle = createDispatcherSidebarToggle(context)
            dispatcherSidebarToggleView = toggle
            root.addView(
                toggle,
                FrameLayout.LayoutParams(
                    dp(SIDEBAR_TOGGLE_WIDTH_DP),
                    dp(SIDEBAR_TOGGLE_HEIGHT_DP),
                    Gravity.TOP or Gravity.START,
                ).apply {
                    leftMargin = dispatcherSidebarWidth()
                    topMargin = dispatcherSidebarToggleTop()
                },
            )
        }

        root.post {
            applyOverlayInsets()
            jobCard.bringToFront()
            jobToggle.bringToFront()
            positionJobCardToggle()
            dispatcherSidebarView?.bringToFront()
            dispatcherSidebarToggleView?.bringToFront()
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
                leftMargin = jobCardStartMargin()
                bottomMargin =
                    (surfaceHeight - area.bottom).coerceAtLeast(0) +
                        dp(OVERLAY_MARGIN_DP)
                (jobSummaryView?.parent as? LinearLayout)?.layoutParams = this
            }
        jobCardView?.post(::positionJobCardToggle)
        applyResponsiveJobCardLayout()
    }

    private fun createJobCardToggle(context: Context): ImageView =
        ImageView(context).apply {
            setImageResource(org.gtlv.car_common.R.drawable.ic_chevron_down)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(16), dp(6), dp(16), dp(6))
            contentDescription = carContext.getString(
                org.gtlv.car_common.R.string.driver_hide_job_card,
            )
            background = topRoundedBackground(
                color = Color.argb(230, 32, 33, 36),
                radiusDp = JOB_CARD_TOGGLE_RADIUS_DP,
            )
            elevation = dp(12).toFloat()
        }

    private fun positionJobCardToggle() {
        val card = jobCardView ?: return
        val toggle = jobCardToggleView ?: return
        val cardParams = card.layoutParams as? FrameLayout.LayoutParams ?: return
        val toggleParams =
            toggle.layoutParams as? FrameLayout.LayoutParams ?: return
        val cardWidth = cardParams.width.takeIf { width -> width > 0 }
            ?: card.width
        if (cardWidth <= 0 || card.height <= 0) return

        toggleParams.leftMargin = cardParams.leftMargin +
            ((cardWidth - toggleParams.width) / 2).coerceAtLeast(0)
        toggleParams.bottomMargin = cardParams.bottomMargin +
            card.height
        toggle.layoutParams = toggleParams

        if (jobCardAnimator == null) {
            if (isJobCardExpanded) {
                card.translationY = 0f
                toggle.translationY = 0f
            } else {
                val collapseDistance = jobCardCollapseDistance(
                    card = card,
                    cardParams = cardParams,
                )
                card.translationY = collapseDistance
                toggle.translationY = collapseDistance
            }
        }
    }

    private fun jobCardCollapseDistance(
        card: LinearLayout,
        cardParams: FrameLayout.LayoutParams,
    ): Float {
        val queueHeight = jobQueueView?.height ?: 0
        val visibleHeight = (
            card.paddingTop + queueHeight + card.paddingBottom
            ).coerceAtMost(card.height)
        return (
            card.height - visibleHeight + cardParams.bottomMargin
            ).coerceAtLeast(0).toFloat()
    }

    private fun isJobCardToggleClick(x: Float, y: Float): Boolean {
        val toggle = jobCardToggleView ?: return false
        val bounds = Rect()
        return toggle.getGlobalVisibleRect(bounds) &&
            bounds.contains(x.toInt(), y.toInt())
    }

    private fun setJobCardExpanded(expanded: Boolean) {
        if (isJobCardExpanded == expanded || jobCardAnimator != null) return

        val card = jobCardView ?: return
        val toggle = jobCardToggleView ?: return
        positionJobCardToggle()
        val cardParams = card.layoutParams as? FrameLayout.LayoutParams ?: return
        if (card.height <= 0) return

        val collapseDistance = jobCardCollapseDistance(
            card = card,
            cardParams = cardParams,
        )
        val startProgress = if (expanded) 1f else 0f
        val endProgress = if (expanded) 0f else 1f

        isJobCardExpanded = expanded
        card.visibility = View.VISIBLE
        toggle.setImageResource(if (expanded) {
            org.gtlv.car_common.R.drawable.ic_chevron_down
        } else {
            org.gtlv.car_common.R.drawable.ic_chevron_up
        })
        toggle.contentDescription = carContext.getString(
            if (expanded) {
                org.gtlv.car_common.R.string.driver_hide_job_card
            } else {
                org.gtlv.car_common.R.string.driver_show_job_card
            },
        )

        val animator = ValueAnimator.ofFloat(startProgress, endProgress).apply {
            duration = JOB_CARD_ANIMATION_DURATION_MILLIS
            interpolator = DecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                val progress = valueAnimator.animatedValue as Float
                card.translationY = collapseDistance * progress
                toggle.translationY = collapseDistance * progress
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (jobCardAnimator !== animation) return

                        card.visibility = View.VISIBLE
                        jobCardAnimator = null
                    }
                },
            )
        }
        jobCardAnimator = animator
        animator.start()
    }

    private fun createDispatcherSidebar(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(32, 33, 36))

            addView(
                TextView(context).apply {
                    text = carContext.getString(
                        org.gtlv.car_common.R.string.dispatcher_active_users,
                        sidebarUsers.size,
                    )
                    setTextColor(Color.WHITE)
                    textSize = 22f
                    setPadding(dp(18), dp(16), dp(18), dp(16))
                }.also { dispatcherUserCountView = it },
            )

            addView(createSidebarDivider(context))

            val rows = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            dispatcherUserRowsView = rows

            val scrollView = ScrollView(context).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(
                    rows,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            dispatcherUserScrollView = scrollView

            addView(
                scrollView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )

            renderDispatcherUsers()
        }
    }

    private fun createDispatcherSidebarToggle(context: Context): TextView =
        TextView(context).apply {
            text = if (isDispatcherSidebarExpanded) {
                SIDEBAR_COLLAPSE_CHEVRON
            } else {
                SIDEBAR_EXPAND_CHEVRON
            }
            contentDescription = carContext.getString(
                if (isDispatcherSidebarExpanded) {
                    org.gtlv.car_common.R.string.dispatcher_hide_sidebar
                } else {
                    org.gtlv.car_common.R.string.dispatcher_show_sidebar
                },
            )
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 26f
            background = rightRoundedBackground(
                color = Color.rgb(32, 33, 36),
                radiusDp = SIDEBAR_TOGGLE_RADIUS_DP,
            )
            elevation = dp(14).toFloat()
        }

    private fun isDispatcherSidebarToggleClick(x: Float, y: Float): Boolean {
        if (!showDispatcherDriverList) return false

        val toggleWidth = dp(SIDEBAR_TOGGLE_WIDTH_DP)
        val toggleLeft = if (isDispatcherSidebarExpanded) {
            dispatcherSidebarWidth()
        } else {
            0
        }
        val toggleTop = dispatcherSidebarToggleTop()
        return x >= toggleLeft &&
            x < toggleLeft + toggleWidth &&
            y >= toggleTop &&
            y < toggleTop + dp(SIDEBAR_TOGGLE_HEIGHT_DP)
    }

    private fun dispatcherSidebarToggleTop(): Int =
        ((surfaceHeight - dp(SIDEBAR_TOGGLE_HEIGHT_DP)) / 2)
            .coerceAtLeast(0)

    private fun setDispatcherSidebarExpanded(expanded: Boolean) {
        if (
            !showDispatcherDriverList ||
            isDispatcherSidebarExpanded == expanded ||
            dispatcherSidebarAnimator != null
        ) {
            return
        }

        val sidebar = dispatcherSidebarView ?: return
        val expandedWidth = expandedDispatcherSidebarWidth()
        val startWidth = if (expanded) 0 else expandedWidth
        val endWidth = if (expanded) expandedWidth else 0

        isDispatcherSidebarExpanded = expanded
        if (!expanded) {
            interactionTarget = InteractionTarget.MAP
        }

        sidebar.visibility = View.VISIBLE
        sidebar.translationX = (startWidth - expandedWidth).toFloat()
        dispatcherSidebarToggleView?.apply {
            text = if (expanded) {
                SIDEBAR_COLLAPSE_CHEVRON
            } else {
                SIDEBAR_EXPAND_CHEVRON
            }
            contentDescription = carContext.getString(
                if (expanded) {
                    org.gtlv.car_common.R.string.dispatcher_hide_sidebar
                } else {
                    org.gtlv.car_common.R.string.dispatcher_show_sidebar
                },
            )
            bringToFront()
        }

        val animator = ValueAnimator.ofInt(startWidth, endWidth).apply {
            duration = SIDEBAR_ANIMATION_DURATION_MILLIS
            interpolator = DecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                val visibleSidebarWidth = valueAnimator.animatedValue as Int
                updateDispatcherSidebarAnimationFrame(
                    visibleSidebarWidth = visibleSidebarWidth,
                    expandedSidebarWidth = expandedWidth,
                )
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (dispatcherSidebarAnimator !== animation) return

                        sidebar.visibility =
                            if (expanded) View.VISIBLE else View.GONE
                        sidebar.translationX = 0f
                        dispatcherSidebarAnimator = null
                        appliedMapPadding = null
                        rootView?.post {
                            applyVisibleArea()
                            applyOverlayInsets()
                        }
                    }
                },
            )
        }
        dispatcherSidebarAnimator = animator
        animator.start()
    }

    private fun updateDispatcherSidebarAnimationFrame(
        visibleSidebarWidth: Int,
        expandedSidebarWidth: Int,
    ) {
        dispatcherSidebarView?.translationX =
            (visibleSidebarWidth - expandedSidebarWidth).toFloat()

        (mapView?.layoutParams as? FrameLayout.LayoutParams)?.apply {
            leftMargin = visibleSidebarWidth
            mapView?.layoutParams = this
        }
        (dispatcherSidebarToggleView?.layoutParams as? FrameLayout.LayoutParams)
            ?.apply {
                leftMargin = visibleSidebarWidth
                dispatcherSidebarToggleView?.layoutParams = this
            }
        applyResponsiveJobCardLayout(visibleSidebarWidth)
        positionJobCardToggle()

        rootView?.requestLayout()
    }

    private fun renderDispatcherUsers() {
        dispatcherUserCountView?.text = carContext.getString(
            org.gtlv.car_common.R.string.dispatcher_active_users,
            sidebarUsers.size,
        )

        val rows = dispatcherUserRowsView ?: return
        val previousScrollY = dispatcherUserScrollView?.scrollY ?: 0
        rows.removeAllViews()

        if (sidebarUsers.isEmpty()) {
            rows.addView(
                TextView(rows.context).apply {
                    text = carContext.getString(
                        org.gtlv.car_common.R.string.dispatcher_no_active_users,
                    )
                    setTextColor(Color.rgb(210, 213, 218))
                    textSize = 17f
                    setPadding(dp(18), dp(16), dp(18), dp(16))
                },
            )
        } else {
            sidebarUsers.forEachIndexed { index, user ->
                rows.addView(createDispatcherUserRow(rows.context, user))
                if (index < sidebarUsers.lastIndex) {
                    rows.addView(createSidebarDivider(rows.context))
                }
            }
        }

        dispatcherUserScrollView?.post {
            dispatcherUserScrollView?.scrollTo(0, previousScrollY)
            if (!isDispatcherSidebarScrollable()) {
                interactionTarget = InteractionTarget.MAP
            }
        }
    }

    private fun createDispatcherUserRow(
        context: Context,
        user: SidebarUser,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))

            addView(
                TextView(context).apply {
                    text = user.name
                    setTextColor(Color.WHITE)
                    textSize = 20f
                },
            )

            addView(
                TextView(context).apply {
                    text = "\u25CF ${carContext.getString(user.state.statusResource())}"
                    setTextColor(user.state.liveMapMarkerColor)
                    textSize = 17f
                },
            )
        }
    }

    private fun TelemetryVehicleState.statusResource(): Int = when (this) {
        TelemetryVehicleState.FREE ->
            org.gtlv.car_common.R.string.driver_status_free
        TelemetryVehicleState.ON_THE_WAY ->
            org.gtlv.car_common.R.string.driver_status_on_the_way
        TelemetryVehicleState.OCCUPIED ->
            org.gtlv.car_common.R.string.driver_status_occupied
        TelemetryVehicleState.AWAY ->
            org.gtlv.car_common.R.string.driver_status_away
    }

    private fun TelemetryVehicleState.sidebarSortOrder(): Int = when (this) {
        TelemetryVehicleState.FREE -> 0
        TelemetryVehicleState.ON_THE_WAY -> 1
        TelemetryVehicleState.OCCUPIED -> 2
        TelemetryVehicleState.AWAY -> 3
    }

    private fun createSidebarDivider(context: Context): View {
        return View(context).apply {
            setBackgroundColor(Color.argb(80, 210, 213, 218))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            )
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

    private fun rightRoundedBackground(
        color: Int,
        radiusDp: Int,
    ): GradientDrawable = GradientDrawable().apply {
        val radius = dp(radiusDp).toFloat()
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadii = floatArrayOf(
            0f,
            0f,
            radius,
            radius,
            radius,
            radius,
            0f,
            0f,
        )
    }

    private fun topRoundedBackground(
        color: Int,
        radiusDp: Int,
    ): GradientDrawable = GradientDrawable().apply {
        val radius = dp(radiusDp).toFloat()
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadii = floatArrayOf(
            radius,
            radius,
            radius,
            radius,
            0f,
            0f,
            0f,
            0f,
        )
    }

    private fun dp(value: Int): Int = (value * renderDensity).roundToInt()

    private fun responsiveRenderDpi(
        width: Int,
        height: Int,
        hostDpi: Int,
    ): Int {
        val resolutionScale = min(
            width.toFloat() / REFERENCE_SURFACE_WIDTH,
            height.toFloat() / REFERENCE_SURFACE_HEIGHT,
        ).coerceIn(MIN_RESOLUTION_SCALE, 1f)
        val responsiveMaximum =
            (MAX_RENDER_DPI * resolutionScale).roundToInt()
                .coerceAtLeast(MIN_RENDER_DPI)
        return min(hostDpi, responsiveMaximum)
    }

    private fun dispatcherSidebarWidth(): Int =
        if (showDispatcherDriverList && isDispatcherSidebarExpanded) {
            expandedDispatcherSidebarWidth()
        } else {
            0
        }

    private fun expandedDispatcherSidebarWidth(): Int =
        if (showDispatcherDriverList) {
            min(
                dp(DISPATCHER_SIDEBAR_WIDTH_DP),
                (surfaceWidth * SIDEBAR_MAX_WIDTH_FRACTION).roundToInt(),
            ).coerceAtLeast(1)
        } else {
            0
        }

    private fun responsiveJobCardWidth(
        sidebarWidth: Int = dispatcherSidebarWidth(),
    ): Int {
        val mapContentWidth = (surfaceWidth - sidebarWidth).coerceAtLeast(1)
        val safeAreaRight = if (stableArea.isEmpty) {
            surfaceWidth
        } else {
            min(surfaceWidth, stableArea.right)
        }
        val availableWidth =
            (
                safeAreaRight - jobCardStartMargin(sidebarWidth) -
                    dp(OVERLAY_MARGIN_DP)
                ).coerceAtLeast(1)
        val density = renderDensity.coerceAtLeast(MIN_LAYOUT_DENSITY)
        val availableWidthDp = availableWidth / density
        val maximumWidthFraction = when {
            availableWidthDp < VERY_COMPACT_JOB_CARD_WIDTH_DP ->
                VERY_COMPACT_JOB_CARD_MAX_WIDTH_FRACTION
            availableWidthDp < COMPACT_JOB_CARD_WIDTH_DP ->
                COMPACT_JOB_CARD_MAX_WIDTH_FRACTION
            else -> JOB_CARD_MAX_WIDTH_FRACTION
        }
        return minOf(
            dp(JOB_CARD_WIDTH_DP),
            (mapContentWidth * maximumWidthFraction).roundToInt(),
            availableWidth,
        ).coerceAtLeast(1)
    }

    private fun jobCardStartMargin(
        sidebarWidth: Int = dispatcherSidebarWidth(),
    ): Int {
        val safeAreaMargin = if (stableArea.isEmpty) {
            sidebarWidth + dp(OVERLAY_MARGIN_DP)
        } else {
            maxOf(stableArea.left, sidebarWidth) + dp(OVERLAY_MARGIN_DP)
        }
        val driverToggleClearance = if (showDispatcherDriverList) {
            sidebarWidth +
                dp(SIDEBAR_TOGGLE_WIDTH_DP + JOB_CARD_DRIVER_TOGGLE_GAP_DP)
        } else {
            sidebarWidth + dp(OVERLAY_MARGIN_DP)
        }
        return maxOf(safeAreaMargin, driverToggleClearance)
    }

    private fun applyResponsiveJobCardLayout(
        sidebarWidth: Int = dispatcherSidebarWidth(),
    ) {
        val card = jobCardView ?: return
        val mapContentWidth = (surfaceWidth - sidebarWidth).coerceAtLeast(1)
        val density = renderDensity.coerceAtLeast(MIN_LAYOUT_DENSITY)
        val contentWidthDp = mapContentWidth / density
        val contentHeightDp = surfaceHeight / density
        val veryCompact =
            contentWidthDp < VERY_COMPACT_JOB_CARD_WIDTH_DP ||
                contentHeightDp < VERY_COMPACT_JOB_CARD_HEIGHT_DP
        val compact = veryCompact ||
            contentWidthDp < COMPACT_JOB_CARD_WIDTH_DP ||
            contentHeightDp < COMPACT_JOB_CARD_HEIGHT_DP

        val horizontalPaddingDp = when {
            veryCompact -> 10
            compact -> 14
            else -> 20
        }
        val verticalPaddingDp = when {
            veryCompact -> 8
            compact -> 10
            else -> 14
        }
        card.setPadding(
            dp(horizontalPaddingDp),
            dp(verticalPaddingDp),
            dp(horizontalPaddingDp),
            dp(verticalPaddingDp),
        )

        jobQueueView?.textSize = when {
            veryCompact -> 14f
            compact -> 16f
            else -> 20f
        }
        jobTitleView?.textSize = when {
            veryCompact -> 18f
            compact -> 21f
            else -> 26f
        }
        jobSummaryView?.apply {
            textSize = when {
                veryCompact -> 16f
                compact -> 18f
                else -> 22f
            }
        }

        (card.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            val responsiveWidth = responsiveJobCardWidth(sidebarWidth)
            val responsiveLeftMargin = jobCardStartMargin(sidebarWidth)
            if (
                params.width != responsiveWidth ||
                params.leftMargin != responsiveLeftMargin
            ) {
                params.width = responsiveWidth
                params.leftMargin = responsiveLeftMargin
                card.layoutParams = params
            }
        }
        card.requestLayout()
        card.post(::positionJobCardToggle)
    }

    private fun queuedJobText(): String =
        carContext.resources.getQuantityString(
            org.gtlv.car_common.R.plurals.driver_jobs_in_queue,
            queuedJobCount,
            queuedJobCount,
        )

    private fun destroyDisplay(releaseSurface: Boolean = true) {
        dispatcherSidebarAnimator?.removeAllListeners()
        dispatcherSidebarAnimator?.cancel()
        dispatcherSidebarAnimator = null
        jobCardAnimator?.removeAllListeners()
        jobCardAnimator?.cancel()
        jobCardAnimator = null
        val oldMapView = mapView
        map = null
        mapView = null
        mapCompassView = null
        rootView = null
        jobCardView = null
        jobCardToggleView = null
        jobQueueView = null
        jobTitleView = null
        jobSummaryView = null
        dispatcherSidebarView = null
        dispatcherSidebarToggleView = null
        dispatcherUserScrollView = null
        dispatcherUserRowsView = null
        dispatcherUserCountView = null
        appliedMapPadding = null
        interactionTarget = InteractionTarget.MAP
        isDispatcherSidebarExpanded = false
        isJobCardExpanded = true
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
        renderDensity = 1f
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

    private fun View.findCompassView(): CompassView? {
        if (this is CompassView) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findCompassView()?.let { return it }
        }
        return null
    }

    private companion object {
        data class SidebarUser(
            val userId: String,
            val name: String,
            val state: TelemetryVehicleState,
        )

        const val DISPLAY_NAME = "Atlas Android Auto map"
        const val LOG_TAG = "AtlasCarMap"
        const val DISPATCHER_SIDEBAR_WIDTH_DP = 220
        const val SIDEBAR_TOGGLE_WIDTH_DP = 42
        const val SIDEBAR_TOGGLE_HEIGHT_DP = 88
        const val SIDEBAR_TOGGLE_RADIUS_DP = 18
        const val SIDEBAR_ANIMATION_DURATION_MILLIS = 280L
        const val SIDEBAR_COLLAPSE_CHEVRON = "\u2039"
        const val SIDEBAR_EXPAND_CHEVRON = "\u203A"
        const val JOB_CARD_WIDTH_DP = 240
        const val JOB_CARD_DRIVER_TOGGLE_GAP_DP = 12
        const val COMPACT_JOB_CARD_WIDTH_DP = 700
        const val COMPACT_JOB_CARD_HEIGHT_DP = 500
        const val VERY_COMPACT_JOB_CARD_WIDTH_DP = 480
        const val VERY_COMPACT_JOB_CARD_HEIGHT_DP = 360
        const val JOB_CARD_TOGGLE_WIDTH_DP = 64
        const val JOB_CARD_TOGGLE_HEIGHT_DP = 32
        const val JOB_CARD_TOGGLE_RADIUS_DP = 14
        const val JOB_CARD_ANIMATION_DURATION_MILLIS = 240L
        const val OVERLAY_MARGIN_DP = 8
        const val BASE_DENSITY_DPI = 160
        const val MAX_RENDER_DPI = 200
        const val MIN_RENDER_DPI = 140
        const val REFERENCE_SURFACE_WIDTH = 1920f
        const val REFERENCE_SURFACE_HEIGHT = 1080f
        const val MIN_RESOLUTION_SCALE = 0.75f
        const val MIN_LAYOUT_DENSITY = 0.1f
        const val SIDEBAR_MAX_WIDTH_FRACTION = 0.24f
        const val JOB_CARD_MAX_WIDTH_FRACTION = 0.34f
        const val COMPACT_JOB_CARD_MAX_WIDTH_FRACTION = 0.36f
        const val VERY_COMPACT_JOB_CARD_MAX_WIDTH_FRACTION = 0.40f
        const val INITIAL_LATITUDE = 48.500
        const val INITIAL_LONGITUDE = 14.580
        const val INITIAL_ZOOM = 12.5
        const val FOLLOW_ZOOM = 15.5
        const val NORTH_BEARING_DEGREES = 0.0
        const val COMPASS_MARGIN_DP = 16
        const val COMPASS_RESET_DURATION_MILLIS = 300
        const val MIN_USER_ZOOM = 3.0
        const val MAX_USER_ZOOM = 20.0
        const val ZOOM_STEP = 1.0
        const val ZOOM_COMPARISON_TOLERANCE = 0.01
        const val ZOOM_DURATION_MILLIS = 300
        const val FOLLOW_TILT = 45.0
        const val TILT_COMPARISON_TOLERANCE = 0.5
        const val TILT_DURATION_MILLIS = 300L
        val TILT_LEVELS = doubleArrayOf(0.0, 30.0, 45.0, 60.0)
        const val LOCATION_MARKER_SCALE = 1.5f
        const val RECENTER_DURATION_MILLIS = 500L
        const val FLING_SECONDS = 0.18f
        const val FLING_DURATION_MILLIS = 400
    }

    private enum class InteractionTarget {
        MAP,
        SIDEBAR,
    }
}

private fun Style.addAutomotiveRouteLayers() {
    if (getSource(AUTOMOTIVE_ROUTE_SOURCE_ID) == null) {
        addSource(
            GeoJsonSource(
                AUTOMOTIVE_ROUTE_SOURCE_ID,
                emptyAutomotiveRouteFeatures(),
            ),
        )
    }
    if (getSource(AUTOMOTIVE_DESTINATION_SOURCE_ID) == null) {
        addSource(
            GeoJsonSource(
                AUTOMOTIVE_DESTINATION_SOURCE_ID,
                emptyAutomotiveRouteFeatures(),
            ),
        )
    }

    if (getLayer(AUTOMOTIVE_ROUTE_CASING_LAYER_ID) == null) {
        val casing = LineLayer(
            AUTOMOTIVE_ROUTE_CASING_LAYER_ID,
            AUTOMOTIVE_ROUTE_SOURCE_ID,
        ).withProperties(
            PropertyFactory.lineColor(Color.rgb(17, 24, 39)),
            PropertyFactory.lineWidth(10f),
            PropertyFactory.lineOpacity(0.72f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        )
        val firstLabelLayerId = layers
            .firstOrNull { it is SymbolLayer }
            ?.id
        if (firstLabelLayerId != null) {
            addLayerBelow(casing, firstLabelLayerId)
        } else {
            addLayer(casing)
        }
    }

    if (getLayer(AUTOMOTIVE_ROUTE_LAYER_ID) == null) {
        addLayerAbove(
            LineLayer(
                AUTOMOTIVE_ROUTE_LAYER_ID,
                AUTOMOTIVE_ROUTE_SOURCE_ID,
            ).withProperties(
                PropertyFactory.lineColor(Color.rgb(37, 99, 235)),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineOpacity(0.96f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            ),
            AUTOMOTIVE_ROUTE_CASING_LAYER_ID,
        )
    }

    if (getLayer(AUTOMOTIVE_DESTINATION_LAYER_ID) == null) {
        addLayer(
            CircleLayer(
                AUTOMOTIVE_DESTINATION_LAYER_ID,
                AUTOMOTIVE_DESTINATION_SOURCE_ID,
            ).withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor(Color.rgb(37, 99, 235)),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
    }
}

private fun Style.updateAutomotiveRoute(points: List<RoutePoint>) {
    val routeFeatures = if (points.size >= 2) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    points.map {
                        Point.fromLngLat(it.longitude, it.latitude)
                    },
                ),
            ),
        )
    } else {
        emptyAutomotiveRouteFeatures()
    }
    getSourceAs<GeoJsonSource>(AUTOMOTIVE_ROUTE_SOURCE_ID)
        ?.setGeoJson(routeFeatures)

    val destinationFeatures = points.lastOrNull()
        ?.takeIf { points.size >= 2 }
        ?.let {
            FeatureCollection.fromFeature(
                Feature.fromGeometry(
                    Point.fromLngLat(it.longitude, it.latitude),
                ),
            )
        } ?: emptyAutomotiveRouteFeatures()
    getSourceAs<GeoJsonSource>(AUTOMOTIVE_DESTINATION_SOURCE_ID)
        ?.setGeoJson(destinationFeatures)
}

private fun emptyAutomotiveRouteFeatures(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyArray<Feature>())

private const val AUTOMOTIVE_ROUTE_SOURCE_ID =
    "atlas-automotive-route-source"
private const val AUTOMOTIVE_ROUTE_CASING_LAYER_ID =
    "atlas-automotive-route-casing-layer"
private const val AUTOMOTIVE_ROUTE_LAYER_ID =
    "atlas-automotive-route-layer"
private const val AUTOMOTIVE_DESTINATION_SOURCE_ID =
    "atlas-automotive-destination-source"
private const val AUTOMOTIVE_DESTINATION_LAYER_ID =
    "atlas-automotive-destination-layer"
