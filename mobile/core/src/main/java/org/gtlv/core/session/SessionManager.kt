package org.gtlv.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gtlv.core.repository.AuthRepository
import org.gtlv.core.repository.AuthResult

class SessionManager(
    private val authRepository: AuthRepository
) {
    private val _state =
        MutableStateFlow<SessionState>(SessionState.Checking)

    val state: StateFlow<SessionState> =
        _state.asStateFlow()

    suspend fun restoreSession(): SessionRestoreResult {
        val result = authRepository.restoreStoredSession()

        _state.value = when (result) {
            is SessionRestoreResult.Valid -> {
                SessionState.SignedIn(
                    userName = result.userName
                )
            }

            SessionRestoreResult.NoStoredSession,
            SessionRestoreResult.Expired,
            SessionRestoreResult.InvalidResponse,
            SessionRestoreResult.NetworkError,
            is SessionRestoreResult.ServerError -> {
                SessionState.SignedOut
            }
        }

        return result
    }

    suspend fun login(
        username: String,
        password: String
    ): AuthResult {
        val result = authRepository.login(
            username = username,
            password = password
        )

        if (result is AuthResult.Success) {
            _state.value = SessionState.SignedIn(
                userName = result.userName
            )
        }

        return result
    }

    suspend fun logout() {
        authRepository.logout()
        _state.value = SessionState.SignedOut
    }
}