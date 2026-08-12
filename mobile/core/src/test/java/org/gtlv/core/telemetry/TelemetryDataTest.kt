package org.gtlv.core.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TelemetryDataTest {
    @Test
    fun `valid telemetry matches server values`() {
        val telemetry = TelemetryData(
            latitude = 48.511,
            longitude = 14.504,
            state = TelemetryVehicleState.ON_THE_WAY,
            vehicleId = BluetoothVehicleId.fromMacAddress("AA:BB:CC:DD:EE:FF"),
            fuelLevel = 75.5,
            odometer = 123_456.7
        )

        assertEquals("update", telemetry.type)
        assertEquals("onTheWay", telemetry.state.wireValue)
        println(telemetry.vehicleId)
    }

    @Test
    fun `coordinates are validated`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryData(
                latitude = 90.1,
                longitude = 14.504,
                state = TelemetryVehicleState.FREE
            )
        }
    }

    @Test
    fun `optional server values are validated`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryData(
                latitude = 48.511,
                longitude = 14.504,
                state = TelemetryVehicleState.OCCUPIED,
                fuelLevel = 101.0
            )
        }
    }
}
