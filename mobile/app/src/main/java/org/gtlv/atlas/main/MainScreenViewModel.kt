package org.gtlv.atlas.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private var loadedUserId: String? = null


    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasError = false
                )
            }

            when (val result = jobRepository.getJobs()) {
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

    fun toggleJobList() {
        _uiState.update { state ->
            if (state.queuedJobs.isEmpty()) {
                state.copy(isJobListExpanded = false)
            } else {
                state.copy(
                    isJobListExpanded =
                        !state.isJobListExpanded
                )
            }
        }
    }

    fun loadJobsForUser(userId: String) {
        if (loadedUserId == userId) {
            return
        }

        loadedUserId = userId

        _uiState.value = MainScreenUiState(
            isLoading = true
        )

        refresh()
    }

    fun clearJobs() {
        loadedUserId = null
        _uiState.value = MainScreenUiState(
            isLoading = false
        )
    }
}