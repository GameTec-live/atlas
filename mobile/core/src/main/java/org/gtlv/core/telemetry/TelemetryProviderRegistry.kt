package org.gtlv.core.telemetry

import android.content.Context

/** Connects optional Android Auto vehicle data to process-wide telemetry. */
interface TelemetryProviderRegistry {
    val telemetryProvider: TelemetryProvider

    fun connectCarTelemetry(carContext: Context)

    fun disconnectCarTelemetry(carContext: Context)
}
