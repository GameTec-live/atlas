package org.gtlv.atlas.unassigned

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.core.job.JobRepository

class UnassignedJobsViewModelFactory(
    private val jobRepository: JobRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                UnassignedJobsViewModel::class.java
            )
        ) {
            return UnassignedJobsViewModel(
                jobRepository = jobRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}
