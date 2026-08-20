package org.gtlv.core.telemetry

data class LiveMapUser(
    val userId: String,
    val userName: String,
    val latitude: Double,
    val longitude: Double,
    val state: TelemetryVehicleState
)