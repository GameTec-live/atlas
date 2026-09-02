package org.gtlv.core.shift

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val mutationMutex = Mutex()

    suspend fun restore() = mutationMutex.withLock {
        val storedSession = store.restore()

        _state.value = if (storedSession == null) {
            ShiftSessionState.NoActiveShift
        } else {
            ShiftSessionState.Active(storedSession)
        }
    }

    suspend fun startShift(role: ShiftRole) = mutationMutex.withLock {
        val session = ShiftSession(
            role = role,
            startTimeUtc = Instant.now(clock)
        )

        store.save(session)

        _state.value =
            ShiftSessionState.Active(session)
    }

    suspend fun setStartKilometerIfAbsent(
        startKilometer: Double
    ) = mutationMutex.withLock {
        require(
            startKilometer.isFinite() && startKilometer >= 0.0
        ) {
            "startKilometer must be a finite, non-negative value"
        }

        val currentSession =
            (_state.value as? ShiftSessionState.Active)
                ?.session
                ?: return@withLock

        if (currentSession.startKilometer != null) {
            return@withLock
        }

        val updatedSession = currentSession.copy(
            startKilometer = startKilometer
        )

        store.save(updatedSession)
        _state.value = ShiftSessionState.Active(updatedSession)
    }

    suspend fun clear() = mutationMutex.withLock {
        store.clear()
        _state.value = ShiftSessionState.NoActiveShift
    }
}
