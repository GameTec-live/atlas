package org.gtlv.core.telemetry

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BluetoothVehicleIdTest {
    @Test
    fun `same MAC address always creates same UUID`() {
        val first = BluetoothVehicleId.fromMacAddress(
            "AA:BB:CC:DD:EE:FF"
        )
        val second = BluetoothVehicleId.fromMacAddress(
            "aa-bb-cc-dd-ee-ff"
        )

        assertEquals(first, second)
        assertEquals(5, UUID.fromString(first).version())
    }

    @Test
    fun `different MAC addresses create different UUIDs`() {
        val first = BluetoothVehicleId.fromMacAddress(
            "AA:BB:CC:DD:EE:FF"
        )
        val second = BluetoothVehicleId.fromMacAddress(
            "AA:BB:CC:DD:EE:00"
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `invalid MAC address is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothVehicleId.fromMacAddress("not-a-mac")
        }
    }
}
