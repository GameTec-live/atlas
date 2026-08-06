package org.gtlv.core.shift

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant

class ShiftSessionManager(
    private val store: ShiftSessionStore,
    private val clock: Clock = Clock.systemUTC()
) {
    private val _state =
        MutableStateFlow<ShiftSessionState>(
            ShiftSessionState.Loading
        )

    val state: StateFlow<ShiftSessionState> =
        _state.asStateFlow()

    suspend fun restore() {
        val storedSession = store.restore()

        _state.value = if (storedSession == null) {
            ShiftSessionState.NoActiveShift
        } else {
            ShiftSessionState.Active(storedSession)
        }
    }

    suspend fun startShift(role: ShiftRole) {
        val session = ShiftSession(
            role = role,
            startTimeUtc = Instant.now(clock)
        )

        store.save(session)

        _state.value =
            ShiftSessionState.Active(session)
    }

    suspend fun clear() {
        store.clear()
        _state.value = ShiftSessionState.NoActiveShift
    }
}