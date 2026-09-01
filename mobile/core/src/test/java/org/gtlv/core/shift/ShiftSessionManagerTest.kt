package org.gtlv.core.shift

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ShiftSessionManagerTest {
    private val startTime = Instant.parse("2026-09-01T12:00:00Z")
    private val clock = Clock.fixed(startTime, ZoneOffset.UTC)

    @Test
    fun `a new shift starts without an odometer`() = runBlocking {
        val store = FakeShiftSessionStore()
        val manager = ShiftSessionManager(store, clock)

        manager.startShift(ShiftRole.DRIVER)

        val session = manager.activeSession()
        assertEquals(ShiftRole.DRIVER, session.role)
        assertEquals(startTime, session.startTimeUtc)
        assertNull(session.startKilometer)
        assertEquals(session, store.savedSession)
    }

    @Test
    fun `the first start kilometer is persisted and cannot be replaced`() =
        runBlocking {
            val store = FakeShiftSessionStore()
            val manager = ShiftSessionManager(store, clock)
            manager.startShift(ShiftRole.DRIVER)

            manager.setStartKilometerIfAbsent(12_345.6)
            manager.setStartKilometerIfAbsent(54_321.0)

            assertEquals(
                12_345.6,
                manager.activeSession().startKilometer
            )
            assertEquals(
                12_345.6,
                store.savedSession?.startKilometer
            )
        }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative start kilometer is rejected`() = runBlocking {
        val manager = ShiftSessionManager(
            FakeShiftSessionStore(),
            clock
        )
        manager.startShift(ShiftRole.DRIVER)

        manager.setStartKilometerIfAbsent(-1.0)
    }

    private fun ShiftSessionManager.activeSession(): ShiftSession {
        return (state.value as ShiftSessionState.Active).session
    }
}

private class FakeShiftSessionStore : ShiftSessionStore {
    var savedSession: ShiftSession? = null

    override suspend fun restore(): ShiftSession? = savedSession

    override suspend fun save(session: ShiftSession) {
        savedSession = session
    }

    override suspend fun clear() {
        savedSession = null
    }
}
