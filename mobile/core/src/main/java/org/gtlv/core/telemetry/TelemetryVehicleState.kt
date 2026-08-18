package org.gtlv.core.telemetry

enum class TelemetryVehicleState(val wireValue: String) {
    FREE("free"),
    ON_THE_WAY("onTheWay"),
    OCCUPIED("occupied"),
    AWAY("away")
}