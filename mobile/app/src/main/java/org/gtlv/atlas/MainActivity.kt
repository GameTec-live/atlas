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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.gtlv.atlas.auth.LoginScreen
import org.gtlv.atlas.auth.LoginViewModel
import org.gtlv.atlas.auth.LoginViewModelFactory
import org.gtlv.atlas.location.RequiredLocationPermissionGate
import org.gtlv.atlas.navigation.AuthenticatedNavHost
import org.gtlv.atlas.role.RoleSelectionScreen
import org.gtlv.atlas.role.RoleSelectionViewModel
import org.gtlv.atlas.role.RoleSelectionViewModelFactory
import org.gtlv.atlas.ui.theme.AtlasTheme
import org.gtlv.core.session.SessionState
import org.gtlv.core.shift.ShiftSessionState
import org.gtlv.atlas.main.MainScreenViewModel
import org.gtlv.atlas.main.MainScreenViewModelFactory

class MainActivity : ComponentActivity() {

    private val atlasApplication by lazy {
        application as AtlasApplication
    }

    private val mainScreenViewModel:
            MainScreenViewModel by viewModels {
        MainScreenViewModelFactory(
            jobRepository =
                atlasApplication.jobRepository
        )
    }

    private val sessionManager
        get() = atlasApplication.sessionManager

    private val shiftSessionManager
        get() = atlasApplication.shiftSessionManager

    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(
            sessionManager = atlasApplication.sessionManager,
            serverSettingsRepository =
                atlasApplication.serverSettingsRepository
        )
    }

    private val roleSelectionViewModel:
            RoleSelectionViewModel by viewModels {
        RoleSelectionViewModelFactory(
            roleRepository =
                atlasApplication.roleRepository,
            shiftSessionManager =
                atlasApplication.shiftSessionManager,
            sessionManager =
                atlasApplication.sessionManager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AtlasTheme {
                val coroutineScope = rememberCoroutineScope()

                val loginState by loginViewModel.uiState
                    .collectAsStateWithLifecycle()

                val sessionState by sessionManager.state
                    .collectAsStateWithLifecycle()

                LaunchedEffect(sessionState) {
                    if (sessionState !is SessionState.SignedIn) {
                        mainScreenViewModel.clearJobs()
                    }
                }

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

                    is SessionState.RoleCheckFailed -> {
                        RoleCheckFailedScreen(
                            onRetry = {
                                coroutineScope.launch {
                                    sessionManager.retryRoleCheck()
                                }
                            },
                            onLogout = loginViewModel::logout
                        )
                    }

                    is SessionState.SignedIn -> {
                        val shiftState by shiftSessionManager.state
                            .collectAsStateWithLifecycle()

                        when (val currentShift = shiftState) {
                            ShiftSessionState.Loading -> {
                                SessionLoadingScreen()
                            }

                            ShiftSessionState.NoActiveShift -> {
                                val roleState by roleSelectionViewModel.uiState
                                    .collectAsStateWithLifecycle()

                                LaunchedEffect(Unit) {
                                    roleSelectionViewModel.retry()
                                }

                                RoleSelectionScreen(
                                    state = roleState,
                                    onDispatcherSelected =
                                        roleSelectionViewModel::selectDispatcher,
                                    onDriverSelected =
                                        roleSelectionViewModel::selectDriver,
                                    onRetry =
                                        roleSelectionViewModel::retry
                                )
                            }

                            is ShiftSessionState.Active -> {
                                LaunchedEffect(currentSession.userId) {
                                    mainScreenViewModel.loadJobsForUser(
                                        userId = currentSession.userId
                                    )
                                }


                                val mainScreenState by mainScreenViewModel.uiState
                                    .collectAsStateWithLifecycle()
                                RequiredLocationPermissionGate(
                                    locationProvider =
                                        atlasApplication.locationProvider
                                ) { locationState ->
                                    AuthenticatedNavHost(
                                        userName = currentSession.userName,
                                        role = currentShift.session.role,
                                        serverAddress = loginState.serverAddress,
                                        locationState = locationState,
                                        mainScreenState = mainScreenState,
                                        onToggleJobList =
                                            mainScreenViewModel::toggleJobList,
                                        onRetryJobs =
                                            mainScreenViewModel::refresh,
                                        onLogout = loginViewModel::logout
                                    )
                                }
                            }
                        }
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
private fun RoleCheckFailedScreen(
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Could not check your current role.",
            style = MaterialTheme.typography.bodyLarge
        )

        Button(onClick = onRetry) {
            Text(text = "Retry")
        }

        Button(onClick = onLogout) {
            Text(text = "Log out")
        }
    }
}