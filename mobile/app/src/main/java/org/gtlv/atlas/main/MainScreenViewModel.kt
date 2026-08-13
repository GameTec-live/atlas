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
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobsResult
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
    private var jobActionTask: Job? = null

    fun loadJobsForUser(userId: String) {
        if (activeUserId == userId) {
            return
        }

        refreshJob?.cancel()
        jobActionTask?.cancel()

        activeUserId = userId
        _uiState.value = MainScreenUiState()

        refresh()
    }

    fun refresh() {
        val state = _uiState.value

        if (
            activeUserId == null ||
            state.isStartingNextJob ||
            state.isCancellingCurrentJob
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
            state.queuedJobs.isEmpty() ||
            state.isLoading ||
            state.isStartingNextJob ||
            state.isCancellingCurrentJob
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

            when (
                jobRepository.startJob(
                    jobId = nextJob.id
                )
            ) {
                JobActionResult.Success -> {
                    reloadJobsAfterAction()
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

    fun cancelCurrentJob() {
        val state = _uiState.value

        if (
            activeUserId == null ||
            state.currentJob == null ||
            state.isLoading ||
            state.isStartingNextJob ||
            state.isCancellingCurrentJob
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

            when (
                jobRepository.cancelJob(
                    jobId = currentJobId
                )
            ) {
                JobActionResult.Success -> {
                    reloadJobsAfterAction()
                }

                else -> {
                    coroutineContext.ensureActive()

                    _uiState.update {
                        it.copy(
                            isCancellingCurrentJob = false,
                            cancelCurrentJobFailed = true
                        )
                    }
                }
            }
        }
    }

    private suspend fun reloadJobsAfterAction() {
        val result = jobRepository.getJobs()

        currentCoroutineContext().ensureActive()

        when (result) {
            is JobsResult.Success -> {
                applyJobs(result)
            }

            else -> {
                _uiState.update {
                    it.copy(
                        isStartingNextJob = false,
                        isCancellingCurrentJob = false,
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

    fun clearJobs() {
        activeUserId = null

        refreshJob?.cancel()
        refreshJob = null

        jobActionTask?.cancel()
        jobActionTask = null

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