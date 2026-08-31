package org.gtlv.atlas.notification

import org.gtlv.core.job.AssignedJobNotification
import org.gtlv.core.job.JobNotification

internal const val JOB_NOTIFICATION_DURATION_MILLIS =
    10_000L

data class JobNotificationUiState(
    val foregroundNotifications:
    List<JobNotification> =
        emptyList(),
    val currentNotificationExpiresAtElapsedRealtime:
    Long? = null,
    val declineConfirmation:
    AssignedJobNotification? = null,
    val decliningJobId: String? = null,
    val declineFailed: Boolean = false,
    val assignmentJobId: String? = null
) {
    val currentNotification:
            JobNotification?
        get() = foregroundNotifications
            .firstOrNull()

    val isDeclining: Boolean
        get() = decliningJobId != null
}
