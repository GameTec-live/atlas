package org.gtlv.core.map

import org.gtlv.core.telemetry.TelemetryVehicleState
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveMapMarkerStyleTest {
    @Test
    fun `status colors match the mobile live map palette`() {
        assertEquals(
            0xFF10B981.toInt(),
            TelemetryVehicleState.FREE.liveMapMarkerColor,
        )
        assertEquals(
            0xFF3B82F6.toInt(),
            TelemetryVehicleState.ON_THE_WAY.liveMapMarkerColor,
        )
        assertEquals(
            0xFFF59E0B.toInt(),
            TelemetryVehicleState.OCCUPIED.liveMapMarkerColor,
        )
        assertEquals(
            0xFF94A3B8.toInt(),
            TelemetryVehicleState.AWAY.liveMapMarkerColor,
        )
    }
}
