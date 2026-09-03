package org.gtlv.atlas.offboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gtlv.core.fleet.ConnectedVehicleState
import org.gtlv.core.logbook.LogbookRepository
import org.gtlv.core.logbook.LogbookSubmission
import org.gtlv.core.logbook.SubmitLogbookResult
import org.gtlv.core.shift.ShiftSession
import org.gtlv.core.shift.ShiftSessionManager
import org.gtlv.core.shift.ShiftSessionState
import org.gtlv.core.telemetry.TelemetryProvider

class OffboardingViewModel(
    private val shiftSessionManager: ShiftSessionManager,
    private val telemetryProvider: TelemetryProvider,
    private val connectedVehicleState: StateFlow<ConnectedVehicleState>,
    private val logbookRepository: LogbookRepository,
    private val logout: suspend () -> Unit
) : ViewModel() {
    private val _uiState = MutableStateFlow(OffboardingUiState())
    val uiState: StateFlow<OffboardingUiState> = _uiState.asStateFlow()

    init {
        observeShiftSession()
        observeConnectedVehicle()
    }

    fun requestLogout() {
        val session = activeSession() ?: return
        val startKilometer = session.startKilometer
        val endKilometer = telemetryProvider.odometerKilometers.value
            ?.takeIf { value ->
                value.isFinite() && value >= 0.0 &&
                    (startKilometer == null || value >= startKilometer)
            }

        viewModelScope.launch {
            try {
                shiftSessionManager.beginShiftEnd(endKilometer)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(error = OffboardingError.SUBMISSION_FAILED)
                }
            }
        }
    }

    fun updateEndKilometerInput(value: String) {
        _uiState.update {
            it.copy(
                endKilometerInput = value,
                isEndKilometerInvalid = false
            )
        }
    }

    fun confirmEndKilometer() {
        val value = parseNumber(_uiState.value.endKilometerInput)
        val startKilometer = activeSession()?.startKilometer
        if (
            value == null || value < 0.0 ||
            (startKilometer != null && value < startKilometer)
        ) {
            _uiState.update { it.copy(isEndKilometerInvalid = true) }
            return
        }

        viewModelScope.launch {
            try {
                shiftSessionManager.setEndKilometer(value)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.update { it.copy(isEndKilometerInvalid = true) }
            }
        }
    }

    fun dismissEndKilometerDialog() {
        cancelOffboarding()
    }

    fun cancelOffboarding() {
        if (_uiState.value.isSubmitting) return

        viewModelScope.launch {
            try {
                shiftSessionManager.cancelShiftEnd()
                _uiState.update {
                    it.copy(
                        endKilometerInput = "",
                        isEndKilometerInvalid = false,
                        revenueInput = "",
                        isRevenueInvalid = false,
                        isConfirmed = false,
                        error = null
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(error = OffboardingError.SUBMISSION_FAILED)
                }
            }
        }
    }

    fun updateRevenue(value: String) {
        _uiState.update {
            it.copy(
                revenueInput = value,
                isRevenueInvalid = false,
                error = null
            )
        }
    }

    fun setConfirmed(confirmed: Boolean) {
        _uiState.update { it.copy(isConfirmed = confirmed, error = null) }
    }

    fun submitAndLogout() {
        val state = _uiState.value
        if (state.isSubmitting || !state.isConfirmed) return

        val revenue = parseNumber(state.revenueInput)
        if (revenue == null || !revenue.isFinite() || revenue < 0.0) {
            _uiState.update { it.copy(isRevenueInvalid = true) }
            return
        }

        val session = state.session ?: return
        val vehicle = state.vehicle
        if (vehicle == null) {
            _uiState.update {
                it.copy(error = OffboardingError.VEHICLE_UNAVAILABLE)
            }
            return
        }

        val startKilometer = session.startKilometer
        if (startKilometer == null) {
            _uiState.update {
                it.copy(error = OffboardingError.START_KILOMETER_UNAVAILABLE)
            }
            return
        }

        val endKilometer = session.endKilometer ?: return
        val endTime = session.endTimeUtc ?: return

        val submission = LogbookSubmission(
            vehicleId = vehicle.id,
            startedAt = session.startTimeUtc,
            startOdometer = startKilometer.roundToLong(),
            endOdometer = endKilometer.roundToLong(),
            endedAt = endTime,
            revenue = revenue
        )

        _uiState.update {
            it.copy(isSubmitting = true, error = null)
        }

        viewModelScope.launch {
            try {
                when (logbookRepository.submit(submission)) {
                    SubmitLogbookResult.Success -> logout()
                    else -> submissionFailed()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                submissionFailed()
            }
        }
    }

    private fun observeShiftSession() {
        viewModelScope.launch {
            shiftSessionManager.state.collect { state ->
                val session = (state as? ShiftSessionState.Active)?.session
                _uiState.update { current ->
                    current.copy(
                        session = session,
                        isVisible = session?.endTimeUtc != null &&
                            session.endKilometer != null,
                        isEndKilometerDialogVisible =
                            session?.endTimeUtc != null &&
                                session.endKilometer == null
                    )
                }
            }
        }
    }

    private fun observeConnectedVehicle() {
        viewModelScope.launch {
            connectedVehicleState.collect { state ->
                val connected = state as? ConnectedVehicleState.Connected
                    ?: return@collect
                _uiState.update { it.copy(vehicle = connected.vehicle) }
            }
        }
    }

    private fun activeSession(): ShiftSession? =
        (shiftSessionManager.state.value as? ShiftSessionState.Active)?.session

    private fun parseNumber(value: String): Double? = value
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()

    private fun submissionFailed() {
        _uiState.update {
            it.copy(
                isSubmitting = false,
                error = OffboardingError.SUBMISSION_FAILED
            )
        }
    }
}
