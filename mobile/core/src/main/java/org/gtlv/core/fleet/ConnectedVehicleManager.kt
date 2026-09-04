package org.gtlv.core.fleet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.gtlv.core.session.SessionState
import org.gtlv.core.telemetry.TelemetryProvider

sealed interface ConnectedVehicleState {
    data object Disconnected : ConnectedVehicleState

    data class Resolving(val fingerprint: String) : ConnectedVehicleState

    data class Connected(
        val fingerprint: String,
        val vehicle: Vehicle
    ) : ConnectedVehicleState

    data class PairingRequired(
        val fingerprint: String,
        val candidates: List<Vehicle> = emptyList(),
        val isLoading: Boolean = false,
        val isPairing: Boolean = false,
        val hasError: Boolean = false
    ) : ConnectedVehicleState

    data class Unavailable(val fingerprint: String) : ConnectedVehicleState
}

class ConnectedVehicleManager(
    private val telemetryProvider: TelemetryProvider,
    private val sessionState: StateFlow<SessionState>,
    private val fleetRepository: FleetRepository,
    private val store: ConnectedVehicleCache
) {
    private val _state = MutableStateFlow<ConnectedVehicleState>(
        ConnectedVehicleState.Disconnected
    )
    val state: StateFlow<ConnectedVehicleState> = _state.asStateFlow()

    private var observationJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (observationJob != null) return
        observationJob = scope.launch {
            combine(
                telemetryProvider.vehicleFingerprint,
                sessionState
            ) { fingerprint, session -> fingerprint to session }
                .collectLatest { (fingerprint, session) ->
                    if (fingerprint == null) {
                        telemetryProvider.setResolvedVehicleId(null)
                        _state.value = ConnectedVehicleState.Disconnected
                        return@collectLatest
                    }

                    val signedIn = session as? SessionState.SignedIn
                    if (signedIn == null) {
                        telemetryProvider.setResolvedVehicleId(null)
                        _state.value = ConnectedVehicleState.Resolving(fingerprint)
                        return@collectLatest
                    }

                    resolve(fingerprint, signedIn.isAdmin)
                }
        }
    }

    suspend fun retryPairingCandidates() {
        val current = _state.value as? ConnectedVehicleState.PairingRequired
            ?: return
        loadPairingCandidates(current.fingerprint)
    }

    fun dismissPairing() {
        val current = _state.value as? ConnectedVehicleState.PairingRequired
            ?: return
        _state.value = ConnectedVehicleState.Unavailable(current.fingerprint)
    }

    suspend fun pair(vehicleId: String) {
        val current = _state.value as? ConnectedVehicleState.PairingRequired
            ?: return
        if (current.isPairing) return

        _state.value = current.copy(isPairing = true, hasError = false)
        when (
            fleetRepository.assignFingerprint(
                vehicleId = vehicleId,
                fingerprint = current.fingerprint
            )
        ) {
            AssignFingerprintResult.Success ->
                resolve(current.fingerprint, isAdmin = true)

            else -> _state.value = current.copy(
                isPairing = false,
                hasError = true
            )
        }
    }

    private suspend fun resolve(fingerprint: String, isAdmin: Boolean) {
        _state.value = ConnectedVehicleState.Resolving(fingerprint)

        val cachedVehicle = try {
            store.restore(fingerprint)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

        when (val result = fleetRepository.getVehicleByFingerprint(fingerprint)) {
            is VehicleLookupResult.Success -> {
                telemetryProvider.setResolvedVehicleId(result.vehicle.id)
                if (cachedVehicle != result.vehicle) {
                    try {
                        store.save(fingerprint, result.vehicle)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        // The live server result remains usable without the cache.
                    }
                }
                _state.value = ConnectedVehicleState.Connected(
                    fingerprint = fingerprint,
                    vehicle = result.vehicle
                )
            }

            VehicleLookupResult.NotFound -> {
                telemetryProvider.setResolvedVehicleId(null)
                clearCachedVehicle(fingerprint)
                if (isAdmin) {
                    loadPairingCandidates(fingerprint)
                } else {
                    _state.value = ConnectedVehicleState.Unavailable(fingerprint)
                }
            }

            else -> {
                telemetryProvider.setResolvedVehicleId(null)
                clearCachedVehicle(fingerprint)
                _state.value = ConnectedVehicleState.Unavailable(fingerprint)
            }
        }
    }

    private suspend fun loadPairingCandidates(fingerprint: String) {
        _state.value = ConnectedVehicleState.PairingRequired(
            fingerprint = fingerprint,
            isLoading = true
        )

        _state.value = when (
            val result = fleetRepository.getFingerprintCandidates()
        ) {
            is VehiclesResult.Success -> ConnectedVehicleState.PairingRequired(
                fingerprint = fingerprint,
                candidates = result.vehicles
            )

            else -> ConnectedVehicleState.PairingRequired(
                fingerprint = fingerprint,
                hasError = true
            )
        }
    }

    private suspend fun clearCachedVehicle(fingerprint: String) {
        try {
            store.clear(fingerprint)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // State is still cleared when persistent cache cleanup fails.
        }
    }
}
