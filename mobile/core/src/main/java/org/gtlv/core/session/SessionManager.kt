package org.gtlv.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gtlv.core.repository.AuthRepository
import org.gtlv.core.repository.AuthResult
import org.gtlv.core.role.RoleAvailabilityResult
import org.gtlv.core.role.RoleRepository
import org.gtlv.core.shift.ShiftSessionManager
import org.gtlv.core.shift.ShiftSessionState

class SessionManager(
    private val authRepository: AuthRepository,
    private val roleRepository: RoleRepository,
    private val shiftSessionManager: ShiftSessionManager
) {
    private val _state =
        MutableStateFlow<SessionState>(SessionState.Checking)

    val state: StateFlow<SessionState> =
        _state.asStateFlow()

    suspend fun restoreSession(): SessionRestoreResult {
        _state.value = SessionState.Checking

        val result = authRepository.restoreStoredSession()

        when (result) {
            is SessionRestoreResult.Valid -> {
                reconcileRole(
                    userId = result.userId,
                    userName = result.userName
                )
            }

            SessionRestoreResult.NoStoredSession,
            SessionRestoreResult.Expired,
            SessionRestoreResult.InvalidResponse,
            SessionRestoreResult.NetworkError,
            is SessionRestoreResult.ServerError -> {
                shiftSessionManager.clear()
                _state.value = SessionState.SignedOut
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
            reconcileRole(
                userId = result.userId,
                userName = result.userName
            )
        }

        return result
    }

    suspend fun retryRoleCheck() {
        val currentState = _state.value

        if (currentState !is SessionState.RoleCheckFailed) {
            return
        }

        reconcileRole(
            userId = currentState.userId,
            userName = currentState.userName
        )
    }

    private suspend fun reconcileRole(
        userId: String,
        userName: String
    ) {
        _state.value = SessionState.Checking

        // Restore any shift that survived an application restart.
        shiftSessionManager.restore()

        when (
            val result = roleRepository.getAvailability()
        ) {
            is RoleAvailabilityResult.Success -> {
                val assignedRole = result
                    .availability
                    .assignedRoles
                    .firstOrNull { assignment ->
                        assignment.driverId == userId
                    }

                if (assignedRole == null) {
                    // A local shift is stale when the server says
                    // this user currently has no role.
                    shiftSessionManager.clear()
                } else {
                    val currentShiftState =
                        shiftSessionManager.state.value

                    val currentShift =
                        (currentShiftState as? ShiftSessionState.Active)
                            ?.session

                    /*
                     * Keep the existing start time after an ordinary
                     * process restart. After logout the shift was
                     * deleted, so a new login reaches startShift()
                     * and records Instant.now() in UTC.
                     */
                    if (
                        currentShift == null ||
                        currentShift.role != assignedRole.role
                    ) {
                        shiftSessionManager.startShift(
                            assignedRole.role
                        )
                    }
                }

                _state.value = SessionState.SignedIn(
                    userId = userId,
                    userName = userName
                )
            }

            RoleAvailabilityResult.Unauthorized -> {
                logout()
            }

            RoleAvailabilityResult.NetworkError,
            RoleAvailabilityResult.InvalidResponse,
            is RoleAvailabilityResult.ServerError -> {
                _state.value = SessionState.RoleCheckFailed(
                    userId = userId,
                    userName = userName
                )
            }
        }
    }

    suspend fun logout() {
        shiftSessionManager.clear()
        authRepository.logout()
        _state.value = SessionState.SignedOut
    }
}