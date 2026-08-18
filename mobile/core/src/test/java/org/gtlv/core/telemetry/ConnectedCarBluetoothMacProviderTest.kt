package org.gtlv.core.telemetry

import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectedCarBluetoothMacProviderTest {
    @Test
    fun `device connected to both car profiles is preferred`() {
        val result = selectConnectedCarMacAddress(
            mapOf(
                BluetoothProfile.HEADSET to setOf("AA:BB:CC:DD:EE:FF"),
                BluetoothProfile.A2DP to setOf(
                    "AA:BB:CC:DD:EE:FF",
                    "11:22:33:44:55:66"
                )
            )
        )

        assertEquals("AA:BB:CC:DD:EE:FF", result)
    }

    @Test
    fun `single connected profile device is accepted`() {
        val result = selectConnectedCarMacAddress(
            mapOf(
                BluetoothProfile.HEADSET to setOf("AA:BB:CC:DD:EE:FF"),
                BluetoothProfile.A2DP to emptySet()
            )
        )

        assertEquals("AA:BB:CC:DD:EE:FF", result)
    }

    @Test
    fun `ambiguous devices do not produce vehicle identity`() {
        val result = selectConnectedCarMacAddress(
            mapOf(
                BluetoothProfile.HEADSET to setOf("AA:BB:CC:DD:EE:FF"),
                BluetoothProfile.A2DP to setOf("11:22:33:44:55:66")
            )
        )

        assertNull(result)
    }
}
