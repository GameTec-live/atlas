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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobCandidatesResult
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobCreationResult
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.NewJobRequest
import org.gtlv.core.job.UnassignedJobsResult
import org.gtlv.core.location.LocationProvider
import org.gtlv.core.location.LocationState
import org.gtlv.core.settings.ServerSettingsRepository
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.telemetry.LiveMapUser

/** Android Auto surface for creating or assigning a job. */
@RequiresCarApi(7)
internal class AssignJobScreen(
    carContext: CarContext,
    private val initialJobId: String? = null,
    initialFrom: String = "",
    initialTo: String = "",
    initialNote: String = "",
    getRole: () -> ShiftRole?,
    onRoleLost: () -> Unit,
    private val locationProvider: LocationProvider?,
    private val serverSettingsRepository: ServerSettingsRepository?,
    private val geoServiceRepository: GeoServiceRepository?,
    private val jobRepository: JobRepository?,
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
    private var candidatesJob: Job? = null
    private var existingJobLoadJob: Job? = null
    private var hasRequestedExistingJob = false
    private var isLoadingExistingJob = initialJobId != null
    private var routeWasEdited = false
    private var from = initialFrom
    private var to = initialTo
    private var note = initialNote
    private var fromPoint: RoutePoint? = null
    private var toPoint: RoutePoint? = null
    private var previewCameraPoints: List<RoutePoint> = emptyList()
    private var candidates: List<JobCandidate> = emptyList()
    private var isLoadingCandidates = false
    private var candidatesFailed = false
    private var selectedDriverId: String? = null
    private var createUnassignedSelected = false
    private var isSubmitting = false
    private var candidateDueDate = Instant.now().toString()
    private var existingPickupTime: String? = null

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        mapRenderer.enterAssignJobMode()
        mapRenderer.focusRoutePoints(previewCameraPoints)
        loadExistingJobIfNeeded()
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
        val fromRow = if (initialJobId == null) {
            addressRow(
                label = carContext.getString(R.string.assign_job_from),
                value = from,
                placeholder = carContext.getString(
                    R.string.assign_job_from_placeholder,
                ),
                editorTitle = carContext.getString(R.string.assign_job_edit_from),
                onSelected = { suggestion ->
                    from = suggestion.displayName
                    fromPoint = suggestion.toRoutePoint()
                    routeWasEdited = true
                    updateRoutePreview()
                    loadRecommendedDrivers()
                },
            )
        } else {
            readOnlyRow(
                label = carContext.getString(R.string.assign_job_from),
                value = from,
                placeholder = carContext.getString(
                    R.string.assign_job_from_placeholder,
                ),
            )
        }

        val itemsBuilder = ItemList.Builder()
            .addItem(fromRow)
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
                    routeWasEdited = true
                    updateRoutePreview()
                    loadRecommendedDrivers()
                },
            ))

        existingPickupTime?.let { pickupTime ->
            itemsBuilder.addItem(pickupTimeRow(pickupTime))
        }

        val items = itemsBuilder.addItem(editableRow(
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

    private fun readOnlyRow(
        label: String,
        value: String,
        placeholder: String,
    ): Row = Row.Builder()
        .setTitle(label)
        .addText(value.ifBlank { placeholder })
        .build()

    private fun pickupTimeRow(value: String): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.assign_job_pickup_time))
        .addText(formatPickupTime(value))
        .build()

    private fun formatPickupTime(value: String): String = runCatching {
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(carContext.resources.configuration.locales[0])
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrDefault(value)

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
        val items = ItemList.Builder()

        when {
            isLoadingExistingJob || isLoadingCandidates -> items.addItem(
                Row.Builder()
                    .setTitle(
                        carContext.getString(
                            R.string.assign_job_loading_candidates,
                        ),
                    )
                    .build(),
            )

            candidatesFailed -> items.addItem(
                Row.Builder()
                    .setTitle(
                        carContext.getString(
                            R.string.assign_job_candidates_error,
                        ),
                    )
                    .addText(
                        carContext.getString(
                            R.string.assign_job_retry_candidates,
                        ),
                    )
                    .setOnClickListener(::retryRecommendedDrivers)
                    .build(),
            )

            fromPoint == null -> items.addItem(
                Row.Builder()
                    .setTitle(
                        carContext.getString(
                            R.string.assign_job_pickup_required,
                        ),
                    )
                    .build(),
            )

            candidates.isEmpty() -> items.addItem(
                Row.Builder()
                    .setTitle(
                        carContext.getString(
                            R.string.assign_job_no_available_drivers,
                        ),
                    )
                    .build(),
            )

            else -> candidates.forEach { candidate ->
                val isSelected = candidate.driverId == selectedDriverId
                items.addItem(
                    Row.Builder()
                        .setTitle(
                            carContext.getString(
                                if (isSelected) {
                                    R.string.assign_job_selected_driver_rank
                                } else {
                                    R.string.assign_job_driver_rank
                                },
                                candidate.rank,
                                candidate.driverName,
                            ),
                        )
                        .apply {
                            candidate.pickupEstimateText()?.let {
                                pickupEstimate -> addText(pickupEstimate)
                            }
                        }
                        .setOnClickListener {
                            selectedDriverId = candidate.driverId
                            createUnassignedSelected = false
                            invalidate()
                        }
                        .build(),
                )
            }
        }

        if (
            initialJobId == null &&
            !isLoadingExistingJob &&
            !isLoadingCandidates &&
            fromPoint != null
        ) {
            items.addItem(createUnassignedRow())
        }

        return SectionedItemList.create(
            items.build(),
            carContext.getString(R.string.assign_job_recommended_drivers),
        )
    }

    private fun createUnassignedRow(): Row = Row.Builder()
        .setTitle(
            carContext.getString(
                if (createUnassignedSelected) {
                    R.string.assign_job_create_unassigned_selected
                } else {
                    R.string.assign_job_create_unassigned
                },
            ),
        )
        .setOnClickListener {
            selectedDriverId = null
            createUnassignedSelected = true
            invalidate()
        }
        .build()

    private fun assignAction(): Action {
        val canAssign = from.isNotBlank() &&
            (selectedDriverId != null || createUnassignedSelected)

        return Action.Builder()
            .setTitle(
                carContext.getString(
                    when {
                        isSubmitting && createUnassignedSelected ->
                            R.string.assign_job_creating
                        isSubmitting -> R.string.assign_job_assigning
                        createUnassignedSelected ->
                            R.string.assign_job_create_unassigned
                        else -> R.string.assign_job_assign
                    },
                ),
            )
            .setFlags(Action.FLAG_PRIMARY or Action.FLAG_IS_PERSISTENT)
            .setBackgroundColor(CarColor.BLUE)
            .setEnabled(canAssign && !isSubmitting)
            .setOnClickListener(::submitJob)
            .build()
    }

    private fun submitJob() {
        if (isSubmitting) return

        val repository = jobRepository
        val pickup = fromPoint
        val driverId = selectedDriverId
        if (
            repository == null ||
            pickup == null ||
            (!createUnassignedSelected && driverId == null)
        ) {
            showSubmissionToast(R.string.assign_job_submission_failed)
            return
        }

        isSubmitting = true
        invalidate()
        screenScope.launch {
            val succeeded = if (initialJobId == null) {
                repository.createJob(
                    NewJobRequest(
                        from = pickup.toJobCoordinates(),
                        to = toPoint?.toJobCoordinates(),
                        dueDate = candidateDueDate,
                        note = note.trim().ifBlank { null },
                        assignedDriverId = driverId,
                    ),
                ) is JobCreationResult.Success
            } else {
                driverId != null && assignExistingJob(
                    repository = repository,
                    jobId = initialJobId,
                    driverId = driverId,
                ) == JobActionResult.Success
            }
            currentCoroutineContext().ensureActive()

            isSubmitting = false
            if (succeeded) {
                showSubmissionToast(
                    if (createUnassignedSelected) {
                        R.string.assign_job_created_unassigned
                    } else {
                        R.string.assign_job_assigned
                    },
                )
                carContext
                    .getCarService(ScreenManager::class.java)
                    .pop()
            } else {
                showSubmissionToast(R.string.assign_job_submission_failed)
                invalidate()
            }
        }
    }

    private suspend fun assignExistingJob(
        repository: JobRepository,
        jobId: String,
        driverId: String,
    ): JobActionResult = repository.assignJob(
        jobId = jobId,
        driverId = driverId,
        destination = toPoint?.toJobCoordinates(),
        dueDate = candidateDueDate,
        note = note.trim().ifBlank { null },
    )

    private fun showSubmissionToast(message: Int) {
        CarToast.makeText(
            carContext,
            message,
            CarToast.LENGTH_SHORT,
        ).show()
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
            }
        }
    }

    private fun loadRecommendedDrivers() {
        candidatesJob?.cancel()
        candidatesJob = null
        selectedDriverId = null

        val pickup = fromPoint
        if (pickup == null) {
            candidates = emptyList()
            isLoadingCandidates = false
            candidatesFailed = false
            invalidate()
            return
        }

        val repository = jobRepository
        if (repository == null) {
            candidates = emptyList()
            isLoadingCandidates = false
            candidatesFailed = true
            invalidate()
            return
        }

        val destination = toPoint
        candidates = emptyList()
        isLoadingCandidates = true
        candidatesFailed = false
        invalidate()

        candidatesJob = screenScope.launch {
            val result = if (initialJobId != null && !routeWasEdited) {
                repository.getJobCandidates(initialJobId)
            } else {
                repository.getJobCandidates(
                    from = pickup.toJobCoordinates(),
                    to = destination?.toJobCoordinates(),
                    dueDate = candidateDueDate,
                )
            }
            currentCoroutineContext().ensureActive()
            if (fromPoint != pickup || toPoint != destination) return@launch

            candidates = if (result is JobCandidatesResult.Success) {
                result.candidates.sortedBy(JobCandidate::rank)
            } else {
                emptyList()
            }
            isLoadingCandidates = false
            candidatesFailed = result !is JobCandidatesResult.Success
            invalidate()
        }
    }

    private fun loadExistingJobIfNeeded(force: Boolean = false) {
        val jobId = initialJobId ?: return
        if (hasRequestedExistingJob && !force) return

        hasRequestedExistingJob = true
        existingJobLoadJob?.cancel()
        val repository = jobRepository
        if (repository == null) {
            isLoadingExistingJob = false
            candidatesFailed = true
            invalidate()
            return
        }

        isLoadingExistingJob = true
        candidatesFailed = false
        invalidate()
        existingJobLoadJob = screenScope.launch {
            val result = repository.getUnassignedJobs()
            currentCoroutineContext().ensureActive()
            val existingJob = (result as? UnassignedJobsResult.Success)
                ?.jobs
                ?.firstOrNull { job -> job.id == jobId }

            isLoadingExistingJob = false
            if (existingJob == null) {
                candidatesFailed = true
                invalidate()
                return@launch
            }

            from = existingJob.fromAddress
                ?.takeIf(String::isNotBlank)
                ?: from
            to = existingJob.toAddress
                ?.takeIf(String::isNotBlank)
                ?: to
            note = existingJob.note.orEmpty()
            fromPoint = existingJob.from?.toRoutePoint()
            toPoint = existingJob.to?.toRoutePoint()
            existingPickupTime = existingJob.dueDate
                ?.takeIf(String::isNotBlank)
            candidateDueDate = existingPickupTime ?: candidateDueDate
            routeWasEdited = false
            updateRoutePreview()
            loadRecommendedDrivers()
            invalidate()
        }
    }

    private fun retryRecommendedDrivers() {
        if (initialJobId != null && fromPoint == null) {
            loadExistingJobIfNeeded(force = true)
        } else {
            loadRecommendedDrivers()
        }
    }

    private fun JobCandidate.pickupEstimateText(): String? {
        val estimatedPickup = estimatedPickupAt
            ?.let { value ->
                runCatching { Instant.parse(value) }.getOrNull()
            }
            ?: return null
        val remainingSeconds = Duration
            .between(Instant.now(), estimatedPickup)
            .seconds
            .coerceAtLeast(0L)
        val remainingMinutes = remainingSeconds / SECONDS_PER_MINUTE +
            if (remainingSeconds % SECONDS_PER_MINUTE == 0L) 0L else 1L

        val hours = remainingMinutes / MINUTES_PER_HOUR
        val minutes = remainingMinutes % MINUTES_PER_HOUR
        return when {
            hours == 0L -> carContext.getString(
                R.string.assign_job_pickup_in_minutes,
                minutes,
            )
            minutes == 0L -> carContext.getString(
                R.string.assign_job_pickup_in_hours,
                hours,
            )
            else -> carContext.getString(
                R.string.assign_job_pickup_in_hours_minutes,
                hours,
                minutes,
            )
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

    private fun RoutePoint.toJobCoordinates(): JobCoordinates = JobCoordinates(
        latitude = latitude,
        longitude = longitude,
    )

    private fun JobCoordinates.toRoutePoint(): RoutePoint = RoutePoint(
        latitude = latitude,
        longitude = longitude,
    )

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val MINUTES_PER_HOUR = 60L
    }
}
