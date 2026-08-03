package org.gtlv.atlas.auth

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val loginError: String? = null,
    val loginSuccessful: Boolean = false,
    val isCheckingSession: Boolean = true,

    val serverAddress: String = "",
    val serverAddressInput: String = "",
    val serverAddressError: String? = null,
    val showServerDialog: Boolean = false
)