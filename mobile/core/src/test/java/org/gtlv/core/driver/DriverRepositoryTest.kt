package org.gtlv.core.driver

import org.junit.Assert.assertEquals
import org.junit.Test

class DriverRepositoryTest {
    @Test
    fun parsesAllUsersRegardlessOfSignOnStatus() {
        assertEquals(
            DriversResult.Success(listOf(Driver("2", "Anna"), Driver("1", "Zoe"))),
            parseDrivers("""[
                {"driverId":"1","name":"Zoe","signedOn":true},
                {"driverId":"2","name":"Anna","signedOn":false}
            ]""")
        )
    }

    @Test
    fun rejectsMalformedDirectoryResponses() {
        assertEquals(DriversResult.Failed, parseDrivers("{}"))
        assertEquals(DriversResult.Failed, parseDrivers("""[{"name":"Anna"}]"""))
        assertEquals(DriversResult.Failed, parseDrivers("""[{"driverId":""}]"""))
    }
}
