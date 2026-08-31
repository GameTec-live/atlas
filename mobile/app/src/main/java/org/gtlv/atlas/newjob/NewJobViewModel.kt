package org.gtlv.atlas.newjob

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
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
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobCandidatesResult
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.job.JobCreationResult
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.NewJobRequest
import org.gtlv.core.role.RoleAvailabilityResult
import org.gtlv.core.role.RoleRepository

class NewJobViewModel(
    private val jobRepository: JobRepository,
    private val geoServiceRepository: GeoServiceRepository,
    private val roleRepository: RoleRepository,
    private val now: () -> Instant = Instant::now
) : ViewModel() {

    private val _uiState = MutableStateFlow(newState())
    val uiState: StateFlow<NewJobUiState> = _uiState.asStateFlow()

    private var addressSearchTask: CoroutineJob? = null
    private var candidatesTask: CoroutineJob? = null
    private var driversTask: CoroutineJob? = null
    private var routeTask: CoroutineJob? = null
    private var creationTask: CoroutineJob? = null
    private var isLoaded = false

    fun load() {
        if (isLoaded) return

        isLoaded = true
        cancelTasks()
        _uiState.value = newState()
        loadDrivers()
    }

    fun clear() {
        isLoaded = false
        cancelTasks()
        _uiState.value = newState()
    }

    fun openAddressEditor(field: JobLocationField) {
        val state = _uiState.value
        if (state.isCreating) return

        val existingValue = when (field) {
            JobLocationField.FROM -> state.fromAddress
            JobLocationField.TO -> state.toAddress
        }.orEmpty()

        addressSearchTask?.cancel()
        _uiState.update {
            it.copy(
                isAddressEditorOpen = true,
                editedLocationField = field,
                addressSearch = AddressSearchUiState(query = existingValue)
            )
        }
    }

    fun closeAddressEditor() {
        val state = _uiState.value
        if (state.addressSearch.isSaving) return
        if (
            !state.isAddressEditorOpen &&
            state.addressSearch == AddressSearchUiState()
        ) {
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
        if (!state.isAddressEditorOpen || state.isCreating) return

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

        if (!shouldSearch) return

        addressSearchTask = viewModelScope.launch {
            val result = geoServiceRepository.resolveAddress(query)
            currentCoroutineContext().ensureActive()
            if (_uiState.value.addressSearch.query != query) return@launch

            _uiState.update {
                it.copy(
                    addressSearch = it.addressSearch.copy(
                        suggestions = if (result is ResolveAddressResult.Success) {
                            result.suggestions
                        } else {
                            emptyList()
                        },
                        isLoading = false,
                        hasSearched = true,
                        hasError = result !is ResolveAddressResult.Success
                    )
                )
            }
        }
    }

    fun selectAddressSuggestion(suggestion: AddressSuggestion) {
        val state = _uiState.value
        val field = state.editedLocationField ?: return
        if (!state.isAddressEditorOpen || state.isCreating) return

        addressSearchTask?.cancel()
        val coordinates = JobCoordinates(
            latitude = suggestion.latitude,
            longitude = suggestion.longitude
        )
        _uiState.update {
            when (field) {
                JobLocationField.FROM -> it.copy(
                    from = coordinates,
                    fromAddress = suggestion.displayName,
                    isAddressEditorOpen = false,
                    editedLocationField = null,
                    addressSearch = AddressSearchUiState(),
                    creationFailed = false
                )

                JobLocationField.TO -> it.copy(
                    to = coordinates,
                    toAddress = suggestion.displayName,
                    isAddressEditorOpen = false,
                    editedLocationField = null,
                    addressSearch = AddressSearchUiState(),
                    creationFailed = false
                )
            }
        }
        updateRouteAndCamera()
        loadCandidates()
    }

    fun updateDueDate(dueDate: String) {
        if (dueDate.isBlank() || _uiState.value.isCreating) return
        _uiState.update { it.copy(dueDate = dueDate, creationFailed = false) }
        loadCandidates()
    }

    fun updateNote(note: String) {
        if (_uiState.value.isCreating) return
        _uiState.update {
            it.copy(
                note = note.take(NEW_JOB_NOTE_MAX_LENGTH),
                creationFailed = false
            )
        }
    }

    fun retryCandidates() {
        loadCandidates()
        loadDrivers()
    }

    fun requestDriverCreation(candidate: JobCandidate) {
        val state = _uiState.value
        if (
            !state.canCreate ||
            state.isLoadingCandidates ||
            (
                candidate !in state.candidates &&
                    candidate !in state.allDrivers
                )
        ) {
            return
        }

        _uiState.update {
            it.copy(
                pendingCandidate = candidate,
                isConfirmingUnassigned = false,
                creationFailed = false
            )
        }
    }

    fun requestUnassignedCreation() {
        if (!_uiState.value.canCreate) return
        _uiState.update {
            it.copy(
                pendingCandidate = null,
                isSelectingUnassignedDueDate = true,
                dueDatePickerRequestId =
                    it.dueDatePickerRequestId + 1,
                isConfirmingUnassigned = false,
                creationFailed = false
            )
        }
    }

    fun confirmUnassignedDueDate(dueDate: String) {
        if (
            dueDate.isBlank() ||
            !_uiState.value.isSelectingUnassignedDueDate
        ) {
            return
        }

        _uiState.update {
            it.copy(
                dueDate = dueDate,
                isSelectingUnassignedDueDate = false,
                isConfirmingUnassigned = true,
                creationFailed = false
            )
        }
        loadCandidates()
    }

    fun dismissUnassignedDueDatePicker() {
        _uiState.update {
            it.copy(isSelectingUnassignedDueDate = false)
        }
    }

    fun dismissCreation() {
        if (_uiState.value.isCreating) return
        _uiState.update {
            it.copy(
                pendingCandidate = null,
                isSelectingUnassignedDueDate = false,
                isConfirmingUnassigned = false,
                creationFailed = false
            )
        }
    }

    fun confirmCreation() {
        val state = _uiState.value
        val from = state.from ?: return
        if (
            state.isCreating ||
            (!state.isConfirmingUnassigned && state.pendingCandidate == null)
        ) {
            return
        }

        val driverId = state.pendingCandidate?.driverId
        creationTask?.cancel()
        creationTask = viewModelScope.launch {
            _uiState.update {
                it.copy(isCreating = true, creationFailed = false)
            }
            val result = jobRepository.createJob(
                NewJobRequest(
                    from = from,
                    to = state.to,
                    dueDate = state.dueDate,
                    note = state.note.trim().ifBlank { null },
                    assignedDriverId = driverId
                )
            )
            currentCoroutineContext().ensureActive()

            _uiState.update {
                when (result) {
                    is JobCreationResult.Success -> it.copy(
                        isCreating = false,
                        pendingCandidate = null,
                        isConfirmingUnassigned = false,
                        creationFailed = false,
                        createdJob = result.job
                    )

                    else -> it.copy(
                        isCreating = false,
                        creationFailed = true
                    )
                }
            }
        }
    }

    private fun loadCandidates() {
        candidatesTask?.cancel()
        val state = _uiState.value
        val from = state.from
        if (from == null) {
            _uiState.update {
                it.copy(
                    candidates = emptyList(),
                    isLoadingCandidates = false,
                    candidatesFailed = false,
                    pendingCandidate = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                candidates = emptyList(),
                isLoadingCandidates = true,
                candidatesFailed = false,
                pendingCandidate = null
            )
        }

        candidatesTask = viewModelScope.launch {
            val result = jobRepository.getJobCandidates(
                from = from,
                to = state.to,
                dueDate = state.dueDate
            )
            currentCoroutineContext().ensureActive()

            _uiState.update {
                when (result) {
                    is JobCandidatesResult.Success -> it.copy(
                        candidates = result.candidates,
                        isLoadingCandidates = false,
                        candidatesFailed = false
                    )

                    else -> it.copy(
                        candidates = emptyList(),
                        isLoadingCandidates = false,
                        candidatesFailed = true
                    )
                }
            }
        }
    }

    private fun loadDrivers() {
        driversTask?.cancel()
        driversTask = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingDrivers = true, driversFailed = false)
            }
            val result = roleRepository.getAvailability()
            currentCoroutineContext().ensureActive()

            _uiState.update {
                when (result) {
                    is RoleAvailabilityResult.Success -> it.copy(
                        allDrivers = result.availability.assignedRoles
                            .asSequence()
                            .distinctBy { role -> role.driverId }
                            .map { role ->
                                JobCandidate(
                                    driverId = role.driverId,
                                    driverName = role.name
                                        ?.takeIf(String::isNotBlank)
                                        ?: role.driverId,
                                    rank = 0,
                                    summary = null
                                )
                            }
                            .sortedBy { driver -> driver.driverName.lowercase() }
                            .toList(),
                        isLoadingDrivers = false,
                        driversFailed = false
                    )

                    else -> it.copy(
                        allDrivers = emptyList(),
                        isLoadingDrivers = false,
                        driversFailed = true
                    )
                }
            }
        }
    }

    private fun updateRouteAndCamera() {
        routeTask?.cancel()
        val from = _uiState.value.from?.toRoutePoint()
        val to = _uiState.value.to?.toRoutePoint()

        when {
            from == null -> _uiState.update {
                it.copy(
                    route = null,
                    isLoadingRoute = false,
                    routeFailed = false,
                    cameraFocusPoints = emptyList()
                )
            }

            to == null -> _uiState.update {
                it.copy(
                    route = null,
                    isLoadingRoute = false,
                    routeFailed = false,
                    cameraFocusPoints = listOf(from),
                    cameraFocusRequestId = it.cameraFocusRequestId + 1
                )
            }

            else -> {
                _uiState.update {
                    it.copy(
                        route = null,
                        isLoadingRoute = true,
                        routeFailed = false,
                        cameraFocusPoints = listOf(from, to),
                        cameraFocusRequestId = it.cameraFocusRequestId + 1
                    )
                }
                routeTask = viewModelScope.launch {
                    val result = geoServiceRepository.requestRoute(from, to)
                    currentCoroutineContext().ensureActive()
                    _uiState.update {
                        when (result) {
                            is RouteResult.Success -> it.copy(
                                route = result.route,
                                isLoadingRoute = false,
                                routeFailed = false,
                                cameraFocusPoints = result.route.points,
                                cameraFocusRequestId =
                                    it.cameraFocusRequestId + 1
                            )

                            else -> it.copy(
                                route = null,
                                isLoadingRoute = false,
                                routeFailed = true,
                                cameraFocusPoints = listOf(from, to),
                                cameraFocusRequestId =
                                    it.cameraFocusRequestId + 1
                            )
                        }
                    }
                }
            }
        }
    }

    private fun JobCoordinates.toRoutePoint(): RoutePoint? = RoutePoint(
        latitude = latitude,
        longitude = longitude
    ).takeIf(RoutePoint::isValid)

    private fun newState(): NewJobUiState = NewJobUiState(
        dueDate = now().toString()
    )

    private fun cancelTasks() {
        addressSearchTask?.cancel()
        candidatesTask?.cancel()
        driversTask?.cancel()
        routeTask?.cancel()
        creationTask?.cancel()
    }
}
