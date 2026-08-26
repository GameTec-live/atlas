package org.gtlv.atlas.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.gtlv.atlas.main.MainScreen
import org.gtlv.atlas.main.MainScreenUiState
import org.gtlv.atlas.assign.AssignJobScreen
import org.gtlv.atlas.assign.AssignJobUiState
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
    assignJobState: AssignJobUiState,
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
    onRemoveUnassignedJob: (String) -> Unit,
    onLoadAssignJob: (Job) -> Unit,
    onEditAssignJobAddress: (org.gtlv.core.job.JobLocationField) -> Unit,
    onAssignJobAddressQueryChanged: (String) -> Unit,
    onAssignJobAddressSelected: (AddressSuggestion) -> Unit,
    onCloseAssignJobAddressEditor: () -> Unit,
    onAssignJobDueDateChanged: (String) -> Unit,
    onSaveAssignJobChanges: () -> Unit,
    onRetryAssignJobCandidates: () -> Unit,
    onRequestJobAssignment: (org.gtlv.core.job.JobCandidate) -> Unit,
    onDismissJobAssignment: () -> Unit,
    onConfirmJobAssignment: () -> Unit,
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
                onJobClick = { job ->
                    navController.navigate(
                        AssignJobDestination(job.id)
                    ) {
                        launchSingleTop = true
                    }
                },
                onRequestDeletion =
                    onRequestUnassignedJobDeletion,
                onDismissDeletion =
                    onDismissUnassignedJobDeletion,
                onConfirmDeletion =
                    onConfirmUnassignedJobDeletion
            )
        }

        composable<AssignJobDestination> { backStackEntry ->
            val destination = backStackEntry
                .toRoute<AssignJobDestination>()
            val job = unassignedJobsState.jobs
                .firstOrNull { listedJob ->
                    listedJob.id == destination.jobId
                }

            LaunchedEffect(job?.id) {
                job?.let(onLoadAssignJob)
            }

            AssignJobScreen(
                state = assignJobState,
                serverAddress = serverAddress,
                locationState = locationState,
                liveMapUsers = liveMapUsers,
                onBack = {
                    onRefreshUnassignedJobs()
                    navController.popBackStack()
                },
                onEditAddress = onEditAssignJobAddress,
                onAddressQueryChanged =
                    onAssignJobAddressQueryChanged,
                onAddressSuggestionSelected =
                    onAssignJobAddressSelected,
                onCloseAddressEditor =
                    onCloseAssignJobAddressEditor,
                onDueDateChanged =
                    onAssignJobDueDateChanged,
                onSaveChanges = onSaveAssignJobChanges,
                onRetryCandidates =
                    onRetryAssignJobCandidates,
                onRequestAssignment =
                    onRequestJobAssignment,
                onDismissAssignment =
                    onDismissJobAssignment,
                onConfirmAssignment =
                    onConfirmJobAssignment,
                onAssignmentCompleted = {
                    onRemoveUnassignedJob(destination.jobId)
                    navController.popBackStack()
                }
            )
        }
    }
}
