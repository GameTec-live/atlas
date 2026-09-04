package org.gtlv.atlas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.gtlv.atlas.auth.LoginScreen
import org.gtlv.atlas.auth.LoginViewModel
import org.gtlv.atlas.auth.LoginViewModelFactory
import org.gtlv.atlas.assign.AssignJobViewModel
import org.gtlv.atlas.assign.AssignJobViewModelFactory
import org.gtlv.atlas.location.RequiredLocationPermissionGate
import org.gtlv.atlas.fleet.PairVehicleDialog
import org.gtlv.atlas.main.MainScreenViewModel
import org.gtlv.atlas.main.MainScreenViewModelFactory
import org.gtlv.atlas.navigation.AuthenticatedNavHost
import org.gtlv.atlas.notification.JobNotificationViewModel
import org.gtlv.atlas.notification.JobNotificationViewModelFactory
import org.gtlv.atlas.notification.JobSystemNotificationManager
import org.gtlv.atlas.offboarding.composable.EndKilometerDialog
import org.gtlv.atlas.offboarding.OffboardingScreen
import org.gtlv.atlas.offboarding.OffboardingViewModel
import org.gtlv.atlas.offboarding.OffboardingViewModelFactory
import org.gtlv.atlas.newjob.NewJobViewModel
import org.gtlv.atlas.newjob.NewJobViewModelFactory
import org.gtlv.atlas.role.RoleSelectionScreen
import org.gtlv.atlas.role.RoleSelectionViewModel
import org.gtlv.atlas.role.RoleSelectionViewModelFactory
import org.gtlv.atlas.service.ActiveShiftService
import org.gtlv.atlas.ui.theme.AtlasTheme
import org.gtlv.atlas.unassigned.UnassignedJobsViewModel
import org.gtlv.atlas.unassigned.UnassignedJobsViewModelFactory
import org.gtlv.core.session.SessionState
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.shift.ShiftSessionState
import org.gtlv.core.fleet.ConnectedVehicleState

class MainActivity : ComponentActivity() {

    private val atlasApplication by lazy {
        application as AtlasApplication
    }

    private val mainScreenViewModel:
            MainScreenViewModel by viewModels {
        MainScreenViewModelFactory(
            jobRepository = atlasApplication.jobRepository,
            geoServiceRepository = atlasApplication.geoServiceRepository,
            telemetryProvider = atlasApplication.telemetryProvider,
            collectedJobStore = atlasApplication.collectedJobStore,
            jobMileageStore = atlasApplication.jobMileageStore,
            pricingRepository = atlasApplication.pricingRepository,
            shiftSessionManager = atlasApplication.shiftSessionManager
        )
    }

    private val jobNotificationViewModel:
            JobNotificationViewModel by viewModels {
        JobNotificationViewModelFactory(
            jobRepository = atlasApplication.jobRepository,
            notificationSync = atlasApplication.jobNotificationSync
        )
    }

    private val offboardingViewModel:
            OffboardingViewModel by viewModels {
        OffboardingViewModelFactory(
            shiftSessionManager = atlasApplication.shiftSessionManager,
            telemetryProvider = atlasApplication.telemetryProvider,
            connectedVehicleState =
                atlasApplication.connectedVehicleManager.state,
            fleetRepository = atlasApplication.fleetRepository,
            logbookRepository = atlasApplication.logbookRepository,
            sessionManager = atlasApplication.sessionManager
        )
    }

    private val unassignedJobsViewModel:
            UnassignedJobsViewModel by viewModels {
        UnassignedJobsViewModelFactory(
            jobRepository = atlasApplication.jobRepository
        )
    }

    private val assignJobViewModel:
            AssignJobViewModel by viewModels {
        AssignJobViewModelFactory(
            jobRepository = atlasApplication.jobRepository,
            geoServiceRepository =
                atlasApplication.geoServiceRepository,
            roleRepository = atlasApplication.roleRepository
        )
    }

    private val newJobViewModel:
            NewJobViewModel by viewModels {
        NewJobViewModelFactory(
            jobRepository = atlasApplication.jobRepository,
            geoServiceRepository =
                atlasApplication.geoServiceRepository,
            roleRepository = atlasApplication.roleRepository
        )
    }

