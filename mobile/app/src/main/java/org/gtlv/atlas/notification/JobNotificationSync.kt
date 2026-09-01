package org.gtlv.atlas.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.gtlv.core.job.JobNotification
import org.gtlv.core.job.JobNotificationResolution
import org.gtlv.core.job.JobNotificationSyncProvider
import org.gtlv.core.job.type

class JobNotificationSync(
    private val webSocket: JobNotificationWebSocket,
    scope: CoroutineScope
) : JobNotificationSyncProvider {

    private val _events = MutableSharedFlow<JobNotificationEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY
    )
    val events: SharedFlow<JobNotificationEvent> = _events.asSharedFlow()

    private val _jobNotifications = MutableSharedFlow<JobNotification>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY
    )
    override val jobNotifications: SharedFlow<JobNotification> =
        _jobNotifications.asSharedFlow()

    private val _resolvedJobNotifications =
        MutableSharedFlow<JobNotificationResolution>(
            extraBufferCapacity = EVENT_BUFFER_CAPACITY
        )
    override val resolvedJobNotifications:
            SharedFlow<JobNotificationResolution> =
        _resolvedJobNotifications.asSharedFlow()

    init {
        scope.launch {
            webSocket.events.collect { event ->
                _events.emit(event)
                if (event is JobNotificationEvent.Received) {
                    _jobNotifications.emit(event.notification)
                }
            }
        }
    }

    override fun resolveJobNotification(notification: JobNotification) {
        dismissSystemNotification(notification.jobId)
        _resolvedJobNotifications.tryEmit(
            JobNotificationResolution(
                jobId = notification.jobId,
                type = notification.type
            )
        )
    }

    fun dismissSystemNotification(jobId: String) {
        webSocket.dismissSystemNotification(jobId)
    }

    fun clearSystemNotifications() {
        webSocket.clearSystemNotifications()
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 32
    }
}
