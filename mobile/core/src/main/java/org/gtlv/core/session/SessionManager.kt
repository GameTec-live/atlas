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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
                try {
                    shiftSessionManager.clear()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {

                } finally {
                    _state.value = SessionState.SignedOut
                }
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

        try {
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
                        /*
                         * The local shift is stale when the server says
                         * this user currently has no role.
                         */
                        shiftSessionManager.clear()
                    } else {
                        val currentShiftState =
                            shiftSessionManager.state.value

                        val currentShift =
                            (
                                    currentShiftState
                                            as? ShiftSessionState.Active
                                    )
                                ?.session

                        /*
                         * Keep the original start time when the restored
                         * local role matches the server role.
                         *
                         * If there is no local shift, or its role differs
                         * from the server role, persist a new local shift.
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
                    _state.value =
                        SessionState.RoleCheckFailed(
                            userId = userId,
                            userName = userName
                        )
                }
            }
        } catch (exception: CancellationException) {
            /*
             * Coroutine cancellation is lifecycle control, not an
             * application failure. It must remain cancelled.
             */
            throw exception
        } catch (_: Exception) {
            /*
             * A local restore, clear or save operation failed.
             * Leave Checking so the UI can show Retry and Logout.
             */
            _state.value = SessionState.RoleCheckFailed(
                userId = userId,
                userName = userName
            )
        }
    }

    suspend fun logout() {
        withContext(NonCancellable) {
            /*
             * A broken local shift store must not prevent the
             * authentication session from being cleared.
             */
            try {
                shiftSessionManager.clear()
            } catch (_: Exception) {
                // Continue with authentication cleanup.
            }

            /*
             * Do not swallow authentication cleanup failures.
             * If this throws, the caller can retry logoutt.
             */
            authRepository.logout()

            _state.value = SessionState.SignedOut
        }
    }
}