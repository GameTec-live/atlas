package org.gtlv.core.telemetry

import kotlinx.coroutines.flow.StateFlow

/** Temporary, user-visible diagnostics for projected car hardware data. */
interface TelemetryDiagnosticsProvider {
    val telemetryDiagnostics: StateFlow<TelemetryDiagnostics>
}

data class TelemetryDiagnostics(
    val carAppApiLevel: Int? = null,
    val hardware: TelemetryDiagnosticValue =
        TelemetryDiagnosticValue(TelemetryDiagnosticStatus.DISCONNECTED),
    val energyListener: TelemetryDiagnosticValue =
        TelemetryDiagnosticValue(TelemetryDiagnosticStatus.WAITING),
    val batteryPercent: TelemetryDiagnosticValue =
        TelemetryDiagnosticValue(TelemetryDiagnosticStatus.WAITING),
    val fuelPercent: TelemetryDiagnosticValue =
        TelemetryDiagnosticValue(TelemetryDiagnosticStatus.WAITING),
    val mileageListener: TelemetryDiagnosticValue =
        TelemetryDiagnosticValue(TelemetryDiagnosticStatus.WAITING),
    val odometerKilometers: TelemetryDiagnosticValue =
        TelemetryDiagnosticValue(TelemetryDiagnosticStatus.WAITING),
)

data class TelemetryDiagnosticValue(
    val status: TelemetryDiagnosticStatus,
    val value: Double? = null,
    val detail: String? = null,
)

enum class TelemetryDiagnosticStatus {
    DISCONNECTED,
    WAITING,
    SUCCESS,
    UNSUPPORTED,
    UNIMPLEMENTED,
    UNAVAILABLE,
    UNKNOWN,
    FAILED,
}
