package org.gtlv.atlas.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import org.gtlv.atlas.assign.composable.AssignJobRouteState
import org.gtlv.atlas.notification.JobNotificationUiState
import org.gtlv.atlas.newjob.NewJobScreen
import org.gtlv.atlas.newjob.NewJobUiState
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
    newJobState: NewJobUiState,
    jobNotificationState: JobNotificationUiState,
    onToggleJobList: () -> Unit,
    onRetryJobs: () -> Unit,
    onStartNextJob: () -> Unit,
    onStartKilometerChanged: (String) -> Unit,
    onDismissStartKilometerDialog: () -> Unit,
    onConfirmStartKilometer: () -> Unit,
    onCancelCurrentJob: () -> Unit,
    onDismissCancelConfirmation: () -> Unit,
    onConfirmCancel: () -> Unit,
    onPersonCollected: () -> Unit,
    onJobFinished: () -> Unit,
    onDismissFinishConfirmation: () -> Unit,
    onConfirmFinish: () -> Unit,
    onLoadNewJob: () -> Unit,
    onClearNewJob: () -> Unit,
    onEditNewJobAddress: (org.gtlv.core.job.JobLocationField) -> Unit,
    onNewJobAddressQueryChanged: (String) -> Unit,
    onNewJobAddressSelected: (AddressSuggestion) -> Unit,
    onCloseNewJobAddressEditor: () -> Unit,
    onNewJobDueDateChanged: (String) -> Unit,
    onUnassignedJobDueDateSelected: (String) -> Unit,
    onCloseUnassignedJobDueDatePicker: () -> Unit,
    onNewJobNoteChanged: (String) -> Unit,
    onRetryNewJobCandidates: () -> Unit,
    onRequestNewJobDriver: (org.gtlv.core.job.JobCandidate) -> Unit,
    onRequestUnassignedJobCreation: () -> Unit,
    onDismissNewJobCreation: () -> Unit,
    onConfirmNewJobCreation: () -> Unit,
    onRefreshUnassignedJobs: () -> Unit,
    onRequestUnassignedJobDeletion: (Job) -> Unit,
    onDismissUnassignedJobDeletion: () -> Unit,
    onConfirmUnassignedJobDeletion: () -> Unit,
    onRemoveUnassignedJob: (String) -> Unit,
    onLoadAssignJob: (Job) -> Unit,
    onClearAssignJob: () -> Unit,
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
    onAssignUnassignedNotification: () -> Unit,
    onAssignmentNavigationHandled: () -> Unit,
    onDismissDeclineConfirmation: () -> Unit,
    onConfirmDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    LaunchedEffect(
        jobNotificationState.assignmentJobId
    ) {
        val jobId =
            jobNotificationState.assignmentJobId
                ?: return@LaunchedEffect

        onRefreshUnassignedJobs()

        navController.navigate(
            AssignJobDestination(jobId)
        ) {
            launchSingleTop = true
        }

        onAssignmentNavigationHandled()
    }

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
                onStartKilometerChanged = onStartKilometerChanged,
                onDismissStartKilometerDialog =
                    onDismissStartKilometerDialog,
                onConfirmStartKilometer = onConfirmStartKilometer,
                onCancelCurrentJob = onCancelCurrentJob,
                onDismissCancelConfirmation =
                    onDismissCancelConfirmation,
                onConfirmCancel = onConfirmCancel,
                onPersonCollected = onPersonCollected,
                onJobFinished = onJobFinished,
                onDismissFinishConfirmation =
                    onDismissFinishConfirmation,
                onConfirmFinish = onConfirmFinish,
                unassignedJobCount =
                    unassignedJobsState.loadedJobCount,
                onUnassignedJobsClick = {
                    navController.navigate(
                        UnassignedJobsDestination
                    ) {
                        launchSingleTop = true
                    }
                },
                onNewJobClick = {
                    navController.navigate(NewJobDestination) {
                        launchSingleTop = true
                    }
                },
                onEditDestination = onEditDestination,
                onAddressQueryChanged = onAddressQueryChanged,
                onAddressSuggestionSelected = onAddressSuggestionSelected,
                onCloseAddressEditor = onCloseAddressEditor,
                onDismissJobNotification = onDismissJobNotification,
                onDeclineJobNotification = onDeclineJobNotification,
                onAssignUnassignedNotification =
                    onAssignUnassignedNotification,
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

        composable<NewJobDestination> {
            LaunchedEffect(Unit) {
                onLoadNewJob()
            }

            NewJobScreen(
                state = newJobState,
                serverAddress = serverAddress,
                locationState = locationState,
                liveMapUsers = liveMapUsers,
                onBack = {
                    onClearNewJob()
                    navController.popBackStack()
                },
                onEditAddress = onEditNewJobAddress,
                onAddressQueryChanged = onNewJobAddressQueryChanged,
                onAddressSuggestionSelected = onNewJobAddressSelected,
                onCloseAddressEditor = onCloseNewJobAddressEditor,
                onDueDateChanged = onNewJobDueDateChanged,
                onUnassignedDueDateSelected =
                    onUnassignedJobDueDateSelected,
                onDueDatePickerClosed =
                    onCloseUnassignedJobDueDatePicker,
                onNoteChanged = onNewJobNoteChanged,
                onRetryCandidates = onRetryNewJobCandidates,
                onRequestDriverCreation = onRequestNewJobDriver,
                onRequestUnassignedCreation =
                    onRequestUnassignedJobCreation,
                onDismissCreation = onDismissNewJobCreation,
                onConfirmCreation = onConfirmNewJobCreation,
                onCreationCompleted = {
                    onClearNewJob()
                    onRefreshUnassignedJobs()
                    onRetryJobs()
                    navController.popBackStack()
                }
            )
        }

        composable<AssignJobDestination> { backStackEntry ->
            val destination = backStackEntry
                .toRoute<AssignJobDestination>()
            val job = unassignedJobsState.jobs
                .firstOrNull { listedJob ->
                    listedJob.id == destination.jobId
                }

            DisposableEffect(destination.jobId) {
                onDispose(onClearAssignJob)
            }

            LaunchedEffect(destination.jobId) {
                if (job == null) {
                    onRefreshUnassignedJobs()
                }
            }

            LaunchedEffect(destination.jobId, job?.id) {
                if (job == null) {
                    onClearAssignJob()
                } else {
                    onLoadAssignJob(job)
                }
            }

            if (
                job != null &&
                assignJobState.job?.id == destination.jobId
            ) {
                AssignJobScreen(
                    state = assignJobState,
                    serverAddress = serverAddress,
                    locationState = locationState,
                    liveMapUsers = liveMapUsers,
                    onBack = {
                        onClearAssignJob()
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
                        onRemoveUnassignedJob(
                            destination.jobId
                        )
                        onClearAssignJob()
                        navController.popBackStack()
                    }
                )
            } else {
                AssignJobRouteState(
                    isLoading =
                        unassignedJobsState.isLoading ||
                                job != null,
                    onBack = {
                        onClearAssignJob()
                        navController.popBackStack()
                    },
                    onRetry = onRefreshUnassignedJobs
                )
            }
        }
    }
}
