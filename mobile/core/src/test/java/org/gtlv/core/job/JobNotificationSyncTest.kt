package org.gtlv.core.job

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobNotificationSyncTest {

    @Test
    fun notificationIsDeliveredToCollectorThatStartsAfterAdd() = runBlocking {
        val inbox = JobNotificationInbox()
        val notification = assignedNotification()

        inbox.add(notification)

        assertEquals(listOf(notification), inbox.notifications.first())
    }

    @Test
    fun assignedResolutionDoesNotResolveUnassignedFollowUp() {
        val assigned = assignedNotification()
        val unassigned = UnassignedJobNotification(
            jobId = JOB_ID,
            from = "Origin",
            to = "Destination"
        )
        val resolution = JobNotificationResolution(
            jobId = JOB_ID,
            type = JobNotificationType.ASSIGNED
        )

        assertTrue(assigned.matches(resolution))
        assertFalse(unassigned.matches(resolution))
        assertFalse(assigned.hasSameIdentity(unassigned))
    }

    @Test
    fun assignedResolutionKeepsUnassignedFollowUpInInbox() {
        val inbox = JobNotificationInbox()
        val assigned = assignedNotification()
        val unassigned = UnassignedJobNotification(
            jobId = JOB_ID,
            from = "Origin",
            to = "Destination"
        )
        inbox.add(assigned)
        inbox.add(unassigned)

        inbox.resolve(
            JobNotificationResolution(
                jobId = JOB_ID,
                type = JobNotificationType.ASSIGNED
            )
        )

        assertEquals(listOf(unassigned), inbox.notifications.value)
    }

    private fun assignedNotification() = AssignedJobNotification(
        jobId = JOB_ID,
        from = "Origin",
        to = "Destination"
    )

    private companion object {
        const val JOB_ID = "5a4df014-28ff-4f84-82e6-2538fad83a9c"
    }
}
