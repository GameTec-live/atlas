package org.gtlv.core.shift

sealed interface ShiftSessionState {

    data object Loading : ShiftSessionState

    data object NoActiveShift : ShiftSessionState

    data class Active(
        val session: ShiftSession
    ) : ShiftSessionState
}