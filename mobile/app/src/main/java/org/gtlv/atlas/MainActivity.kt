package org.gtlv.atlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.gtlv.atlas.auth.LoginScreen
import org.gtlv.atlas.auth.LoginViewModel
import org.gtlv.atlas.auth.LoginViewModelFactory
import org.gtlv.atlas.ui.theme.AtlasTheme
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.repository.AuthRepository
import org.gtlv.core.repository.AuthRepositoryImpl
import org.gtlv.core.session.SecureSessionStore
import org.gtlv.core.session.SessionManager
import org.gtlv.core.session.SessionState
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

    private val sessionManager by lazy {
        SessionManager(
            authRepository = authRepository
        )
    }

    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(
            sessionManager = sessionManager,
            serverSettingsRepository = serverSettingsRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AtlasTheme {
                val loginState by loginViewModel.uiState
                    .collectAsStateWithLifecycle()

                val sessionState by sessionManager.state
                    .collectAsStateWithLifecycle()

                when (val currentSession = sessionState) {
                    SessionState.Checking -> {
                        SessionLoadingScreen()
                    }

                    SessionState.SignedOut -> {
                        LoginScreen(
                            state = loginState,
                            onUsernameChanged =
                                loginViewModel::onUsernameChanged,
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

                    is SessionState.SignedIn -> {
                        MainScreen(
                            userName = currentSession.userName,
                            onLogout = loginViewModel::logout
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MainScreen(
    userName: String,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome, $userName",
            style = MaterialTheme.typography.headlineMedium
        )

        Button(onClick = onLogout) {
            Text(text = "Log out")
        }
    }
}