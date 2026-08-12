package org.gtlv.core.telemetry

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/** Creates the same UUIDv5 for the same Bluetooth MAC address. */
object BluetoothVehicleId {
    private val namespace = UUID.fromString(
        "7f1d9d52-45a4-4d31-8a1f-a74ac64f3d2e"
    )
    fun fromMacAddress(macAddress: String): String {
        val normalizedMacAddress = normalize(macAddress)
        val digest = MessageDigest.getInstance("SHA-1")

        digest.update(namespace.toByteArray())

        val hash = digest.digest(
            normalizedMacAddress.toByteArray(StandardCharsets.UTF_8)
        )

        // RFC 9562 UUIDv5 version and variant bits.
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()

        val bytes = ByteBuffer.wrap(hash.copyOf(16))

        return UUID(
            bytes.long,
            bytes.long
        ).toString()
    }

    private fun normalize(macAddress: String): String {
        val normalized = macAddress
            .trim()
            .replace(":", "")
            .replace("-", "")
            .lowercase()

        require(MAC_ADDRESS_REGEX.matches(normalized)) {
            "Invalid Bluetooth MAC address"
        }

        return normalized
    }

    private fun UUID.toByteArray(): ByteArray {
        return ByteBuffer.allocate(16)
            .putLong(mostSignificantBits)
            .putLong(leastSignificantBits)
            .array()
    }

    private val MAC_ADDRESS_REGEX = Regex("^[0-9a-f]{12}$")
}
