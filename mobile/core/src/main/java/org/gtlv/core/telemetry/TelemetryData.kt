package org.gtlv.core.telemetry

import java.util.UUID

/** Matches the server's telemetry `update` message. */
data class TelemetryData(
    val latitude: Double,
    val longitude: Double,
    val state: TelemetryVehicleState,
    val vehicleId: String? = null,
    val fuelLevel: Double? = null,
    val odometer: Double? = null,
    val type: String = TYPE
) {
    init {
        require(type == TYPE) { "type must be '$TYPE'" }
        require(latitude in LATITUDE_RANGE) {
            "latitude must be between -90 and 90"
        }
        require(longitude in LONGITUDE_RANGE) {
            "longitude must be between -180 and 180"
        }
        require(fuelLevel == null || fuelLevel in FUEL_LEVEL_RANGE) {
            "fuelLevel must be between 0 and 100"
        }
        require(odometer == null || odometer >= 0.0) {
            "odometer must not be negative"
        }
        require(vehicleId == null || vehicleId.isTelemetryUuid()) {
            "vehicleId must be a UUID"
        }
    }

    companion object {
        const val TYPE = "update"
        val LATITUDE_RANGE = -90.0..90.0
        val LONGITUDE_RANGE = -180.0..180.0
        val FUEL_LEVEL_RANGE = 0.0..100.0
    }
}

internal fun String.isTelemetryUuid(): Boolean {
    return runCatching {
        UUID.fromString(this).toString().equals(this, ignoreCase = true)
    }.getOrDefault(false)
}
