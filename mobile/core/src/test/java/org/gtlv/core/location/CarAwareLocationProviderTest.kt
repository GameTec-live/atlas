package org.gtlv.core.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class CarAwareLocationProviderTest {
    @Test
    fun `car location is preferred and phone is the fallback`() {
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined
        )
        val phoneLocation = atlasLocation(LocationSource.PHONE)
        val carLocation = atlasLocation(LocationSource.CAR)
        val phoneProvider = FakeLocationProvider(
            LocationState.Available(phoneLocation)
        )
        val provider = CarAwareLocationProvider(phoneProvider, scope)

        assertEquals(
            phoneLocation,
            (provider.state.value as LocationState.Available).location
        )

        val carProvider = FakeLocationProvider(LocationState.Stopped)
        provider.registerCarLocationProvider(carProvider)
        carProvider.setState(LocationState.Available(carLocation))

        assertEquals(
            carLocation,
            (provider.state.value as LocationState.Available).location
        )

        carProvider.setState(LocationState.Unavailable)

        assertEquals(
            phoneLocation,
            (provider.state.value as LocationState.Available).location
        )

        scope.cancel()
    }

    @Test
    fun `phone location stops after service and car usage end`() {
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined
        )
        val phoneProvider = FakeLocationProvider(LocationState.Stopped)
        val carProvider = FakeLocationProvider(LocationState.Stopped)
        val provider = CarAwareLocationProvider(phoneProvider, scope)

        provider.start()
        provider.registerCarLocationProvider(carProvider)

        provider.stop()

        assertEquals(true, phoneProvider.isStarted)
        assertEquals(0, phoneProvider.stopCount)

        provider.unregisterCarLocationProvider(carProvider)

        assertEquals(false, phoneProvider.isStarted)
        assertEquals(1, phoneProvider.stopCount)

        scope.cancel()
    }

    @Test
    fun `phone location continues after car disconnects during service usage`() {
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined
        )
        val phoneProvider = FakeLocationProvider(LocationState.Stopped)
        val carProvider = FakeLocationProvider(LocationState.Stopped)
        val provider = CarAwareLocationProvider(phoneProvider, scope)

        provider.start()
        provider.registerCarLocationProvider(carProvider)
        provider.unregisterCarLocationProvider(carProvider)

        assertEquals(true, phoneProvider.isStarted)
        assertEquals(0, phoneProvider.stopCount)

        provider.stop()

        assertEquals(false, phoneProvider.isStarted)
        assertEquals(1, phoneProvider.stopCount)

        scope.cancel()
    }

    private fun atlasLocation(source: LocationSource): AtlasLocation {
        return AtlasLocation(
            latitude = 48.511,
            longitude = 14.504,
            accuracyMeters = 5f,
            bearingDegrees = 0f,
            speedMetersPerSecond = 0f,
            timestampMillis = 1L,
            source = source
        )
    }

    private class FakeLocationProvider(
        initialState: LocationState
    ) : LocationProvider {
        private val mutableState = MutableStateFlow(initialState)

        override val state: StateFlow<LocationState> = mutableState

        var isStarted = false
            private set

        var stopCount = 0
            private set

        override fun start() {
            isStarted = true
        }

        override fun stop() {
            if (!isStarted) return

            isStarted = false
            stopCount += 1
        }

        fun setState(state: LocationState) {
            mutableState.value = state
        }
    }
}
