package org.gtlv.atlas.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobsResult
import org.gtlv.core.job.StartJobResult
import kotlin.coroutines.coroutineContext

class MainScreenViewModel(
    private val jobRepository: JobRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(MainScreenUiState())

    val uiState: StateFlow<MainScreenUiState> =
        _uiState.asStateFlow()

    private var activeUserId: String? = null
    private var refreshJob: Job? = null
    private var startNextJobTask: Job? = null

    fun loadJobsForUser(userId: String) {
        if (activeUserId == userId) {
            return
        }

        refreshJob?.cancel()
        startNextJobTask?.cancel()

        activeUserId = userId
        _uiState.value = MainScreenUiState()

        refresh()
    }

    fun refresh() {
        if (
            activeUserId == null ||
            _uiState.value.isStartingNextJob
        ) {
            return
        }

        refreshJob?.cancel()

        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasError = false,
                    startNextJobFailed = false
                )
            }

            val result = jobRepository.getJobs()

            coroutineContext.ensureActive()

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
            state.isLoading ||
            state.isStartingNextJob
        ) {
            return
        }

        val nextJob = state.queuedJobs.firstOrNull()
            ?: return

        refreshJob?.cancel()
        startNextJobTask?.cancel()

        startNextJobTask = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStartingNextJob = true,
                    startNextJobFailed = false
                )
            }

            when (
                jobRepository.startJob(
                    jobId = nextJob.id
                )
            ) {
                StartJobResult.Success -> {
                    reloadJobsAfterStarting()
                }

                else -> {
                    coroutineContext.ensureActive()

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

    private suspend fun reloadJobsAfterStarting() {
        val result = jobRepository.getJobs()

        coroutineContext.ensureActive()

        when (result) {
            is JobsResult.Success -> {
                applyJobs(result)
            }

            else -> {
                _uiState.update {
                    it.copy(
                        isStartingNextJob = false,
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
                startNextJobFailed = false
            )
        }
    }

    fun clearJobs() {
        activeUserId = null

        refreshJob?.cancel()
        refreshJob = null

        startNextJobTask?.cancel()
        startNextJobTask = null

        _uiState.value = MainScreenUiState(
            isLoading = false
        )
    }

    fun toggleJobList() {
        _uiState.update {
            it.copy(
                isJobListExpanded =
                    !it.isJobListExpanded
            )
        }
    }
}