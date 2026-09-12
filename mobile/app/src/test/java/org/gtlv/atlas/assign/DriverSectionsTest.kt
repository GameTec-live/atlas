package org.gtlv.atlas.assign

import org.gtlv.core.job.JobCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class DriverSectionsTest {
    @Test
    fun offlineListExcludesCandidatesAndOnlineUsersAndRemovesDuplicates() {
        val candidate = driver("candidate", "Candidate")
        val online = driver("online", "Online")
        val offline = driver("offline", "Zoe")
        val anotherOffline = driver("another", "Anna")
        assertEquals(
            listOf(anotherOffline, offline),
            offlineDrivers(
                listOf(candidate, offline, online, anotherOffline, offline),
                listOf(online),
                listOf(candidate)
            )
        )
    }

    private fun driver(id: String, name: String) = JobCandidate(id, name, 0, null)
}
