package org.gtlv.core.location

/** Connects a car-session location provider to the application's map. */
interface CarLocationProviderRegistry {
    fun registerCarLocationProvider(provider: LocationProvider)

    fun unregisterCarLocationProvider(provider: LocationProvider)
}
