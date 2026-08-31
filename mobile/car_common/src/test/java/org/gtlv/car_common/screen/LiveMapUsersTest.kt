package org.gtlv.car_common.screen

import org.gtlv.core.telemetry.LiveMapUser
import org.gtlv.core.telemetry.TelemetryVehicleState
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveMapUsersTest {
    @Test
    fun `all connected drivers except the current driver remain visible`() {
        val currentDriver = liveMapUser("current")
        val availableDriver = liveMapUser("available")
        val busyDriver = liveMapUser(
            userId = "busy",
            state = TelemetryVehicleState.OCCUPIED,
        )

        val visibleDrivers = listOf(
            currentDriver,
            availableDriver,
            busyDriver,
        ).excludingUser(currentDriver.userId)

        assertEquals(
            listOf(availableDriver, busyDriver),
            visibleDrivers,
        )
    }

    @Test
    fun `drivers absent from the latest snapshot are not visible`() {
        val connectedDriver = liveMapUser("connected")
        val disconnectedDriver = liveMapUser("disconnected")
        val previousSnapshot = listOf(connectedDriver, disconnectedDriver)
        val latestSnapshot = previousSnapshot.filterNot { driver ->
            driver.userId == disconnectedDriver.userId
        }

        val visibleDrivers = latestSnapshot
            .excludingUser("current")

        assertEquals(listOf(connectedDriver), visibleDrivers)
    }

    private fun liveMapUser(
        userId: String,
        state: TelemetryVehicleState = TelemetryVehicleState.FREE,
    ): LiveMapUser = LiveMapUser(
        userId = userId,
        userName = userId,
        latitude = 48.5,
        longitude = 14.58,
        state = state,
    )
}
