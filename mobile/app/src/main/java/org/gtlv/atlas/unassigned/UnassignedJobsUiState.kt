package org.gtlv.atlas.unassigned

import org.gtlv.core.job.Job

data class UnassignedJobsUiState(
    val isLoading: Boolean = true,
    val jobs: List<Job> = emptyList(),
    val hasError: Boolean = false,
    val pendingDeletion: Job? = null,
    val deletingJobId: String? = null,
    val deleteFailed: Boolean = false
) {
    val loadedJobCount: Int?
        get() = when {
            jobs.isNotEmpty() -> jobs.size
            !isLoading && !hasError -> 0
            else -> null
        }

    val isDeleting: Boolean
        get() = deletingJobId != null
}
