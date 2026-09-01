package org.gtlv.core.job

import kotlinx.coroutines.flow.Flow

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

/** Synchronizes job notification presentation across phone and car surfaces. */
interface JobNotificationSyncProvider {
    val jobNotifications: Flow<JobNotification>
    val resolvedJobNotifications: Flow<JobNotificationResolution>

    fun resolveJobNotification(notification: JobNotification)
}
