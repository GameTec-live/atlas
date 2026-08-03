package org.gtlv.atlas.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.core.repository.AuthRepository
import org.gtlv.core.settings.ServerSettingsRepository

class LoginViewModelFactory(
    private val authRepository: AuthRepository,
    private val serverSettingsRepository: ServerSettingsRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(
                authRepository = authRepository,
                serverSettingsRepository = serverSettingsRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}