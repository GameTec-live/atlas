package org.gtlv.atlas.main

import org.gtlv.core.job.Job

data class MainScreenUiState(
    val isLoading: Boolean = true,
    val currentJob: Job? = null,
    val queuedJobs: List<Job> = emptyList(),
    val isJobListExpanded: Boolean = false,
    val hasError: Boolean = false,
    val isStartingNextJob: Boolean = false,
    val startNextJobFailed: Boolean = false,
    val isCancellingCurrentJob: Boolean = false,
    val cancelCurrentJobFailed: Boolean = false
)