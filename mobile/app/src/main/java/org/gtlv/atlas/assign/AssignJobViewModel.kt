package org.gtlv.atlas.assign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job as CoroutineJob
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
import org.gtlv.core.geoservice.ResolveAddressResult
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.geoservice.RouteResult
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobCandidatesResult
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.job.JobRepository
import org.gtlv.core.role.RoleAvailabilityResult
import org.gtlv.core.role.RoleRepository
import org.gtlv.core.shift.ShiftRole

class AssignJobViewModel(
    private val jobRepository: JobRepository,
    private val geoServiceRepository: GeoServiceRepository,
    private val roleRepository: RoleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AssignJobUiState()
    )

    val uiState: StateFlow<AssignJobUiState> =
        _uiState.asStateFlow()

    private var savedJob: Job? = null
    private var addressSearchTask: CoroutineJob? = null
    private var saveChangesTask: CoroutineJob? = null
    private var candidatesTask: CoroutineJob? = null
    private var driversTask: CoroutineJob? = null
    private var routeTask: CoroutineJob? = null
    private var assignmentTask: CoroutineJob? = null

    fun clear() {
        cancelTasks()
        savedJob = null
        _uiState.value = AssignJobUiState()
    }

    fun load(job: Job) {
        cancelTasks()
        savedJob = job
        _uiState.value = AssignJobUiState(job = job)
        loadCandidates(job.id)
        loadDrivers()
        updateRouteAndCamera(job)
    }

    fun openAddressEditor(field: JobLocationField) {
        val job = _uiState.value.job ?: return

        if (
            field != JobLocationField.TO ||
            _uiState.value.isAssigning ||
            _uiState.value.isSavingChanges
        ) {
            return
        }

        addressSearchTask?.cancel()
        _uiState.update {
            it.copy(
                isAddressEditorOpen = true,
                editedLocationField = field,
                addressSearch = AddressSearchUiState(
                    query = job.toAddress
                        ?: job.to?.let { coordinates ->
                            "${coordinates.latitude}, " +
                                    coordinates.longitude
                        }
                        ?: ""
                )
            )
        }
    }

    fun closeAddressEditor() {
        if (_uiState.value.addressSearch.isSaving) {
            return
        }

        addressSearchTask?.cancel()
        _uiState.update {
            it.copy(
                isAddressEditorOpen = false,
                editedLocationField = null,
                addressSearch = AddressSearchUiState()
            )
        }
    }

    fun onAddressQueryChanged(query: String) {
        val state = _uiState.value
        if (
            !state.isAddressEditorOpen ||
            state.isSavingChanges
        ) {
            return
        }

        addressSearchTask?.cancel()
        val shouldSearch = query.isNotBlank()

        _uiState.update {
            it.copy(
                addressSearch = it.addressSearch.copy(
                    query = query,
                    suggestions = emptyList(),
                    isLoading = shouldSearch,
                    hasSearched = false,
                    hasError = false,
                    saveFailed = false
                )
            )
        }

        if (!shouldSearch) {
            return
        }

        addressSearchTask = viewModelScope.launch {
            val result = geoServiceRepository
                .resolveAddress(query)

            currentCoroutineContext().ensureActive()

            if (_uiState.value.addressSearch.query != query) {
                return@launch
            }

            _uiState.update {
                it.copy(
                    addressSearch = it.addressSearch.copy(
                        suggestions =
                            if (
                                result is ResolveAddressResult.Success
                            ) {
                                result.suggestions
                            } else {
                                emptyList()
                            },
                        isLoading = false,
                        hasSearched = true,
                        hasError =
                            result !is ResolveAddressResult.Success
                    )
                )
            }
        }
    }

    fun selectAddressSuggestion(
        suggestion: AddressSuggestion
    ) {
        val state = _uiState.value
        val job = state.job ?: return
        if (
            !state.isAddressEditorOpen ||
            state.editedLocationField != JobLocationField.TO ||
            state.isSavingChanges
        ) {
            return
        }

        addressSearchTask?.cancel()

        val updatedJob = job.copy(
            to = JobCoordinates(
                latitude = suggestion.latitude,
                longitude = suggestion.longitude
            ),
            toAddress = suggestion.displayName
        )

        _uiState.update {
            it.copy(
                job = updatedJob,
                isAddressEditorOpen = false,
                editedLocationField = null,
                addressSearch = AddressSearchUiState(),
                hasUnsavedChanges =
                    updatedJob.hasChangesFrom(savedJob),
                saveChangesFailed = false
            )
        }

        updateRouteAndCamera(updatedJob)
    }

    fun updateDueDate(dueDate: String) {
        val state = _uiState.value
        val job = state.job ?: return

        if (state.isAssigning || state.isSavingChanges) {
            return
        }

        val updatedJob = job.copy(dueDate = dueDate)
        _uiState.update {
            it.copy(
                job = updatedJob,
                hasUnsavedChanges =
                    updatedJob.hasChangesFrom(savedJob),
                saveChangesFailed = false
            )
        }
    }

    fun saveChanges() {
        val state = _uiState.value
        val job = state.job ?: return

        if (
            !state.hasUnsavedChanges ||
            state.isSavingChanges ||
            state.isAssigning
        ) {
            return
        }

        saveChangesTask?.cancel()
        saveChangesTask = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingChanges = true,
                    saveChangesFailed = false
                )
            }

            val result = persistChanges(job)
            currentCoroutineContext().ensureActive()

            if (result == JobActionResult.Success) {
                savedJob = job
                _uiState.update {
                    it.copy(
                        hasUnsavedChanges = false,
                        isSavingChanges = false,
                        saveChangesFailed = false
                    )
                }
                loadCandidates(job.id)
            } else {
                _uiState.update {
                    it.copy(
                        isSavingChanges = false,
                        saveChangesFailed = true
                    )
                }
            }
        }
    }

    private fun Job.hasChangesFrom(saved: Job?): Boolean =
        saved == null ||
                to != saved.to ||
                dueDate != saved.dueDate

    fun retryCandidates() {
        _uiState.value.job?.id?.let(::loadCandidates)
        loadDrivers()
    }

    fun requestAssignment(candidate: JobCandidate) {
        if (_uiState.value.isAssigning) {
            return
        }

        _uiState.update {
            it.copy(
                pendingCandidate = candidate,
                assignmentFailed = false
            )
        }
    }

    fun dismissAssignment() {
        if (_uiState.value.isAssigning) {
            return
        }

        _uiState.update {
            it.copy(
                pendingCandidate = null,
                assignmentFailed = false
            )
        }
    }

    fun confirmAssignment() {
        val state = _uiState.value
        val job = state.job ?: return
        val candidate = state.pendingCandidate ?: return

        if (state.isAssigning) {
            return
        }

        assignmentTask?.cancel()
        assignmentTask = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAssigning = true,
                    isSavingChanges =
                        it.hasUnsavedChanges,
                    assignmentFailed = false
                )
            }

            val saveResult = if (state.hasUnsavedChanges) {
                persistChanges(job)
            } else {
                JobActionResult.Success
            }

            val result = if (
                saveResult == JobActionResult.Success
            ) {
                savedJob = job
                jobRepository.assignJob(
                    jobId = job.id,
                    driverId = candidate.driverId
                )
            } else {
                saveResult
            }

            currentCoroutineContext().ensureActive()

            _uiState.update {
                if (result == JobActionResult.Success) {
                    it.copy(
                        isAssigning = false,
                        isSavingChanges = false,
                        hasUnsavedChanges = false,
                        pendingCandidate = null,
                        assignmentCompleted = true
                    )
                } else {
                    it.copy(
                        isAssigning = false,
                        isSavingChanges = false,
                        hasUnsavedChanges =
                            if (
                                saveResult ==
                                JobActionResult.Success
                            ) {
                                false
                            } else {
                                it.hasUnsavedChanges
                            },
                        saveChangesFailed =
                            saveResult != JobActionResult.Success,
                        assignmentFailed = true
                    )
                }
            }
        }
    }

    private suspend fun persistChanges(
        job: Job
    ): JobActionResult = jobRepository.updateJobDetails(
        jobId = job.id,
        destination = job.to,
        dueDate = job.dueDate
            ?: java.time.Instant.now().toString()
    )

    private fun loadCandidates(jobId: String) {
        candidatesTask?.cancel()
        candidatesTask = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingCandidates = true,
                    candidatesFailed = false
                )
            }

            val result = jobRepository
                .getJobCandidates(jobId)

            currentCoroutineContext().ensureActive()

            _uiState.update {
                when (result) {
                    is JobCandidatesResult.Success -> {
                        it.copy(
                            candidates = result.candidates,
                            isLoadingCandidates = false,
                            candidatesFailed = false
                        )
                    }

                    else -> {
                        it.copy(
                            candidates = emptyList(),
                            isLoadingCandidates = false,
                            candidatesFailed = true
                        )
                    }
                }
            }
        }
    }

    private fun loadDrivers() {
        driversTask?.cancel()
        driversTask = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingDrivers = true,
                    driversFailed = false
                )
            }

            val result = roleRepository.getAvailability()
            currentCoroutineContext().ensureActive()

            _uiState.update {
                when (result) {
                    is RoleAvailabilityResult.Success -> {
                        val drivers = result.availability
                            .assignedRoles
                            .asSequence()
                            .filter { assignedRole ->
                                assignedRole.role ==
                                        ShiftRole.DRIVER
                            }
                            .distinctBy { assignedRole ->
                                assignedRole.driverId
                            }
                            .map { assignedRole ->
                                JobCandidate(
                                    driverId =
                                        assignedRole.driverId,
                                    driverName =
                                        assignedRole.name
                                            ?.takeIf(String::isNotBlank)
                                            ?: assignedRole.driverId,
                                    rank = 0,
                                    summary = null
                                )
                            }
                            .sortedBy { driver ->
                                driver.driverName.lowercase()
                            }
                            .toList()

                        it.copy(
                            allDrivers = drivers,
                            isLoadingDrivers = false,
                            driversFailed = false
                        )
                    }

                    else -> {
                        it.copy(
                            allDrivers = emptyList(),
                            isLoadingDrivers = false,
                            driversFailed = true
                        )
                    }
                }
            }
        }
    }

    private fun updateRouteAndCamera(job: Job) {
        routeTask?.cancel()

        val from = job.from?.toRoutePoint()
        val to = job.to?.toRoutePoint()

        when {
            from == null -> {
                _uiState.update {
                    it.copy(
                        route = null,
                        isLoadingRoute = false,
                        routeFailed = false,
                        cameraFocusPoints = emptyList()
                    )
                }
            }

            to == null -> {
                _uiState.update {
                    it.copy(
                        route = null,
                        isLoadingRoute = false,
                        routeFailed = false,
                        cameraFocusPoints = listOf(from),
                        cameraFocusRequestId =
                            it.cameraFocusRequestId + 1
                    )
                }
            }

            else -> {
                _uiState.update {
                    it.copy(
                        route = null,
                        isLoadingRoute = true,
                        routeFailed = false,
                        cameraFocusPoints = listOf(from, to),
                        cameraFocusRequestId =
                            it.cameraFocusRequestId + 1
                    )
                }

                routeTask = viewModelScope.launch {
                    val result = geoServiceRepository
                        .requestRoute(
                            origin = from,
                            destination = to
                        )

                    currentCoroutineContext().ensureActive()

                    _uiState.update {
                        when (result) {
                            is RouteResult.Success -> {
                                it.copy(
                                    route = result.route,
                                    isLoadingRoute = false,
                                    routeFailed = false,
                                    cameraFocusPoints =
                                        result.route.points,
                                    cameraFocusRequestId =
                                        it.cameraFocusRequestId + 1
                                )
                            }

                            else -> {
                                it.copy(
                                    route = null,
                                    isLoadingRoute = false,
                                    routeFailed = true,
                                    cameraFocusPoints =
                                        listOf(from, to),
                                    cameraFocusRequestId =
                                        it.cameraFocusRequestId + 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun JobCoordinates.toRoutePoint(): RoutePoint? {
        return RoutePoint(
            latitude = latitude,
            longitude = longitude
        ).takeIf(RoutePoint::isValid)
    }

    private fun cancelTasks() {
        addressSearchTask?.cancel()
        saveChangesTask?.cancel()
        candidatesTask?.cancel()
        driversTask?.cancel()
        routeTask?.cancel()
        assignmentTask?.cancel()
    }
}