    private val sessionManager
        get() = atlasApplication.sessionManager

    private val shiftSessionManager
        get() = atlasApplication.shiftSessionManager

    private val loginViewModel:
            LoginViewModel by viewModels {
        LoginViewModelFactory(
            sessionManager =
                atlasApplication.sessionManager,
            serverSettingsRepository =
                atlasApplication
                    .serverSettingsRepository
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

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        lifecycleScope.launch {
            handleAtlasUrlIntent(intent)
            handleJobNotificationIntent(intent)
            showAppContent()
        }
    }

    private fun showAppContent() {
        setContent {
            AtlasTheme {
                val coroutineScope =
                    rememberCoroutineScope()

                val loginState by
                loginViewModel.uiState
                    .collectAsStateWithLifecycle()

                val sessionState by
                sessionManager.state
                    .collectAsStateWithLifecycle()

                val connectedVehicleState by
                    atlasApplication.connectedVehicleManager.state
                        .collectAsStateWithLifecycle()

                val offboardingState by
                    offboardingViewModel.uiState
                        .collectAsStateWithLifecycle()

                val jobNotificationState by
                jobNotificationViewModel.uiState
                    .collectAsStateWithLifecycle()

                val navigationLanguage = stringResource(
                    R.string.navigation_route_language
                )

                LaunchedEffect(navigationLanguage) {
                    mainScreenViewModel
                        .updateNavigationLanguage(
                            navigationLanguage
                        )
                }

                LaunchedEffect(sessionState) {
                    when (sessionState) {
                        SessionState.SignedOut -> {
                            mainScreenViewModel
                                .clearJobs()

                            unassignedJobsViewModel
                                .clear()

                            assignJobViewModel.clear()

                            newJobViewModel.clear()

                            jobNotificationViewModel
                                .clear()

                            ActiveShiftService.stop(
                                this@MainActivity
                            )
                        }

                        is SessionState.RoleCheckFailed -> {
                            ActiveShiftService.stop(
                                this@MainActivity
                            )
                        }

                        else -> Unit
                    }
                }

                LaunchedEffect(Unit) {
                    jobNotificationViewModel
                        .refreshRequests
                        .collect {
                            mainScreenViewModel
                                .refresh()
                        }
                }

                LaunchedEffect(Unit) {
                    jobNotificationViewModel
                        .unassignedRefreshRequests
                        .collect {
                            unassignedJobsViewModel
                                .refresh()
                        }
                }

                when (
                    val currentSession =
                        sessionState
                ) {
                    SessionState.Checking -> {
                        SessionLoadingScreen()
                    }

                    SessionState.SignedOut -> {
                        LoginScreen(
                            state = loginState,
                            onUsernameChanged = loginViewModel::onUsernameChanged,
                            onPasswordChanged = loginViewModel::onPasswordChanged,
                            onPasswordVisibilityChanged = loginViewModel::togglePasswordVisibility,
                            onLogin = loginViewModel::login,
                            onEditServer = loginViewModel::openServerDialog,
                            onServerAddressChanged = loginViewModel::onServerAddressChanged,
                            onSaveServerAddress = loginViewModel::saveServerAddress,
                            onDismissServerDialog = loginViewModel::closeServerDialog
                        )
                    }

                    is SessionState.RoleCheckFailed -> {
                        RoleCheckFailedScreen(
                            onRetry = {
                                coroutineScope.launch {
                                    sessionManager
                                        .retryRoleCheck()
                                }
                            },
                            onLogout =
                                loginViewModel::logout
                        )
                    }

                    is SessionState.SignedIn -> {
                        val shiftState by
                        shiftSessionManager.state
                            .collectAsStateWithLifecycle()

                        when (
                            val currentShift =
                                shiftState
                        ) {
                            ShiftSessionState.Loading -> {
                                SessionLoadingScreen()
                            }

                            ShiftSessionState
                                .NoActiveShift -> {
                                val roleState by
                                roleSelectionViewModel
                                    .uiState
                                    .collectAsStateWithLifecycle()

                                LaunchedEffect(Unit) {
                                    ActiveShiftService.stop(
                                        this@MainActivity
                                    )

                                    roleSelectionViewModel
                                        .retry()
                                }

                                RoleSelectionScreen(
                                    state = roleState,
                                    onDispatcherSelected =
                                        roleSelectionViewModel::
                                        selectDispatcher,
                                    onDriverSelected =
                                        roleSelectionViewModel::
                                        selectDriver,
                                    onRetry =
                                        roleSelectionViewModel::
                                        retry
                                )
                            }

                            is ShiftSessionState.Active -> {
                                LaunchedEffect(
                                    currentSession.userId
                                ) {
                                    mainScreenViewModel
                                        .loadJobsForUser(
                                            userId =
                                                currentSession
                                                    .userId
                                        )
                                }

                                LaunchedEffect(
                                    currentSession.userId,
                                    currentShift.session.role
                                ) {
                                    if (
                                        currentShift.session.role ==
                                        ShiftRole.DISPATCHER
                                    ) {
                                        unassignedJobsViewModel
                                            .refresh()
                                    } else {
                                        unassignedJobsViewModel
                                            .clear()
                                    }
                                }

                                val mainScreenState by
                                mainScreenViewModel
                                    .uiState
                                    .collectAsStateWithLifecycle()

                                val unassignedJobsState by
                                unassignedJobsViewModel
                                    .uiState
                                    .collectAsStateWithLifecycle()

                                val assignJobState by
                                assignJobViewModel
                                    .uiState
                                    .collectAsStateWithLifecycle()

                                val newJobState by
                                newJobViewModel
                                    .uiState
                                    .collectAsStateWithLifecycle()

                                val liveMapUsers by
                                atlasApplication
                                    .telemetryWebSocketSender
                                    .liveMapUsers
                                    .collectAsStateWithLifecycle()

                                val otherMapUsers =
                                    liveMapUsers
                                        .values
                                        .filterNot {
                                                mapUser ->
                                            mapUser.userId ==
                                                    currentSession
                                                        .userId
                                        }

                                if (
                                    offboardingState
                                        .isEndKilometerDialogVisible
                                ) {
                                    EndKilometerDialog(
                                        value = offboardingState
                                            .endKilometerInput,
                                        isInvalid = offboardingState
                                            .isEndKilometerInvalid,
                                        onValueChanged =
                                            offboardingViewModel::
                                            updateEndKilometerInput,
                                        onConfirm =
                                            offboardingViewModel::
                                            confirmEndKilometer,
                                        onDismiss =
                                            offboardingViewModel::
                                            dismissEndKilometerDialog
                                    )
                                }

                                if (offboardingState.isVisible) {
                                    OffboardingScreen(
                                        state = offboardingState,
                                        onRevenueChanged =
                                            offboardingViewModel::
                                            updateRevenue,
                                        onConfirmationChanged =
                                            offboardingViewModel::
                                            setConfirmed,
                                        onVehicleSelected =
                                            offboardingViewModel::
                                            selectVehicle,
                                        onRetryVehicles =
                                            offboardingViewModel::
                                            retryLoadVehicles,
                                        onSubmit =
                                            offboardingViewModel::
                                            submitAndLogout,
                                        onBack =
                                            offboardingViewModel::
                                            cancelOffboarding
                                    )
                                } else {
                                    RequiredLocationPermissionGate(
                                    locationProvider =
                                        atlasApplication
                                            .locationProvider
                                ) { locationState ->
                                    LaunchedEffect(locationState) {
                                        mainScreenViewModel
                                            .updateLocationState(
                                                locationState
                                            )
                                    }

                                    LaunchedEffect(Unit) {
                                        ActiveShiftService.start(
                                            this@MainActivity
                                        )
                                    }

                                    AssignedJobNotificationPermissionEffect()

                                    AuthenticatedNavHost(
                                        userName = currentSession.userName,
                                        role = currentShift.session.role,
                                        serverAddress = loginState.serverAddress,
                                        locationState = locationState,
                                        liveMapUsers = otherMapUsers,
                                        mainScreenState = mainScreenState,
                                        unassignedJobsState = unassignedJobsState,
                                        assignJobState = assignJobState,
                                        newJobState = newJobState,
                                        onToggleJobList = mainScreenViewModel::toggleJobList,
                                        onRetryJobs = mainScreenViewModel::refresh,
                                        onStartNextJob = mainScreenViewModel::startNextJob,
                                        onStartKilometerChanged = mainScreenViewModel::updateStartKilometerInput,
                                        onDismissStartKilometerDialog = mainScreenViewModel::dismissStartKilometerDialog,
                                        onConfirmStartKilometer = mainScreenViewModel::confirmStartKilometer,
                                        onCancelCurrentJob = mainScreenViewModel::requestCancelCurrentJob,
                                        onDismissCancelConfirmation = mainScreenViewModel::dismissCancelConfirmation,
                                        onConfirmCancel = mainScreenViewModel::confirmCancelCurrentJob,
                                        onPersonCollected = mainScreenViewModel::personCollected,
                                        onJobFinished = mainScreenViewModel::requestFinishCurrentJob,
                                        onDismissFinishConfirmation = mainScreenViewModel::dismissFinishConfirmation,
                                        onConfirmFinish = mainScreenViewModel::confirmFinishCurrentJob,
                                        onLoadNewJob = newJobViewModel::load,
                                        onClearNewJob = newJobViewModel::clear,
                                        onEditNewJobAddress = newJobViewModel::openAddressEditor,
                                        onNewJobAddressQueryChanged = newJobViewModel::onAddressQueryChanged,
                                        onNewJobAddressSelected = newJobViewModel::selectAddressSuggestion,
                                        onCloseNewJobAddressEditor = newJobViewModel::closeAddressEditor,
                                        onNewJobDueDateChanged = newJobViewModel::updateDueDate,
                                        onUnassignedJobDueDateSelected = newJobViewModel::confirmUnassignedDueDate,
                                        onCloseUnassignedJobDueDatePicker = newJobViewModel::dismissUnassignedDueDatePicker,
                                        onNewJobNoteChanged = newJobViewModel::updateNote,
                                        onRetryNewJobCandidates = newJobViewModel::retryCandidates,
                                        onRequestNewJobDriver = newJobViewModel::requestDriverCreation,
                                        onRequestUnassignedJobCreation = newJobViewModel::requestUnassignedCreation,
                                        onDismissNewJobCreation = newJobViewModel::dismissCreation,
                                        onConfirmNewJobCreation = newJobViewModel::confirmCreation,
                                        onRefreshUnassignedJobs = unassignedJobsViewModel::refresh,
                                        onRequestUnassignedJobDeletion = unassignedJobsViewModel::requestDeletion,
                                        onDismissUnassignedJobDeletion = unassignedJobsViewModel::dismissDeletion,
                                        onConfirmUnassignedJobDeletion = unassignedJobsViewModel::confirmDeletion,
                                        onRemoveUnassignedJob = unassignedJobsViewModel::removeJob,
                                        onLoadAssignJob = assignJobViewModel::load,
                                        onClearAssignJob = assignJobViewModel::clear,
                                        onEditAssignJobAddress = assignJobViewModel::openAddressEditor,
                                        onAssignJobAddressQueryChanged = assignJobViewModel::onAddressQueryChanged,
                                        onAssignJobAddressSelected = assignJobViewModel::selectAddressSuggestion,
                                        onCloseAssignJobAddressEditor = assignJobViewModel::closeAddressEditor,
                                        onAssignJobDueDateChanged = assignJobViewModel::updateDueDate,
                                        onSaveAssignJobChanges = assignJobViewModel::saveChanges,
                                        onRetryAssignJobCandidates = assignJobViewModel::retryCandidates,
                                        onRequestJobAssignment = assignJobViewModel::requestAssignment,
                                        onDismissJobAssignment = assignJobViewModel::dismissAssignment,
                                        onConfirmJobAssignment = assignJobViewModel::confirmAssignment,
                                        onEditDestination = mainScreenViewModel::openDestinationEditor,
                                        onAddressQueryChanged = mainScreenViewModel::onAddressQueryChanged,
                                        onAddressSuggestionSelected = mainScreenViewModel::selectAddressSuggestion,
                                        onCloseAddressEditor = mainScreenViewModel::closeAddressEditor,
                                        jobNotificationState = jobNotificationState,
                                        onDismissJobNotification = jobNotificationViewModel::dismissCurrentNotification,
                                        onDeclineJobNotification = jobNotificationViewModel::declineCurrentNotification,
                                        onAssignUnassignedNotification = jobNotificationViewModel::assignCurrentNotification,
                                        onAssignmentNavigationHandled = jobNotificationViewModel::assignmentNavigationHandled,
                                        onDismissDeclineConfirmation = jobNotificationViewModel::dismissDeclineConfirmation,
                                        onConfirmDecline = jobNotificationViewModel::confirmDecline,
                                        onLogout =
                                            offboardingViewModel::requestLogout
                                    )
                                }
                                }
                            }
                        }
                    }
                }

                (connectedVehicleState as? ConnectedVehicleState.PairingRequired)
                    ?.let { pairingState ->
                        PairVehicleDialog(
                            state = pairingState,
                            onVehicleSelected = { vehicleId ->
                                coroutineScope.launch {
                                    atlasApplication.connectedVehicleManager
                                        .pair(vehicleId)
                                }
                            },
                            onRetry = {
                                coroutineScope.launch {
                                    atlasApplication.connectedVehicleManager
                                        .retryPairingCandidates()
                                }
                            },
                            onDismiss = atlasApplication
                                .connectedVehicleManager::dismissPairing
                        )
                    }
            }
        }
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)
        lifecycleScope.launch {
            handleAtlasUrlIntent(intent)
            handleJobNotificationIntent(intent)
        }
    }

