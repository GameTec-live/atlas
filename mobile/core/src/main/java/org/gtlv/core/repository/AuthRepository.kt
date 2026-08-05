package org.gtlv.core.repository

import org.gtlv.core.session.SessionRestoreResult

interface AuthRepository {

    suspend fun login(
        username: String,
        password: String
    ): AuthResult

    suspend fun restoreStoredSession(): SessionRestoreResult

    suspend fun logout()
}