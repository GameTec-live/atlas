package org.gtlv.core.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Publishes car location when one is available and otherwise falls back to
 * the phone location provider.
 */
class CarAwareLocationProvider(
    private val phoneLocationProvider: LocationProvider,
    private val scope: CoroutineScope
) : LocationProvider {
    private val _state = MutableStateFlow(phoneLocationProvider.state.value)

    override val state: StateFlow<LocationState> = _state.asStateFlow()

    private var phoneState = phoneLocationProvider.state.value
    private var carState: LocationState = LocationState.Stopped
    private var carLocationProvider: LocationProvider? = null
    private var carStateJob: Job? = null

    init {
        scope.launch {
            phoneLocationProvider.state.collect { newPhoneState ->
                phoneState = newPhoneState
                publishPreferredState()
            }
        }
    }

    override fun start() {
        phoneLocationProvider.start()
    }

    override fun stop() {
        phoneLocationProvider.stop()
    }

    fun registerCarLocationProvider(provider: LocationProvider) {
        if (carLocationProvider === provider) return

        carStateJob?.cancel()
        carLocationProvider = provider
        carState = provider.state.value
        publishPreferredState()

        carStateJob = scope.launch {
            provider.state.collect { newCarState ->
                carState = newCarState
                publishPreferredState()
            }
        }
    }

    fun unregisterCarLocationProvider(provider: LocationProvider) {
        if (carLocationProvider !== provider) return

        carStateJob?.cancel()
        carStateJob = null
        carLocationProvider = null
        carState = LocationState.Stopped
        publishPreferredState()
    }

    private fun publishPreferredState() {
        _state.value = if (carState is LocationState.Available) {
            carState
        } else {
            phoneState
        }
    }
}
