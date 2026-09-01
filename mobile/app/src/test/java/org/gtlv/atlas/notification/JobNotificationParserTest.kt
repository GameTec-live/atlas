package org.gtlv.atlas.notification

import org.gtlv.core.job.AssignedJobNotification
import org.gtlv.core.job.UnassignedJobNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobNotificationParserTest {

    @Test
    fun jobsChangedMessage_createsSilentRefreshSignal() {
        assertEquals(
            ParsedNotifyMessage.JobsChanged,
            parseNotifyMessage(
                """{"type":"jobs_changed"}"""
            )
        )
    }

    @Test
    fun assignedMessage_keepsExistingAssignedNotificationFlow() {
        val notification = parseJobNotification(
            """
            {
              "type": "assigned",
              "jobId": "e3603d62-882b-48a4-96d7-ca1fda4f2b41",
              "from": "Lest 110, 4212 Kefermarkt",
              "to": "Linzer Schloss, Schlossberg 1, 4020 Linz"
            }
            """.trimIndent()
        )

        assertTrue(
            notification is AssignedJobNotification
        )
        assertEquals(
            "e3603d62-882b-48a4-96d7-ca1fda4f2b41",
            notification?.jobId
        )
        assertEquals(
            "Lest 110, 4212 Kefermarkt",
            notification?.from
        )
        assertEquals(
            "Linzer Schloss, Schlossberg 1, 4020 Linz",
            notification?.to
        )
    }

    @Test
    fun unassignedMessage_createsDispatcherNotification() {
        val notification = parseJobNotification(
            """
            {
              "type": "unassigned",
              "jobId": "e3603d62-882b-48a4-96d7-ca1fda4f2b41",
              "from": "Lest",
              "to": "Linz",
              "note": "Wheelchair pickup"
            }
            """.trimIndent()
        )

        assertTrue(
            notification is UnassignedJobNotification
        )
        assertEquals(
            "Wheelchair pickup",
            notification?.note
        )
    }

    @Test
    fun missingOrUnknownType_isIgnored() {
        assertNull(
            parseJobNotification(
                """
                {
                  "jobId": "e3603d62-882b-48a4-96d7-ca1fda4f2b41",
                  "from": "Lest",
                  "to": "Linz"
                }
                """.trimIndent()
            )
        )

        assertNull(
            parseJobNotification(
                """
                {
                  "type": "cancelled",
                  "jobId": "e3603d62-882b-48a4-96d7-ca1fda4f2b41",
                  "from": "Lest",
                  "to": "Linz"
                }
                """.trimIndent()
            )
        )
    }

    @Test
    fun missingDestination_isAccepted() {
        val notification = parseJobNotification(
            """
            {
              "type": "assigned",
              "jobId": "e3603d62-882b-48a4-96d7-ca1fda4f2b41",
              "from": "Lest"
            }
            """.trimIndent()
        )

        assertTrue(notification is AssignedJobNotification)
        assertNull(notification?.to)
    }

    @Test
    fun missingRequiredFields_isIgnored() {
        assertNull(
            parseJobNotification(
                """
                {
                  "type": "assigned",
                  "jobId": "e3603d62-882b-48a4-96d7-ca1fda4f2b41"
                }
                """.trimIndent()
            )
        )
    }
}
