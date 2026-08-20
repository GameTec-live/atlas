package org.gtlv.atlas.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.core.job.JobRepository

class JobNotificationViewModelFactory(
    private val jobRepository: JobRepository,
    private val webSocket:
    JobNotificationWebSocket
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
                webSocket = webSocket
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}