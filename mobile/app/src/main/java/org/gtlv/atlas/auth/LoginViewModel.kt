package org.gtlv.atlas.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.gtlv.atlas.R
import org.gtlv.atlas.ui.UiText
import org.gtlv.core.repository.AuthResult
import org.gtlv.core.session.SessionManager
import org.gtlv.core.session.SessionRestoreResult
import org.gtlv.core.settings.ServerSettingsRepository

class LoginViewModel(
    private val sessionManager: SessionManager,
    private val serverSettingsRepository: ServerSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())

    val uiState: StateFlow<LoginUiState> =
        _uiState.asStateFlow()

    init {
        observeServerAddress()
        restoreSession()
    }

    private fun observeServerAddress() {
        viewModelScope.launch {
            serverSettingsRepository.serverAddress.collect { address ->
                _uiState.update {
                    it.copy(serverAddress = address)
                }
            }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            when (
                val result = sessionManager.restoreSession()
            ) {
                is SessionRestoreResult.Valid -> {
                    _uiState.update {
                        it.copy(
                            isCheckingSession = false,
                            loginSuccessful = true,
                            loginError = null
                        )
                    }
                }

                SessionRestoreResult.NoStoredSession,
                SessionRestoreResult.Expired -> {
                    _uiState.update {
                        it.copy(
                            isCheckingSession = false,
                            loginSuccessful = false,
                            loginError = null
                        )
                    }
                }

                SessionRestoreResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isCheckingSession = false,
                            loginError = UiText.Resource(
                                R.string.cannot_verify_session
                            )
                        )
                    }
                }

                SessionRestoreResult.InvalidResponse -> {
                    _uiState.update {
                        it.copy(
                            isCheckingSession = false,
                            loginError = UiText.Resource(
                                R.string.invalid_session_response
                            )
                        )
                    }
                }

                is SessionRestoreResult.ServerError -> {
                    _uiState.update {
                        it.copy(
                            isCheckingSession = false,
                            loginError = UiText.Resource(
                                resourceId =
                                    R.string.session_check_failed,
                                arguments =
                                    listOf(result.statusCode)
                            )
                        )
                    }
                }
            }
        }
    }

    fun openServerDialog() {
        _uiState.update {
            it.copy(
                showServerDialog = true,
                serverAddressInput = it.serverAddress,
                serverAddressError = null
            )
        }
    }

    fun closeServerDialog() {
        _uiState.update {
            it.copy(
                showServerDialog = false,
                serverAddressError = null
            )
        }
    }

    fun onServerAddressChanged(value: String) {
        _uiState.update {
            it.copy(
                serverAddressInput = value,
                serverAddressError = null
            )
        }
    }

    fun saveServerAddress() {
        val input = _uiState.value
            .serverAddressInput
            .trim()
            .removeSuffix("/")

        val parsedAddress = input.toHttpUrlOrNull()

        if (
            parsedAddress == null ||
            parsedAddress.scheme !in setOf("http", "https")
        ) {
            _uiState.update {
                it.copy(
                    serverAddressError = UiText.Resource(
                        R.string.server_address_error
                    )
                )
            }

            return
        }

        viewModelScope.launch {
            val addressChanged =
                input != _uiState.value.serverAddress

            if (addressChanged) {
                sessionManager.logout()
                serverSettingsRepository.setServerAddress(input)
            }

            _uiState.update {
                it.copy(
                    showServerDialog = false,
                    serverAddressError = null,
                    loginSuccessful = if (addressChanged) {
                        false
                    } else {
                        it.loginSuccessful
                    },
                    username = if (addressChanged) {
                        ""
                    } else {
                        it.username
                    },
                    password = if (addressChanged) {
                        ""
                    } else {
                        it.password
                    },
                    usernameError = null,
                    passwordError = null,
                    loginError = null
                )
            }
        }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update {
            it.copy(
                username = value,
                usernameError = null,
                loginError = null
            )
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null,
                loginError = null
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update {
            it.copy(
                passwordVisible = !it.passwordVisible
            )
        }
    }

    fun login() {
        val currentState = _uiState.value

        val usernameError =
            if (currentState.username.isBlank()) {
                UiText.Resource(
                    R.string.username_required
                )
            } else {
                null
            }

        val passwordError =
            if (currentState.password.isBlank()) {
                UiText.Resource(
                    R.string.password_required
                )
            } else {
                null
            }

        if (
            usernameError != null ||
            passwordError != null
        ) {
            _uiState.update {
                it.copy(
                    usernameError = usernameError,
                    passwordError = passwordError
                )
            }

            return
        }

        if (currentState.isLoading) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loginError = null
                )
            }

            val result = sessionManager.login(
                username = currentState.username.trim(),
                password = currentState.password
            )

            handleLoginResult(result)
        }
    }

    private fun handleLoginResult(
        result: AuthResult
    ) {
        when (result) {
            is AuthResult.Success -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loginSuccessful = true,
                        loginError = null
                    )
                }
            }

            AuthResult.InvalidCredentials -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loginSuccessful = false,
                        loginError = UiText.Resource(
                            R.string.invalid_credentials
                        )
                    )
                }
            }

            AuthResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loginSuccessful = false,
                        loginError = UiText.Resource(
                            R.string.cannot_connect_to_server
                        )
                    )
                }
            }

            AuthResult.InvalidResponse -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loginSuccessful = false,
                        loginError = UiText.Resource(
                            R.string.invalid_server_response
                        )
                    )
                }
            }

            is AuthResult.ServerError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loginSuccessful = false,
                        loginError = UiText.Resource(
                            resourceId =
                                R.string.server_error_with_code,
                            arguments =
                                listOf(result.statusCode)
                        )
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.logout()
        }
    }
}