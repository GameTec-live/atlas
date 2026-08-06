package org.gtlv.core.shift

interface ShiftSessionStore {

    suspend fun restore(): ShiftSession?

    suspend fun save(session: ShiftSession)

    suspend fun clear()
}