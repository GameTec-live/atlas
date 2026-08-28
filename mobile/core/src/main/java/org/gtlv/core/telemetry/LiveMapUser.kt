package org.gtlv.core.telemetry

import kotlinx.coroutines.flow.StateFlow

data class LiveMapUser(
    val userId: String,
    val userName: String,
    val latitude: Double,
    val longitude: Double,
    val state: TelemetryVehicleState
)

/** Makes the live users received over telemetry available across UI surfaces. */
interface LiveMapUsersProvider {
    val liveMapUsers: StateFlow<Map<String, LiveMapUser>>
}