    private suspend fun handleAtlasUrlIntent(
        intent: Intent?
    ) {
        if (
            intent?.action != Intent.ACTION_VIEW ||
            !intent.data?.scheme.equals(
                other = "atlas",
                ignoreCase = true
            )
        ) {
            return
        }

        val serverAddress =
            AtlasUrlProtocol.serverAddressFrom(
                intent.dataString
            )

        // Consume the deep link so an activity recreation cannot apply it
        // and show the result a second time.
        intent.action = null
        intent.data = null

        if (serverAddress == null) {
            Toast.makeText(
                applicationContext,
                R.string.server_address_error,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val currentAddress =
                atlasApplication
                    .serverSettingsRepository
                    .serverAddress
                    .first()

            if (serverAddress != currentAddress) {
                sessionManager.logout()
                atlasApplication
                    .serverSettingsRepository
                    .setServerAddress(serverAddress)
            }

            Toast.makeText(
                applicationContext,
                R.string.url_set_successfully,
                Toast.LENGTH_SHORT
            ).show()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Toast.makeText(
                applicationContext,
                R.string.url_set_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleJobNotificationIntent(
        intent: Intent?
    ) {
        val assignmentJobId =
            JobSystemNotificationManager
                .assignmentJobIdFromIntent(intent)

        if (assignmentJobId != null) {
            jobNotificationViewModel
                .requestAssignment(
                    assignmentJobId
                )

            intent?.action = null
            return
        }

        val notification =
            JobSystemNotificationManager
                .notificationFromIntent(
                    intent ?: return
                )
                ?: return

        jobNotificationViewModel
            .requestDeclineConfirmation(
                notification
            )

        intent.action = null
    }
}

@Composable
private fun AssignedJobNotificationPermissionEffect() {
    if (
        Build.VERSION.SDK_INT <
        Build.VERSION_CODES.TIRAMISU
    ) {
        return
    }

    val context = LocalContext.current

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission()
        ) {
            // The app continues working if denied.
        }

    LaunchedEffect(Unit) {
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission
                    .POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            permissionLauncher.launch(
                Manifest.permission
                    .POST_NOTIFICATIONS
            )
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
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {
        Text(
            text = stringResource(
                R.string.role_check_failed
            ),
            style =
                MaterialTheme.typography.bodyLarge
        )

        Button(
            onClick = onRetry
        ) {
            Text(text = stringResource(R.string.role_retry))
        }

        Button(
            onClick = onLogout
        ) {
            Text(text = stringResource(R.string.logout))
        }
    }
}
