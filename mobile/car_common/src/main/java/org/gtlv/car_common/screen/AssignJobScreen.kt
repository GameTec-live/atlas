package org.gtlv.car_common.screen

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.ScreenManager
import androidx.car.app.annotations.RequiresCarApi
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.LifecycleOwner
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.gtlv.car_common.R
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.geoservice.RouteResult
import org.gtlv.core.location.LocationProvider
import org.gtlv.core.location.LocationState
import org.gtlv.core.settings.ServerSettingsRepository
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.telemetry.LiveMapUser
import org.gtlv.core.telemetry.TelemetryVehicleState

/** UI-only Android Auto surface for editing and assigning an unassigned job. */
@RequiresCarApi(7)
internal class AssignJobScreen(
    carContext: CarContext,
    initialFrom: String = "",
    initialTo: String = "",
    initialNote: String = "",
    getRole: () -> ShiftRole?,
    onRoleLost: () -> Unit,
    private val locationProvider: LocationProvider?,
    private val serverSettingsRepository: ServerSettingsRepository?,
    private val geoServiceRepository: GeoServiceRepository?,
    private val getUserId: () -> String?,
    private val liveMapUsers: StateFlow<Map<String, LiveMapUser>>?,
    private val mapRenderer: MapLibreSurfaceRenderer,
) : RoleAwareScreen(
    carContext = carContext,
    expectedRole = ShiftRole.DISPATCHER,
    getRole = getRole,
    onRoleLost = onRoleLost,
) {
    private val screenScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )
    private var locationJob: Job? = null
    private var styleJob: Job? = null
    private var liveUsersJob: Job? = null
    private var routeJob: Job? = null
    private var from = initialFrom
    private var to = initialTo
    private var note = initialNote
    private var fromPoint: RoutePoint? = null
    private var toPoint: RoutePoint? = null
    private var previewCameraPoints: List<RoutePoint> = emptyList()
    private var drivers: List<AssignDriver> = emptyList()
    private var selectedDriverId: String? = null

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        mapRenderer.enterAssignJobMode()
        mapRenderer.focusRoutePoints(previewCameraPoints)
        observeMapConfiguration()
        observeLocation()
        observeLiveUsers()
    }

    override fun onStop(owner: LifecycleOwner) {
        locationJob?.cancel()
        locationJob = null
        styleJob?.cancel()
        styleJob = null
        liveUsersJob?.cancel()
        liveUsersJob = null
        routeJob?.cancel()
        routeJob = null
        super.onStop(owner)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        screenScope.cancel()
        super.onDestroy(owner)
    }

    override fun onGetTemplate(): Template {
        val content = ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.assign_job_title))
                    .setStartHeaderAction(Action.BACK)
                    .build(),
            )
            .addSectionedList(jobDetailsSection())
            .addSectionedList(recommendedDriversSection())
            .build()

        return MapWithContentTemplate.Builder()
            .setContentTemplate(content)
            .setMapController(buildMapController())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(assignAction())
                    .build(),
            )
            .build()
    }

    private fun jobDetailsSection(): SectionedItemList {
        val items = ItemList.Builder()
            .addItem(addressRow(
                label = carContext.getString(R.string.assign_job_from),
                value = from,
                placeholder = carContext.getString(
                    R.string.assign_job_from_placeholder,
                ),
                editorTitle = carContext.getString(R.string.assign_job_edit_from),
                onSelected = { suggestion ->
                    from = suggestion.displayName
                    fromPoint = suggestion.toRoutePoint()
                    updateRoutePreview()
                },
            ))
            .addItem(addressRow(
                label = carContext.getString(R.string.assign_job_to),
                value = to,
                placeholder = carContext.getString(
                    R.string.assign_job_to_placeholder,
                ),
                editorTitle = carContext.getString(R.string.assign_job_edit_to),
                onSelected = { suggestion ->
                    to = suggestion.displayName
                    toPoint = suggestion.toRoutePoint()
                    updateRoutePreview()
                },
            ))
            .addItem(editableRow(
                label = carContext.getString(R.string.assign_job_note),
                value = note,
                placeholder = carContext.getString(
                    R.string.assign_job_note_placeholder,
                ),
                editorTitle = carContext.getString(R.string.assign_job_edit_note),
                onChanged = { note = it },
            ))
            .build()

        return SectionedItemList.create(
            items,
            carContext.getString(R.string.assign_job_details),
        )
    }

    private fun addressRow(
        label: String,
        value: String,
        placeholder: String,
        editorTitle: String,
        onSelected: (AddressSuggestion) -> Unit,
    ): Row = Row.Builder()
        .setTitle(label)
        .addText(value.ifBlank { placeholder })
        .setOnClickListener {
            val repository = geoServiceRepository
            if (repository == null) {
                CarToast.makeText(
                    carContext,
                    R.string.assign_job_address_search_unavailable,
                    CarToast.LENGTH_SHORT,
                ).show()
                return@setOnClickListener
            }

            carContext.getCarService(ScreenManager::class.java).push(
                AssignJobAddressSearchScreen(
                    carContext = carContext,
                    title = editorTitle,
                    initialValue = value,
                    geoServiceRepository = repository,
                    onSuggestionSelected = { suggestion ->
                        onSelected(suggestion)
                        invalidate()
                    },
                ),
            )
        }
        .build()

    private fun editableRow(
        label: String,
        value: String,
        placeholder: String,
        editorTitle: String,
        onChanged: (String) -> Unit,
    ): Row = Row.Builder()
        .setTitle(label)
        .addText(value.ifBlank { placeholder })
        .setOnClickListener {
            carContext.getCarService(ScreenManager::class.java).push(
                AssignJobTextInputScreen(
                    carContext = carContext,
                    title = editorTitle,
                    initialValue = value,
                    onValueChanged = { updatedValue ->
                        onChanged(updatedValue)
                        invalidate()
                    },
                ),
            )
        }
        .build()

    private fun recommendedDriversSection(): SectionedItemList {
        val availableDrivers = drivers
            .filter { driver -> driver.state == TelemetryVehicleState.FREE }
            .sortedBy { driver -> driver.userName.lowercase() }
        val items = ItemList.Builder()

        if (availableDrivers.isEmpty()) {
            items.addItem(
                Row.Builder()
                    .setTitle(
                        carContext.getString(
                            R.string.assign_job_no_available_drivers,
                        ),
                    )
                    .build(),
            )
        } else {
            availableDrivers.forEachIndexed { index, driver ->
                val isSelected = driver.userId == selectedDriverId
                items.addItem(
                    Row.Builder()
                        .setTitle(
                            carContext.getString(
                                if (isSelected) {
                                    R.string.assign_job_selected_driver_rank
                                } else {
                                    R.string.assign_job_driver_rank
                                },
                                index + 1,
                                driver.userName,
                            ),
                        )
                        .addText(
                            if (isSelected) {
                                carContext.getString(
                                    R.string.assign_job_selected_driver,
                                    carContext.getString(R.string.driver_status_free),
                                )
                            } else {
                                carContext.getString(R.string.driver_status_free)
                            },
                        )
                        .setOnClickListener {
                            selectedDriverId = driver.userId
                            invalidate()
                        }
                        .build(),
                )
            }
        }

        return SectionedItemList.create(
            items.build(),
            carContext.getString(R.string.assign_job_recommended_drivers),
        )
    }

    private fun assignAction(): Action {
        val canAssign = from.isNotBlank() &&
            to.isNotBlank() &&
            selectedDriverId != null

        return Action.Builder()
            .setTitle(carContext.getString(R.string.assign_job_assign))
            .setFlags(Action.FLAG_PRIMARY or Action.FLAG_IS_PERSISTENT)
            .setBackgroundColor(CarColor.BLUE)
            .setEnabled(canAssign)
            .setOnClickListener {
                CarToast.makeText(
                    carContext,
                    R.string.assign_job_ui_only_message,
                    CarToast.LENGTH_SHORT,
                ).show()
            }
            .build()
    }

    private fun buildMapController(): MapController = MapController.Builder()
        .setMapActionStrip(
            ActionStrip.Builder()
                .addAction(
                    Action.Builder(Action.PAN)
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_pan,
                                ),
                            ).build(),
                        )
                        .build(),
                )
                .addAction(
                    mapAction(
                        iconResource = R.drawable.ic_zoom_in,
                        onClick = mapRenderer::zoomIn,
                    ),
                )
                .addAction(
                    mapAction(
                        iconResource = R.drawable.ic_zoom_out,
                        onClick = mapRenderer::zoomOut,
                    ),
                )
                .build(),
        )
        .setPanModeListener { isInPanMode ->
            if (isInPanMode) mapRenderer.stopFollowingLocation()
        }
        .build()

    private fun mapAction(
        iconResource: Int,
        onClick: () -> Unit,
    ): Action = Action.Builder()
        .setIcon(
            CarIcon.Builder(
                IconCompat.createWithResource(
                    carContext,
                    iconResource,
                ),
            ).build(),
        )
        .setFlags(Action.FLAG_IS_PERSISTENT)
        .setOnClickListener(onClick)
        .build()

    private fun observeMapConfiguration() {
        if (styleJob != null) return
        val repository = serverSettingsRepository ?: return

        styleJob = screenScope.launch {
            repository.serverAddress.collectLatest { serverAddress ->
                if (serverAddress.isNotBlank()) {
                    mapRenderer.setStyleUrl(
                        "${serverAddress.trimEnd('/')}/map/style/liberty",
                    )
                }
            }
        }
    }

    private fun observeLocation() {
        if (locationJob != null) return
        val provider = locationProvider ?: return

        locationJob = screenScope.launch {
            provider.state.collectLatest { state ->
                val location = (state as? LocationState.Available)?.location
                    ?: return@collectLatest
                mapRenderer.updateLocation(location)
            }
        }
    }

    private fun observeLiveUsers() {
        if (liveUsersJob != null) return
        val users = liveMapUsers ?: return

        liveUsersJob = screenScope.launch {
            users.collectLatest { usersById ->
                val currentUserId = getUserId()
                val visibleDrivers = usersById.values
                    .filterNot { driver -> driver.userId == currentUserId }
                mapRenderer.updateLiveUsers(visibleDrivers)

                val updatedDrivers = visibleDrivers
                    .map { driver ->
                        AssignDriver(
                            userId = driver.userId,
                            userName = driver.userName,
                            state = driver.state,
                        )
                    }
                    .sortedBy(AssignDriver::userId)
                val previousSelectedDriverId = selectedDriverId
                if (updatedDrivers.none { driver ->
                        driver.userId == selectedDriverId &&
                            driver.state == TelemetryVehicleState.FREE
                    }
                ) {
                    selectedDriverId = null
                }

                if (
                    drivers != updatedDrivers ||
                    previousSelectedDriverId != selectedDriverId
                ) {
                    drivers = updatedDrivers
                    invalidate()
                }
            }
        }
    }

    private fun updateRoutePreview() {
        routeJob?.cancel()
        routeJob = null
        mapRenderer.updateRoute(emptyList())

        val origin = fromPoint
        val destination = toPoint
        val selectedPoints = listOfNotNull(origin, destination)
        previewCameraPoints = selectedPoints
        when {
            origin != null && destination == null -> {
                mapRenderer.updateRoute(listOf(origin))
            }
            origin == null && destination != null -> {
                mapRenderer.updateRoute(listOf(destination))
            }
        }
        mapRenderer.focusRoutePoints(selectedPoints)

        val repository = geoServiceRepository ?: return
        if (origin == null || destination == null) return

        routeJob = screenScope.launch {
            val result = repository.requestRoute(origin, destination)
            currentCoroutineContext().ensureActive()
            if (fromPoint != origin || toPoint != destination) return@launch

            val previewPoints = if (result is RouteResult.Success) {
                result.route.points
            } else {
                selectedPoints
            }
            previewCameraPoints = previewPoints
            mapRenderer.updateRoute(
                if (result is RouteResult.Success) previewPoints else emptyList(),
            )
            mapRenderer.focusRoutePoints(previewPoints)
        }
    }

    private fun AddressSuggestion.toRoutePoint(): RoutePoint = RoutePoint(
        latitude = latitude,
        longitude = longitude,
    )
}

private data class AssignDriver(
    val userId: String,
    val userName: String,
    val state: TelemetryVehicleState,
)
