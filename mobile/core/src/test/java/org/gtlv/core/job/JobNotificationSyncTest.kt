package org.gtlv.core.job

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobNotificationSyncTest {

    @Test
    fun assignedResolutionDoesNotResolveUnassignedFollowUp() {
        val assigned = AssignedJobNotification(
            jobId = JOB_ID,
            from = "Origin",
            to = "Destination"
        )
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

    private companion object {
        const val JOB_ID = "5a4df014-28ff-4f84-82e6-2538fad83a9c"
    }
}
