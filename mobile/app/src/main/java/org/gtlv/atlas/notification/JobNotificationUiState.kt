package org.gtlv.atlas.notification

import org.gtlv.core.job.AssignedJobNotification

internal const val JOB_NOTIFICATION_DURATION_MILLIS =
    10_000L

data class JobNotificationUiState(
    val foregroundNotifications:
    List<AssignedJobNotification> =
        emptyList(),
    val currentNotificationExpiresAtElapsedRealtime:
    Long? = null,
    val declineConfirmation:
    AssignedJobNotification? = null,
    val decliningJobId: String? = null,
    val declineFailed: Boolean = false
) {
    val currentNotification:
            AssignedJobNotification?
        get() = foregroundNotifications
            .firstOrNull()

    val isDeclining: Boolean
        get() = decliningJobId != null
}