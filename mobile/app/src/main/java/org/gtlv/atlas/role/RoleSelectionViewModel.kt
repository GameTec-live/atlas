package org.gtlv.atlas.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gtlv.atlas.R
import org.gtlv.atlas.ui.UiText
import org.gtlv.core.role.RoleAvailabilityResult
import org.gtlv.core.role.RoleRepository
import org.gtlv.core.role.SelectRoleResult
import org.gtlv.core.session.SessionManager
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.shift.ShiftSessionManager

class RoleSelectionViewModel(
    private val roleRepository: RoleRepository,
    private val shiftSessionManager: ShiftSessionManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(RoleSelectionUiState())

    val uiState: StateFlow<RoleSelectionUiState> =
        _uiState.asStateFlow()

    init {
        loadAvailability()
    }

    fun retry() {
        val pendingRole = _uiState.value.pendingShiftRole

        if (pendingRole != null) {
            retryShiftSave(pendingRole)
        } else {
            loadAvailability()
        }
    }

    fun selectDriver() {
        selectRole(ShiftRole.DRIVER)
    }

    fun selectDispatcher() {
        selectRole(ShiftRole.DISPATCHER)
    }

    private fun loadAvailability() {
        val currentState = _uiState.value

        if (
            currentState.isLoadingAvailability ||
            currentState.isSelectingRole ||
            currentState.pendingShiftRole != null
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isLoadingAvailability = true,
                availabilityLoaded = false,
                error = null
            )
        }

        viewModelScope.launch {
            when (val result = roleRepository.getAvailability()) {
                is RoleAvailabilityResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAvailability = false,
                            availabilityLoaded = true,
                            dispatcherAvailable =
                                result.availability.dispatcherAvailable,
                            dispatcherSpotsFree =
                                result.availability.dispatcherSpotsFree,
                            error = null
                        )
                    }
                }

                RoleAvailabilityResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAvailability = false,
                            availabilityLoaded = false,
                            error = UiText.Resource(
                                R.string.role_unauthorized
                            )
                        )
                    }

                    sessionManager.logout()
                }

                RoleAvailabilityResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAvailability = false,
                            availabilityLoaded = false,
                            error = UiText.Resource(
                                R.string.role_network_error
                            )
                        )
                    }
                }

                RoleAvailabilityResult.InvalidResponse -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAvailability = false,
                            availabilityLoaded = false,
                            error = UiText.Resource(
                                R.string.role_load_error
                            )
                        )
                    }
                }

                is RoleAvailabilityResult.ServerError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAvailability = false,
                            availabilityLoaded = false,
                            error = UiText.Resource(
                                R.string.role_load_error
                            )
                        )
                    }
                }
            }
        }
    }

    private fun selectRole(role: ShiftRole) {
        val currentState = _uiState.value

        if (
            !currentState.availabilityLoaded ||
            currentState.isLoadingAvailability ||
            currentState.isSelectingRole ||
            currentState.pendingShiftRole != null
        ) {
            return
        }

        if (
            role == ShiftRole.DISPATCHER &&
            !currentState.dispatcherAvailable
        ) {
            _uiState.update {
                it.copy(
                    error = UiText.Resource(
                        R.string.dispatcher_unavailable
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isSelectingRole = true,
                selectedRole = role,
                error = null
            )
        }

        viewModelScope.launch {
            val result = roleRepository.selectRole(role)


            when (result) {
                SelectRoleResult.Success -> {
                    _uiState.update {
                        it.copy(
                            pendingShiftRole = role
                        )
                    }

                    saveShift(role)
                }

                is SelectRoleResult.RoleUnavailable -> {
                    _uiState.update {
                        it.copy(
                            isSelectingRole = false,
                            selectedRole = null,
                            dispatcherAvailable = if (
                                role == ShiftRole.DISPATCHER
                            ) {
                                false
                            } else {
                                it.dispatcherAvailable
                            },
                            dispatcherSpotsFree = if (
                                role == ShiftRole.DISPATCHER
                            ) {
                                0
                            } else {
                                it.dispatcherSpotsFree
                            },
                            error = UiText.Resource(
                                R.string.role_unavailable
                            )
                        )
                    }
                }

                SelectRoleResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(
                            isSelectingRole = false,
                            selectedRole = null,
                            error = UiText.Resource(
                                R.string.role_unauthorized
                            )
                        )
                    }

                    sessionManager.logout()
                }

                SelectRoleResult.NetworkError -> {
                    selectionFailed(
                        R.string.role_network_error
                    )
                }

                is SelectRoleResult.ServerError -> {
                    selectionFailed(
                        R.string.role_selection_error
                    )
                }
            }
        }
    }

    private suspend fun saveShift(role: ShiftRole) {
        try {
            shiftSessionManager.startShift(role)

            _uiState.update {
                it.copy(
                    isSelectingRole = false,
                    selectedRole = null,
                    pendingShiftRole = null,
                    error = null
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isSelectingRole = false,
                    selectedRole = null,
                    pendingShiftRole = role,
                    error = UiText.Resource(
                        R.string.shift_save_error
                    )
                )
            }
        }
    }

    private fun retryShiftSave(role: ShiftRole) {
        val currentState = _uiState.value

        if (
            currentState.isSelectingRole ||
            currentState.pendingShiftRole != role
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isSelectingRole = true,
                selectedRole = role,
                error = null
            )
        }

        viewModelScope.launch {
            saveShift(role)
        }
    }

    private fun selectionFailed(messageResource: Int) {
        _uiState.update {
            it.copy(
                isSelectingRole = false,
                selectedRole = null,
                error = UiText.Resource(messageResource)
            )
        }
    }
}