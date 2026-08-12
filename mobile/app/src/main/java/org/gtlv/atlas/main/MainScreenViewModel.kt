package org.gtlv.atlas.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobsResult

class MainScreenViewModel(
    private val jobRepository: JobRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(MainScreenUiState())

    val uiState: StateFlow<MainScreenUiState> =
        _uiState.asStateFlow()

    private var activeUserId: String? = null
    private var refreshJob: Job? = null
    private var sessionGeneration: Long = 0L

    fun loadJobsForUser(userId: String) {
        if (activeUserId == userId) {
            return
        }

        refreshJob?.cancel()
        sessionGeneration += 1
        activeUserId = userId

        _uiState.value = MainScreenUiState(
            isLoading = true
        )

        refresh()
    }

    fun refresh() {
        val requestedUserId = activeUserId ?: return
        val requestedGeneration = sessionGeneration

        refreshJob?.cancel()

        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasError = false
                )
            }

            val result = jobRepository.getJobs()

            // Ignore results from a previous authenticated session.
            if (
                activeUserId != requestedUserId ||
                sessionGeneration != requestedGeneration
            ) {
                return@launch
            }

            when (result) {
                is JobsResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentJob = result.currentJob,
                            queuedJobs = result.queuedJobs,
                            isJobListExpanded =
                                it.isJobListExpanded &&
                                        result.queuedJobs.isNotEmpty(),
                            hasError = false
                        )
                    }
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

    fun clearJobs() {
        sessionGeneration += 1
        activeUserId = null

        refreshJob?.cancel()
        refreshJob = null

        _uiState.value = MainScreenUiState(
            isLoading = false
        )
    }

    fun toggleJobList() {
        _uiState.update { state ->
            if (state.queuedJobs.isEmpty()) {
                state.copy(
                    isJobListExpanded = false
                )
            } else {
                state.copy(
                    isJobListExpanded =
                        !state.isJobListExpanded
                )
            }
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }
}