package org.gtlv.atlas.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gtlv.atlas.address.AddressSearchUiState
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.geoservice.RouteProgress
import org.gtlv.core.geoservice.RouteProgressCalculator
import org.gtlv.core.geoservice.RouteResult
import org.gtlv.core.geoservice.ResolveAddressResult
import org.gtlv.core.job.CollectedJobStateStore
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobsResult
import org.gtlv.core.location.AtlasLocation
import org.gtlv.core.location.LocationState
import org.gtlv.core.location.VehicleHeadingEstimator
import org.gtlv.core.telemetry.TelemetryProvider
import org.gtlv.core.telemetry.TelemetryVehicleState

class MainScreenViewModel(
    private val jobRepository: JobRepository,
    private val geoServiceRepository:
    GeoServiceRepository,
    private val telemetryProvider:
    TelemetryProvider,
    private val collectedJobStore:
    CollectedJobStateStore
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(MainScreenUiState())

    val uiState: StateFlow<MainScreenUiState> =
        _uiState.asStateFlow()

    private var activeUserId: String? = null
    private var refreshJob: Job? = null
    private var jobActionTask: Job? = null
    private var addressSearchTask: Job? = null
    private var locationUpdateTask: Job? = null
    private var routeRequestTask: Job? = null
    private var collectedJobId: String? = null
    private var latestLocation: AtlasLocation? = null
    private var latestVehicleHeadingDegrees: Int? = null
    private val vehicleHeadingEstimator =
        VehicleHeadingEstimator()
    private var pickupRouteOrigin:
        NavigationRouteOrigin? = null
    private var destinationRouteOrigin:
        NavigationRouteOrigin? = null
    private var activeRouteRequest:
        NavigationRouteRequest? = null
    private var loadedRouteRequest:
        NavigationRouteRequest? = null
    private var routeRequestGeneration = 0L
    private var offRouteSampleCount = 0
    private var wrongWaySampleCount = 0
    private var lastAutomaticRerouteAtMillis = 0L
    private var navigationLanguage =
        DEFAULT_NAVIGATION_LANGUAGE

    fun updateNavigationLanguage(
        language: String
    ) {
        if (
            language == navigationLanguage ||
            language.length !in 2..5
        ) {
            return
        }

        navigationLanguage = language
        routeRequestTask?.cancel()
        routeRequestTask = null
        routeRequestGeneration += 1
        activeRouteRequest = null
        loadedRouteRequest = null

        reconcileNavigation()
    }

    fun loadJobsForUser(
        userId: String
    ) {
        if (activeUserId == userId) {
            return
        }

        cancelAllTasks()

        activeUserId = userId
        collectedJobId =
            collectedJobStore.getCollectedJobId(userId)

        _uiState.value = MainScreenUiState()

        telemetryProvider.setVehicleState(
            TelemetryVehicleState.FREE
        )

        refresh()
    }

    fun refresh() {
        val state = _uiState.value

        if (
            activeUserId == null ||
            state.isStartingNextJob ||
            state.isCancellingCurrentJob ||
            state.addressSearch.isSaving
        ) {
            return
        }

        refreshJob?.cancel()

        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasError = false,
                    startNextJobFailed = false,
                    cancelCurrentJobFailed = false
                )
            }

            val result = jobRepository.getJobs()

            currentCoroutineContext()
                .ensureActive()

            when (result) {
                is JobsResult.Success -> {
                    applyJobs(result)
                }

                else -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasError = true
                        )
                    }
                }
            }
        }
    }

    fun startNextJob() {
        val state = _uiState.value

        if (
            activeUserId == null ||
            state.currentJob != null ||
            state.queuedJobs.isEmpty() ||
            state.isLoading ||
            state.isStartingNextJob ||
            state.isCancellingCurrentJob ||
            state.isAddressEditorOpen ||
            state.addressSearch.isSaving
        ) {
            return
        }

        val nextJob = state.queuedJobs.first()

        refreshJob?.cancel()
        jobActionTask?.cancel()

        jobActionTask = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStartingNextJob = true,
                    startNextJobFailed = false,
                    cancelCurrentJobFailed = false
                )
            }

            val result = jobRepository.startJob(
                jobId = nextJob.id
            )

            currentCoroutineContext()
                .ensureActive()

            when (result) {
                JobActionResult.Success -> {
                    clearNavigation()
                    reloadJobsAfterAction()
                }

                else -> {
                    _uiState.update {
                        it.copy(
                            isStartingNextJob = false,
                            startNextJobFailed = true
                        )
                    }
                }
            }
        }
    }

    fun cancelCurrentJob() {
        val state = _uiState.value

        if (
            activeUserId == null ||
            state.currentJob == null ||
            state.isLoading ||
            state.isStartingNextJob ||
            state.isCancellingCurrentJob ||
            state.isAddressEditorOpen ||
            state.addressSearch.isSaving
        ) {
            return
        }

        val currentJobId = state.currentJob.id

        refreshJob?.cancel()
        jobActionTask?.cancel()

        jobActionTask = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCancellingCurrentJob = true,
                    cancelCurrentJobFailed = false,
                    startNextJobFailed = false
                )
            }

            val result =
                jobRepository.cancelJob(
                    jobId = currentJobId
                )

            currentCoroutineContext()
                .ensureActive()

            when (result) {
                JobActionResult.Success -> {
                    clearNavigation()
                    reloadJobsAfterAction()
                }

                else -> {
                    _uiState.update {
                        it.copy(
                            isCancellingCurrentJob =
                                false,
                            cancelCurrentJobFailed =
                                true
                        )
                    }
                }
            }
        }
    }

    fun personCollected() {
        val state = _uiState.value
        val currentJob = state.currentJob ?: return
        val userId = activeUserId ?: return

        if (
            state.isLoading ||
            state.isStartingNextJob ||
            state.isCancellingCurrentJob ||
            state.isPersonCollected ||
            state.addressSearch.isSaving
        ) {
            return
        }

        collectedJobId = currentJob.id

        collectedJobStore.setCollectedJobId(
            userId = userId,
            jobId = currentJob.id
        )

        telemetryProvider.setVehicleState(
            TelemetryVehicleState.OCCUPIED
        )

        destinationRouteOrigin = latestLocation
            ?.toNavigationRouteOrigin(
                latestVehicleHeadingDegrees
            )

        val destinationIsMissing =
            currentJob.to == null

        addressSearchTask?.cancel()
        addressSearchTask = null

        _uiState.update {
            it.copy(
                isPersonCollected = true,
                isAddressEditorOpen =
                    destinationIsMissing,
                editedLocationField =
                    if (destinationIsMissing) {
                        JobLocationField.TO
                    } else {
                        null
                    },
                addressSearch =
                    AddressSearchUiState()
            )
        }

        reconcileNavigation()
    }

    private suspend fun reloadJobsAfterAction() {
        val result = jobRepository.getJobs()

        currentCoroutineContext()
            .ensureActive()

        when (result) {
            is JobsResult.Success -> {
                applyJobs(result)
            }

            else -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isStartingNextJob = false,
                        isCancellingCurrentJob =
                            false,
                        hasError = true
                    )
                }
            }
        }
    }

    private fun applyJobs(
        result: JobsResult.Success
    ) {
        val previousJobId = _uiState.value.currentJob?.id
        val currentJob = result.currentJob
        val userId = activeUserId

        val personCollected =
            currentJob != null &&
                    currentJob.id == collectedJobId

        if (previousJobId != currentJob?.id) {
            pickupRouteOrigin = null
            destinationRouteOrigin = null
            clearNavigation()
        }

        if (
            collectedJobId != null &&
            !personCollected
        ) {
            if (userId != null) {
                collectedJobStore.clearCollectedJobId(
                    userId
                )
            }

            collectedJobId = null
        }

        telemetryProvider.setVehicleState(
            when {
                currentJob == null -> {
                    TelemetryVehicleState.FREE
                }

                personCollected -> {
                    TelemetryVehicleState.OCCUPIED
                }

                else -> {
                    TelemetryVehicleState.ON_THE_WAY
                }
            }
        )

        _uiState.update {
            it.copy(
                isLoading = false,
                currentJob = currentJob,
                queuedJobs = result.queuedJobs,
                isJobListExpanded =
                    it.isJobListExpanded &&
                            result.queuedJobs.isNotEmpty(),
                hasError = false,
                isStartingNextJob = false,
                startNextJobFailed = false,
                isCancellingCurrentJob = false,
                cancelCurrentJobFailed = false,
                isPersonCollected = personCollected
            )
        }

        reconcileNavigation()
    }

    fun updateLocationState(
        locationState: LocationState
    ) {
        val availableLocation =
            (locationState as? LocationState.Available)
                ?.location

        if (availableLocation != null) {
            latestLocation = availableLocation
            latestVehicleHeadingDegrees =
                vehicleHeadingEstimator.update(
                    availableLocation
                )

            if (
                _uiState.value.isPersonCollected &&
                destinationRouteOrigin == null
            ) {
                destinationRouteOrigin = availableLocation
                    .toNavigationRouteOrigin(
                        latestVehicleHeadingDegrees
                    )
            }
        }

        val navigation = _uiState.value.navigation
        val route = navigation.route

        if (
            availableLocation != null &&
            route != null &&
            navigation.status != NavigationStatus.Idle
        ) {
            val progress = RouteProgressCalculator.calculate(
                route = route,
                location = RoutePoint(
                    latitude = availableLocation.latitude,
                    longitude = availableLocation.longitude
                ),
                previousShapeIndex = navigation.progress
                    ?.routeShapeIndex
                    ?: 0,
                previousProgress = navigation.progress
            )

            _uiState.update {
                it.copy(
                    navigation = it.navigation.copy(
                        progress = progress
                    )
                )
            }

            evaluateAutomaticReroute(
                location = availableLocation,
                navigation = navigation,
                progress = progress
            )
        }

        if (
            _uiState.value.navigation.status ==
            NavigationStatus.WaitingForLocation
        ) {
            reconcileNavigation()
        }
    }

    fun toggleJobList() {
        _uiState.update {
            it.copy(
                isJobListExpanded =
                    !it.isJobListExpanded
            )
        }
    }

    fun openDestinationEditor() {
        val state = _uiState.value

        if (
            state.currentJob == null ||
            state.isLoading ||
            state.isStartingNextJob ||
            state.isCancellingCurrentJob ||
            state.addressSearch.isSaving
        ) {
            return
        }

        addressSearchTask?.cancel()
        addressSearchTask = null

        _uiState.update {
            it.copy(
                isAddressEditorOpen = true,
                editedLocationField =
                    JobLocationField.TO,
                addressSearch =
                    AddressSearchUiState()
            )
        }
    }

    fun closeAddressEditor() {
        if (_uiState.value.addressSearch.isSaving) {
            return
        }

        addressSearchTask?.cancel()
        addressSearchTask = null

        _uiState.update {
            it.copy(
                isAddressEditorOpen = false,
                editedLocationField = null,
                addressSearch =
                    AddressSearchUiState()
            )
        }
    }

    fun onAddressQueryChanged(
        query: String
    ) {
        val state = _uiState.value

        if (
            !state.isAddressEditorOpen ||
            state.addressSearch.isSaving
        ) {
            return
        }

        addressSearchTask?.cancel()
        addressSearchTask = null

        val shouldSearch = query.isNotBlank()

        _uiState.update {
            it.copy(
                addressSearch =
                    it.addressSearch.copy(
                        query = query,
                        suggestions = emptyList(),
                        isLoading = shouldSearch,
                        hasError = false,
                        saveFailed = false
                    )
            )
        }

        if (!shouldSearch) {
            return
        }

        addressSearchTask =
            viewModelScope.launch {
                val result =
                    geoServiceRepository
                        .resolveAddress(
                            address = query
                        )

                /*
                 * An older cancelled request must never
                 * replace the results for newer input.
                 */
                currentCoroutineContext()
                    .ensureActive()

                if (
                    _uiState.value
                        .addressSearch
                        .query != query
                ) {
                    return@launch
                }

                when (result) {
                    is ResolveAddressResult.Success -> {
                        _uiState.update {
                            it.copy(
                                addressSearch =
                                    it.addressSearch.copy(
                                        suggestions =
                                            result.suggestions,
                                        isLoading = false,
                                        hasError = false
                                    )
                            )
                        }
                    }

                    else -> {
                        _uiState.update {
                            it.copy(
                                addressSearch =
                                    it.addressSearch.copy(
                                        suggestions =
                                            emptyList(),
                                        isLoading = false,
                                        hasError = true
                                    )
                            )
                        }
                    }
                }
            }
    }

    fun selectAddressSuggestion(
        suggestion: AddressSuggestion
    ) {
        val state = _uiState.value
        val currentJob = state.currentJob
            ?: return
        val locationField =
            state.editedLocationField
                ?: return

        if (
            !state.isAddressEditorOpen ||
            state.addressSearch.isSaving
        ) {
            return
        }

        addressSearchTask?.cancel()
        addressSearchTask = null

        locationUpdateTask?.cancel()

        locationUpdateTask =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        addressSearch =
                            it.addressSearch.copy(
                                query =
                                    suggestion.displayName,
                                suggestions =
                                    emptyList(),
                                isLoading = false,
                                hasError = false,
                                isSaving = true,
                                saveFailed = false
                            )
                    )
                }

                val updateResult =
                    jobRepository
                        .updateJobLocation(
                            jobId = currentJob.id,
                            field = locationField,
                            latitude =
                                suggestion.latitude,
                            longitude =
                                suggestion.longitude
                        )

                currentCoroutineContext()
                    .ensureActive()

                when (updateResult) {
                    JobActionResult.Success -> {
                        reloadJobsAfterLocationUpdate()
                    }

                    else -> {
                        _uiState.update {
                            it.copy(
                                addressSearch =
                                    it.addressSearch.copy(
                                        isSaving = false,
                                        saveFailed = true
                                    )
                            )
                        }
                    }
                }
            }
    }

    private suspend fun
            reloadJobsAfterLocationUpdate() {
        val result = jobRepository.getJobs()

        currentCoroutineContext()
            .ensureActive()

        when (result) {
            is JobsResult.Success -> {
                applyJobs(result)

                _uiState.update {
                    it.copy(
                        isAddressEditorOpen = false,
                        editedLocationField = null,
                        addressSearch =
                            AddressSearchUiState()
                    )
                }
            }

            else -> {
                _uiState.update {
                    it.copy(
                        isAddressEditorOpen = false,
                        editedLocationField = null,
                        addressSearch =
                            AddressSearchUiState(),
                        hasError = true
                    )
                }
            }
        }
    }

    private fun reconcileNavigation() {
        val state = _uiState.value
        val plan = NavigationRoutePlanner.plan(
            currentJob = state.currentJob,
            isPersonCollected = state.isPersonCollected,
            latestLocation = latestLocation,
            latestHeadingDegrees =
                latestVehicleHeadingDegrees,
            pickupOrigin = pickupRouteOrigin,
            destinationOrigin = destinationRouteOrigin
        )

        when (plan) {
            NavigationRoutePlan.None -> {
                clearNavigation()
            }

            is NavigationRoutePlan.WaitingForLocation -> {
                clearNavigationRequest()
                _uiState.update {
                    it.copy(
                        navigation = NavigationUiState(
                            jobId = state.currentJob?.id,
                            phase = plan.phase,
                            status =
                                NavigationStatus.WaitingForLocation
                        )
                    )
                }
            }

            NavigationRoutePlan.PickupUnavailable -> {
                clearNavigationRequest()
                _uiState.update {
                    it.copy(
                        navigation = NavigationUiState(
                            jobId = state.currentJob?.id,
                            phase = if (state.isPersonCollected) {
                                NavigationPhase.ToDestination
                            } else {
                                NavigationPhase.ToPickup
                            },
                            status =
                                NavigationStatus.PickupUnavailable
                        )
                    )
                }
            }

            NavigationRoutePlan.WaitingForDestination -> {
                clearNavigationRequest()
                _uiState.update {
                    it.copy(
                        navigation = NavigationUiState(
                            jobId = state.currentJob?.id,
                            phase = NavigationPhase.ToDestination,
                            status = NavigationStatus
                                .WaitingForDestination
                        )
                    )
                }
            }

            is NavigationRoutePlan.Request -> {
                if (
                    plan.value.phase ==
                    NavigationPhase.ToPickup &&
                    pickupRouteOrigin == null
                ) {
                    pickupRouteOrigin =
                        plan.value.toRouteOrigin()
                }

                if (
                    plan.value.phase ==
                    NavigationPhase.ToDestination &&
                    destinationRouteOrigin == null
                ) {
                    destinationRouteOrigin =
                        plan.value.toRouteOrigin()
                }

                requestRoute(plan.value)
            }
        }
    }

    private fun requestRoute(
        request: NavigationRouteRequest
    ) {
        if (
            loadedRouteRequest == request &&
            _uiState.value.navigation.route != null
        ) {
            return
        }

        if (
            activeRouteRequest == request &&
            routeRequestTask?.isActive == true
        ) {
            return
        }

        routeRequestTask?.cancel()
        routeRequestGeneration += 1
        val generation = routeRequestGeneration
        activeRouteRequest = request

        val currentNavigation = _uiState.value.navigation
        val keepCurrentRoute =
            currentNavigation.jobId == request.jobId &&
                    currentNavigation.phase == request.phase &&
                    currentNavigation.route != null

        if (!keepCurrentRoute) {
            loadedRouteRequest = null
        }

        _uiState.update {
            it.copy(
                navigation = NavigationUiState(
                    jobId = request.jobId,
                    phase = request.phase,
                    status = NavigationStatus.Loading,
                    route = if (keepCurrentRoute) {
                        currentNavigation.route
                    } else {
                        null
                    },
                    progress = if (keepCurrentRoute) {
                        currentNavigation.progress
                    } else {
                        null
                    }
                )
            )
        }

        routeRequestTask = viewModelScope.launch {
            val result = geoServiceRepository.requestRoute(
                origin = request.origin,
                destination = request.destination,
                headingDegrees = request.headingDegrees,
                language = navigationLanguage
            )

            currentCoroutineContext().ensureActive()

            if (
                generation != routeRequestGeneration ||
                activeRouteRequest != request ||
                _uiState.value.currentJob?.id != request.jobId
            ) {
                return@launch
            }

            when (result) {
                is RouteResult.Success -> {
                    loadedRouteRequest = request
                    activeRouteRequest = null
                    offRouteSampleCount = 0
                    wrongWaySampleCount = 0

                    val initialProgress = latestLocation?.let {
                        location ->
                        RouteProgressCalculator.calculate(
                            route = result.route,
                            location = RoutePoint(
                                latitude = location.latitude,
                                longitude = location.longitude
                            )
                        )
                    } ?: RouteProgressCalculator.initial(
                        result.route
                    )

                    _uiState.update {
                        it.copy(
                            navigation = NavigationUiState(
                                jobId = request.jobId,
                                phase = request.phase,
                                status = NavigationStatus.Ready,
                                route = result.route,
                                progress = initialProgress
                            )
                        )
                    }
                }

                else -> {
                    activeRouteRequest = null
                    val navigation = _uiState.value.navigation

                    _uiState.update {
                        it.copy(
                            navigation = navigation.copy(
                                status = NavigationStatus.Error,
                                error = result.toNavigationError()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun RouteResult.toNavigationError(): NavigationError {
        return when (this) {
            RouteResult.Unauthorized ->
                NavigationError.Unauthorized

            RouteResult.NetworkError ->
                NavigationError.Network

            is RouteResult.RouterError ->
                NavigationError.Router

            is RouteResult.ServerError ->
                NavigationError.Server

            RouteResult.MalformedJson,
            RouteResult.InvalidResponse ->
                NavigationError.InvalidResponse

            is RouteResult.Success ->
                error("A successful route has no error")
        }
    }

    private fun evaluateAutomaticReroute(
        location: AtlasLocation,
        navigation: NavigationUiState,
        progress: RouteProgress
    ) {
        if (
            navigation.phase == NavigationPhase.None ||
            (
                    navigation.status != NavigationStatus.Ready &&
                            navigation.status != NavigationStatus.Error
                    ) ||
            routeRequestTask?.isActive == true
        ) {
            return
        }

        val accuracyThresholdKilometers =
            (location.accuracyMeters ?: 0f) *
                    GPS_ACCURACY_MULTIPLIER / 1000.0
        val offRouteThreshold = maxOf(
            MINIMUM_OFF_ROUTE_DISTANCE_KILOMETERS,
            accuracyThresholdKilometers
        )
        val isOffRoute = progress.distanceFromRouteKilometers
            ?.let { it > offRouteThreshold }
            ?: false

        offRouteSampleCount = if (isOffRoute) {
            offRouteSampleCount + 1
        } else {
            0
        }
        wrongWaySampleCount = if (progress.isMovingAgainstRoute) {
            wrongWaySampleCount + 1
        } else {
            0
        }

        if (
            offRouteSampleCount < DEVIATION_SAMPLES_FOR_REROUTE &&
            wrongWaySampleCount < DEVIATION_SAMPLES_FOR_REROUTE
        ) {
            return
        }

        val now = System.currentTimeMillis()
        if (
            lastAutomaticRerouteAtMillis != 0L &&
            now - lastAutomaticRerouteAtMillis <
            AUTOMATIC_REROUTE_COOLDOWN_MILLIS
        ) {
            return
        }

        val origin = location.toNavigationRouteOrigin(
            latestVehicleHeadingDegrees
        ) ?: return

        when (navigation.phase) {
            NavigationPhase.ToPickup -> {
                pickupRouteOrigin = origin
            }

            NavigationPhase.ToDestination -> {
                destinationRouteOrigin = origin
            }

            NavigationPhase.None -> return
        }

        offRouteSampleCount = 0
        wrongWaySampleCount = 0
        lastAutomaticRerouteAtMillis = now
        loadedRouteRequest = null
        reconcileNavigation()
    }

    private fun clearNavigationRequest() {
        routeRequestGeneration += 1
        routeRequestTask?.cancel()
        routeRequestTask = null
        activeRouteRequest = null
        loadedRouteRequest = null
    }

    private fun clearNavigation() {
        clearNavigationRequest()
        offRouteSampleCount = 0
        wrongWaySampleCount = 0
        lastAutomaticRerouteAtMillis = 0L
        _uiState.update {
            it.copy(navigation = NavigationUiState())
        }
    }

    fun clearJobs() {
        activeUserId = null

        collectedJobId = null
        latestLocation = null
        latestVehicleHeadingDegrees = null
        vehicleHeadingEstimator.reset()
        pickupRouteOrigin = null
        destinationRouteOrigin = null

        telemetryProvider.setVehicleState(
            TelemetryVehicleState.FREE
        )

        cancelAllTasks()

        _uiState.value = MainScreenUiState(
            isLoading = false
        )
    }

    private fun AtlasLocation.toNavigationRouteOrigin(
        headingDegrees: Int?
    ): NavigationRouteOrigin? {
        val point = RoutePoint(
            latitude = latitude,
            longitude = longitude
        ).takeIf(RoutePoint::isValid) ?: return null

        return NavigationRouteOrigin(
            point = point,
            headingDegrees = headingDegrees
        )
    }

    private fun NavigationRouteRequest.toRouteOrigin():
            NavigationRouteOrigin = NavigationRouteOrigin(
        point = origin,
        headingDegrees = headingDegrees
    )

    private fun cancelAllTasks() {
        refreshJob?.cancel()
        refreshJob = null

        jobActionTask?.cancel()
        jobActionTask = null

        addressSearchTask?.cancel()
        addressSearchTask = null

        locationUpdateTask?.cancel()
        locationUpdateTask = null

        clearNavigationRequest()
    }

    private companion object {
        const val MINIMUM_OFF_ROUTE_DISTANCE_KILOMETERS = 0.03
        const val GPS_ACCURACY_MULTIPLIER = 2.5
        const val DEVIATION_SAMPLES_FOR_REROUTE = 2
        const val AUTOMATIC_REROUTE_COOLDOWN_MILLIS = 15_000L
        const val DEFAULT_NAVIGATION_LANGUAGE = "en"
    }
}
