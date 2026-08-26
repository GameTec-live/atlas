package org.gtlv.atlas.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.gtlv.atlas.main.MainScreen
import org.gtlv.atlas.main.MainScreenUiState
import org.gtlv.atlas.notification.JobNotificationUiState
import org.gtlv.atlas.unassigned.UnassignedJobsScreen
import org.gtlv.atlas.unassigned.UnassignedJobsUiState
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.job.Job
import org.gtlv.core.location.LocationState
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.telemetry.LiveMapUser

@Composable
internal fun AuthenticatedNavHost(
    userName: String,
    role: ShiftRole,
    locationState: LocationState,
    liveMapUsers: Collection<LiveMapUser>,
    onLogout: () -> Unit,
    serverAddress: String,
    mainScreenState: MainScreenUiState,
    unassignedJobsState: UnassignedJobsUiState,
    jobNotificationState: JobNotificationUiState,
    onToggleJobList: () -> Unit,
    onRetryJobs: () -> Unit,
    onStartNextJob: () -> Unit,
    onCancelCurrentJob: () -> Unit,
    onPersonCollected: () -> Unit,
    onJobFinished: () -> Unit,
    onNewJobClick: () -> Unit,
    onRefreshUnassignedJobs: () -> Unit,
    onRequestUnassignedJobDeletion: (Job) -> Unit,
    onDismissUnassignedJobDeletion: () -> Unit,
    onConfirmUnassignedJobDeletion: () -> Unit,
    onEditDestination: () -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected:
        (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    onDismissJobNotification: () -> Unit,
    onDeclineJobNotification: () -> Unit,
    onDismissDeclineConfirmation: () -> Unit,
    onConfirmDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = MainDestination,
        modifier = modifier
    ) {
        composable<MainDestination> {
            MainScreen(
                userName = userName,
                role = role,
                serverAddress = serverAddress,
                locationState = locationState,
                liveMapUsers = liveMapUsers,
                jobState = mainScreenState,
                jobNotificationState = jobNotificationState,
                onToggleJobList = onToggleJobList,
                onRetryJobs = onRetryJobs,
                onStartNextJob = onStartNextJob,
                onCancelCurrentJob = onCancelCurrentJob,
                onPersonCollected = onPersonCollected,
                onJobFinished = onJobFinished,
                unassignedJobCount =
                    unassignedJobsState.loadedJobCount,
                onUnassignedJobsClick = {
                    navController.navigate(
                        UnassignedJobsDestination
                    ) {
                        launchSingleTop = true
                    }
                },
                onNewJobClick = onNewJobClick,
                onEditDestination = onEditDestination,
                onAddressQueryChanged = onAddressQueryChanged,
                onAddressSuggestionSelected = onAddressSuggestionSelected,
                onCloseAddressEditor = onCloseAddressEditor,
                onDismissJobNotification = onDismissJobNotification,
                onDeclineJobNotification = onDeclineJobNotification,
                onDismissDeclineConfirmation = onDismissDeclineConfirmation,
                onConfirmDecline = onConfirmDecline,
                onLogout = onLogout
            )
        }

        composable<UnassignedJobsDestination> {
            UnassignedJobsScreen(
                state = unassignedJobsState,
                onBack = {
                    navController.popBackStack()
                },
                onRetry = onRefreshUnassignedJobs,
                onRequestDeletion =
                    onRequestUnassignedJobDeletion,
                onDismissDeletion =
                    onDismissUnassignedJobDeletion,
                onConfirmDeletion =
                    onConfirmUnassignedJobDeletion
            )
        }
    }
}
