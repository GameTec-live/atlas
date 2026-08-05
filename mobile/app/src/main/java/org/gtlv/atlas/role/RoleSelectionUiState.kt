package org.gtlv.atlas.role

import org.gtlv.atlas.ui.UiText
import org.gtlv.core.shift.ShiftRole

data class RoleSelectionUiState(
    val availabilityLoaded: Boolean = false,
    val isLoadingAvailability: Boolean = false,
    val isSelectingRole: Boolean = false,
    val selectedRole: ShiftRole? = null,
    val dispatcherAvailable: Boolean = false,
    val dispatcherSpotsFree: Int = 0,
    val error: UiText? = null
)