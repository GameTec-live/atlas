package org.gtlv.core.telemetry

import org.gtlv.core.location.LocationProvider

/** Connects car-session telemetry to the application process. */
interface TelemetryProviderRegistry {
    val telemetryLocationProvider: LocationProvider

    fun registerTelemetryProvider(provider: TelemetryProvider)

    fun unregisterTelemetryProvider(provider: TelemetryProvider)
}
