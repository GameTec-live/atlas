package org.gtlv.core.repository

import org.gtlv.core.session.SessionRestoreResult

interface AuthRepository {

    suspend fun login(
        email: String,
        password: String
    ): AuthResult

    suspend fun restoreStoredSession(): SessionRestoreResult

    suspend fun logout()
}