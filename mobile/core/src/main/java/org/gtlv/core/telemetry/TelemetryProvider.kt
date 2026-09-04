package org.gtlv.core.telemetry

import kotlinx.coroutines.flow.StateFlow

/** Provides telemetry that is ready to be serialized for the server. */
interface TelemetryProvider {
    /** Null until a valid location has been received. */
    val telemetry: StateFlow<TelemetryData?>

    /** Vehicle odometer in kilometres, independent of location availability. */
    val odometerKilometers: StateFlow<Double?>

    /** Stable identity derived from the currently connected car's Bluetooth MAC. */
    val vehicleFingerprint: StateFlow<String?>

    fun start()

    fun stop()

    fun setVehicleState(state: TelemetryVehicleState)

    /** Sets the fleet vehicle id after [vehicleFingerprint] has been resolved. */
    fun setResolvedVehicleId(vehicleId: String?)

    /** Retries automatic vehicle identification after permission changes. */
    fun refreshVehicleId()
}
