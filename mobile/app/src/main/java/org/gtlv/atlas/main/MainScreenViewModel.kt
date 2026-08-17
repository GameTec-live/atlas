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
import org.gtlv.core.geoservice.ResolveAddressResult
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobsResult

class MainScreenViewModel(
    private val jobRepository: JobRepository,
    private val geoServiceRepository:
    GeoServiceRepository
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

    fun loadJobsForUser(
        userId: String
    ) {
        if (activeUserId == userId) {
            return
        }

        cancelAllTasks()

        activeUserId = userId
        _uiState.value = MainScreenUiState()

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
        _uiState.update {
            it.copy(
                isLoading = false,
                currentJob = result.currentJob,
                queuedJobs = result.queuedJobs,
                isJobListExpanded =
                    it.isJobListExpanded &&
                            result.queuedJobs.isNotEmpty(),
                hasError = false,
                isStartingNextJob = false,
                startNextJobFailed = false,
                isCancellingCurrentJob = false,
                cancelCurrentJobFailed = false
            )
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
        val currentJob = state.currentJob
            ?: return

        if (
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
                    AddressSearchUiState(
                        query =
                            currentJob.toAddress
                                .orEmpty()
                    )
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

    fun clearJobs() {
        activeUserId = null

        cancelAllTasks()

        _uiState.value = MainScreenUiState(
            isLoading = false
        )
    }

    private fun cancelAllTasks() {
        refreshJob?.cancel()
        refreshJob = null

        jobActionTask?.cancel()
        jobActionTask = null

        addressSearchTask?.cancel()
        addressSearchTask = null

        locationUpdateTask?.cancel()
        locationUpdateTask = null
    }
}