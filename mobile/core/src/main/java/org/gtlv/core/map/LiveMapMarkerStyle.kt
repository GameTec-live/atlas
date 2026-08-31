package org.gtlv.core.map

import org.gtlv.core.telemetry.TelemetryVehicleState

/** Status colors shared by every map surface that renders live drivers. */
val TelemetryVehicleState.liveMapMarkerColor: Int
    get() = when (this) {
        TelemetryVehicleState.FREE -> 0xFF10B981.toInt()
        TelemetryVehicleState.ON_THE_WAY -> 0xFF3B82F6.toInt()
        TelemetryVehicleState.OCCUPIED -> 0xFFF59E0B.toInt()
        TelemetryVehicleState.AWAY -> 0xFF94A3B8.toInt()
    }
