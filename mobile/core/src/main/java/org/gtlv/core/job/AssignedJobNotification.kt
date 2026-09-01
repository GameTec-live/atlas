package org.gtlv.core.job

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface JobNotification {
    val jobId: String
    val from: String
    val to: String?
    val note: String?
}

data class AssignedJobNotification(
    override val jobId: String,
    override val from: String,
    override val to: String?,
    override val note: String? = null
) : JobNotification

data class UnassignedJobNotification(
    override val jobId: String,
    override val from: String,
    override val to: String?,
    override val note: String? = null
) : JobNotification

enum class JobNotificationType {
    ASSIGNED,
    UNASSIGNED
}

val JobNotification.type: JobNotificationType
    get() = when (this) {
        is AssignedJobNotification -> JobNotificationType.ASSIGNED
        is UnassignedJobNotification -> JobNotificationType.UNASSIGNED
    }

fun JobNotification.hasSameIdentity(other: JobNotification): Boolean =
    jobId == other.jobId && type == other.type

data class JobNotificationResolution(
    val jobId: String,
    val type: JobNotificationType
)

fun JobNotification.matches(resolution: JobNotificationResolution): Boolean =
    jobId == resolution.jobId && type == resolution.type

/** Keeps unresolved notifications available to collectors that start late. */
class JobNotificationInbox {
    private val _notifications =
        MutableStateFlow<List<JobNotification>>(emptyList())

    val notifications: StateFlow<List<JobNotification>> =
        _notifications.asStateFlow()

    fun add(notification: JobNotification) {
        _notifications.update { notifications ->
            if (
                notifications.any { existing ->
                    existing.hasSameIdentity(notification)
                }
            ) {
                notifications
            } else {
                notifications + notification
            }
        }
    }

    fun resolve(resolution: JobNotificationResolution) {
        _notifications.update { notifications ->
            notifications.filterNot { notification ->
                notification.matches(resolution)
            }
        }
    }

    fun clear() {
        _notifications.value = emptyList()
    }
}

/** Synchronizes job notification presentation across phone and car surfaces. */
interface JobNotificationSyncProvider {
    val jobNotifications: StateFlow<List<JobNotification>>
    val resolvedJobNotifications: Flow<JobNotificationResolution>

    fun resolveJobNotification(notification: JobNotification)
}
