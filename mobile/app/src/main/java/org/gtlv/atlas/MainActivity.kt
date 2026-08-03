package org.gtlv.atlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.gtlv.atlas.auth.LoginScreen
import org.gtlv.atlas.auth.LoginViewModel
import org.gtlv.atlas.auth.LoginViewModelFactory
import org.gtlv.atlas.ui.theme.AtlasTheme
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.repository.AuthRepository
import org.gtlv.core.repository.AuthRepositoryImpl
import org.gtlv.core.session.SecureSessionStore
import org.gtlv.core.settings.DataStoreServerSettingsRepository

class MainActivity : ComponentActivity() {

    private val networkClient by lazy {
        NetworkClient()
    }

    private val serverSettingsRepository by lazy {
        DataStoreServerSettingsRepository(
            context = applicationContext
        )
    }

    private val secureSessionStore by lazy {
        SecureSessionStore(
            context = applicationContext
        )
    }

    private val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository = serverSettingsRepository,
            secureSessionStore = secureSessionStore
        )
    }

    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(
            authRepository = authRepository,
            serverSettingsRepository = serverSettingsRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AtlasTheme {
                val state by loginViewModel.uiState
                    .collectAsStateWithLifecycle()

                LoginScreen(
                    state = state,
                    onEmailChanged =
                        loginViewModel::onEmailChanged,
                    onPasswordChanged =
                        loginViewModel::onPasswordChanged,
                    onPasswordVisibilityChanged =
                        loginViewModel::togglePasswordVisibility,
                    onLogin =
                        loginViewModel::login,
                    onEditServer =
                        loginViewModel::openServerDialog,
                    onServerAddressChanged =
                        loginViewModel::onServerAddressChanged,
                    onSaveServerAddress =
                        loginViewModel::saveServerAddress,
                    onDismissServerDialog =
                        loginViewModel::closeServerDialog
                )
            }
        }
    }
}