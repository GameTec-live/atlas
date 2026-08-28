package org.gtlv.atlas.unassigned

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
import org.gtlv.core.job.UnassignedJobsResult

class UnassignedJobsViewModel(
    private val jobRepository: JobRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UnassignedJobsUiState()
    )

    val uiState: StateFlow<UnassignedJobsUiState> =
        _uiState.asStateFlow()

    private var loadTask: Job? = null
    private var deletionTask: Job? = null

    fun clear() {
        loadTask?.cancel()
        deletionTask?.cancel()
        loadTask = null
        deletionTask = null
        _uiState.value = UnassignedJobsUiState()
    }

    fun refresh() {
        if (_uiState.value.isDeleting) {
            return
        }

        loadTask?.cancel()

        loadTask = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasError = false
                )
            }

            val result =
                jobRepository.getUnassignedJobs()

            currentCoroutineContext().ensureActive()

            when (result) {
                is UnassignedJobsResult.Success -> {
                    _uiState.value =
                        UnassignedJobsUiState(
                            isLoading = false,
                            jobs = result.jobs
                        )
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

    fun removeJob(jobId: String) {
        _uiState.update {
            it.copy(
                jobs = it.jobs.filterNot { job ->
                    job.id == jobId
                }
            )
        }
    }

    fun requestDeletion(
        job: org.gtlv.core.job.Job
    ) {
        if (_uiState.value.isDeleting) {
            return
        }

        _uiState.update {
            it.copy(
                pendingDeletion = job,
                deleteFailed = false
            )
        }
    }

    fun dismissDeletion() {
        if (_uiState.value.isDeleting) {
            return
        }

        _uiState.update {
            it.copy(
                pendingDeletion = null,
                deleteFailed = false
            )
        }
    }

    fun confirmDeletion() {
        val job = _uiState.value.pendingDeletion
            ?: return

        if (_uiState.value.isDeleting) {
            return
        }

        deletionTask?.cancel()
        deletionTask = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    deletingJobId = job.id,
                    deleteFailed = false
                )
            }

            val result = jobRepository
                .deleteUnassignedJob(job.id)

            currentCoroutineContext().ensureActive()

            when (result) {
                JobActionResult.Success -> {
                    _uiState.update {
                        it.copy(
                            jobs = it.jobs.filterNot {
                                    listedJob ->
                                listedJob.id == job.id
                            },
                            pendingDeletion = null,
                            deletingJobId = null,
                            deleteFailed = false
                        )
                    }
                }

                else -> {
                    _uiState.update {
                        it.copy(
                            deletingJobId = null,
                            deleteFailed = true
                        )
                    }
                }
            }
        }
    }
}
