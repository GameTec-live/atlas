package org.gtlv.atlas.offboarding

import org.gtlv.core.fleet.Vehicle
import org.gtlv.core.shift.ShiftSession

data class OffboardingUiState(
    val isVisible: Boolean = false,
    val isEndKilometerDialogVisible: Boolean = false,
    val endKilometerInput: String = "",
    val isEndKilometerInvalid: Boolean = false,
    val session: ShiftSession? = null,
    val vehicle: Vehicle? = null,
    val revenueInput: String = "",
    val isRevenueInvalid: Boolean = false,
    val isConfirmed: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: OffboardingError? = null
)

enum class OffboardingError {
    VEHICLE_UNAVAILABLE,
    START_KILOMETER_UNAVAILABLE,
    SUBMISSION_FAILED
}
