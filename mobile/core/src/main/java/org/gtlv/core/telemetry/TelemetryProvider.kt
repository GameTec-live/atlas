package org.gtlv.core.telemetry

import kotlinx.coroutines.flow.StateFlow

/** Provides telemetry that is ready to be serialized for the server. */
interface TelemetryProvider {
    /** Null until a valid location has been received. */
    val telemetry: StateFlow<TelemetryData?>

    /** Vehicle odometer in kilometres, independent of location availability. */
    val odometerKilometers: StateFlow<Double?>

    fun start()

    fun stop()

    fun setVehicleState(state: TelemetryVehicleState)

    /** Retries automatic vehicle identification after permission changes. */
    fun refreshVehicleId()
}
