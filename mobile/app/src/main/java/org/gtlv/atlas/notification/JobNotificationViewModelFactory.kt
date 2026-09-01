package org.gtlv.atlas.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.core.job.JobRepository

class JobNotificationViewModelFactory(
    private val jobRepository: JobRepository,
    private val notificationSync: JobNotificationSync
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                JobNotificationViewModel::class.java
            )
        ) {
            return JobNotificationViewModel(
                jobRepository = jobRepository,
                notificationSync = notificationSync
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}
