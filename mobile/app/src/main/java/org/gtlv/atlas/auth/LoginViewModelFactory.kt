package org.gtlv.atlas.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.core.repository.AuthRepository
import org.gtlv.core.session.SessionManager
import org.gtlv.core.settings.ServerSettingsRepository

class LoginViewModelFactory(
    private val sessionManager: SessionManager,
    private val serverSettingsRepository: ServerSettingsRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(
                sessionManager = sessionManager,
                serverSettingsRepository = serverSettingsRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}