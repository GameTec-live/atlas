package org.gtlv.atlas.notification

import org.gtlv.core.job.AssignedJobNotification
import org.gtlv.core.job.UnassignedJobNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobNotificationQueueTest {

    @Test
    fun assignedAndUnassignedEventsForSameJob_areDistinct() {
        val assigned = AssignedJobNotification(
            jobId = "job-1",
            from = "Lest",
            to = "Linz"
        )
        val unassigned = UnassignedJobNotification(
            jobId = "job-1",
            from = "Lest",
            to = "Linz"
        )

        val assignedState = JobNotificationUiState()
            .withEnqueuedNotification(
                notification = assigned,
                newExpirationTime = { 10L }
            )

        val queuedState = assignedState
            .withEnqueuedNotification(
                notification = unassigned,
                newExpirationTime = { 20L }
            )

        assertFalse(
            assigned.hasSameQueueIdentity(unassigned)
        )
        assertTrue(
            queuedState.foregroundNotifications ==
                    listOf(assigned, unassigned)
        )
        assertTrue(
            queuedState
                .currentNotificationExpiresAtElapsedRealtime ==
                    10L
        )
    }

    @Test
    fun repeatedEventForSameJob_isStillDeduplicated() {
        val first = UnassignedJobNotification(
            jobId = "job-1",
            from = "Lest",
            to = "Linz"
        )
        val repeated = first.copy(note = "Updated note")

        val initialState = JobNotificationUiState()
            .withEnqueuedNotification(
                notification = first,
                newExpirationTime = { 10L }
            )
        val repeatedState = initialState
            .withEnqueuedNotification(
                notification = repeated,
                newExpirationTime = { 20L }
            )

        assertTrue(
            first.hasSameQueueIdentity(repeated)
        )
        assertTrue(
            repeatedState === initialState
        )
    }
}
