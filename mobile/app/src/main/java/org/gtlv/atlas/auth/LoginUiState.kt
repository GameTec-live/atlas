package org.gtlv.atlas.auth

import org.gtlv.atlas.ui.UiText

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,

    val usernameError: UiText? = null,
    val passwordError: UiText? = null,
    val loginError: UiText? = null,

    val serverAddress: String = "",
    val serverAddressInput: String = "",
    val serverAddressError: UiText? = null,
    val showServerDialog: Boolean = false
)