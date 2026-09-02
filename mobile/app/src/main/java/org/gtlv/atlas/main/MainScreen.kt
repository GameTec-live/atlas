package org.gtlv.atlas.main

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.address.AddressSearchField
import org.gtlv.atlas.main.composable.AssignedJobDeclineDialog
import org.gtlv.atlas.main.composable.AssignedJobNotificationBanner
import org.gtlv.atlas.main.composable.DispatcherActionButtons
import org.gtlv.atlas.main.composable.CancelJobConfirmationDialog
import org.gtlv.atlas.main.composable.JobActionButtons
import org.gtlv.atlas.main.composable.JobPanel
import org.gtlv.atlas.main.composable.FinishJobConfirmationDialog
import org.gtlv.atlas.main.composable.NavigationPanel
import org.gtlv.atlas.main.composable.ProfileButton
import org.gtlv.atlas.main.composable.ProfileSidebar
import org.gtlv.atlas.main.composable.StartKilometerDialog
import org.gtlv.atlas.map.AtlasMap
import org.gtlv.atlas.map.MapConfiguration
import org.gtlv.atlas.notification.JobNotificationUiState
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.geoservice.RouteProgressCalculator
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.location.LocationState
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.telemetry.LiveMapUser

@Composable
internal fun MainScreen(
    userName: String,
    role: ShiftRole,
    locationState: LocationState,
    liveMapUsers: Collection<LiveMapUser>,
    onLogout: () -> Unit,
    jobState: MainScreenUiState,
    jobNotificationState: JobNotificationUiState,
    onDismissJobNotification: () -> Unit,
    onDeclineJobNotification: () -> Unit,
    onAssignUnassignedNotification: () -> Unit,
    onDismissDeclineConfirmation: () -> Unit,
    onConfirmDecline: () -> Unit,
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
    unassignedJobCount: Int?,
    onUnassignedJobsClick: () -> Unit,
    onNewJobClick: () -> Unit,
    serverAddress: String,
    onEditDestination: () -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected:
        (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape =
        LocalConfiguration.current.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

    if (jobState.isStartKilometerDialogVisible) {
        StartKilometerDialog(
            value = jobState.startKilometerInput,
            isInvalid = jobState.isStartKilometerInputInvalid,
            isSaving = jobState.isStartingNextJob,
            onValueChanged = onStartKilometerChanged,
            onConfirm = onConfirmStartKilometer,
            onDismiss = onDismissStartKilometerDialog
        )
    }

    if (jobState.isCancelConfirmationVisible) {
        CancelJobConfirmationDialog(
            isCancelling =
                jobState.isCancellingCurrentJob,
            onConfirm = onConfirmCancel,
            onDismiss = onDismissCancelConfirmation
        )
    }

    jobState.finishConfirmation?.let { confirmation ->
        FinishJobConfirmationDialog(
            confirmation = confirmation,
            isFinishing =
                jobState.isFinishingCurrentJob,
            onConfirm = onConfirmFinish,
            onDismiss = onDismissFinishConfirmation
        )
    }

    val styleUrl =
        MapConfiguration.createStyleUrl(
            serverAddress = serverAddress
        )

    var isFollowingLocation by rememberSaveable {
        mutableStateOf(true)
    }

    var recenterRequestId by remember {
        mutableIntStateOf(0)
    }

    var landscapeNavigationPanelHeightPixels by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(jobState.navigation.route) {
        val route = jobState.navigation.route
        if (route != null && route.points.size >= 2) {
            isFollowingLocation = true
            recenterRequestId += 1
        }
    }

    var isProfileOpen by rememberSaveable(
        userName
    ) {
        mutableStateOf(false)
    }

    BackHandler(
        enabled =
            isProfileOpen ||
                    (
                            jobState.isAddressEditorOpen &&
                                    !jobState.addressSearch.isSaving
                            )
    ) {
        when {
            isProfileOpen -> {
                isProfileOpen = false
            }

            jobState.isAddressEditorOpen -> {
                onCloseAddressEditor()
            }
        }
    }

    val jobErrorSnackbarHostState = remember {
        SnackbarHostState()
    }

    val jobActionErrorMessage = when {
        jobNotificationState.declineFailed -> {
            stringResource(
                R.string
                    .job_notification_decline_failed
            )
        }

        jobState.startNextJobFailed -> {
            stringResource(
                R.string.job_action_start_failed
            )
        }

        jobState.cancelCurrentJobFailed -> {
            stringResource(
                R.string.job_action_cancel_failed
            )
        }

        jobState.finishCurrentJobFailed -> {
            stringResource(
                R.string.job_action_finish_failed
            )
        }

        else -> null
    }

    LaunchedEffect(
        jobActionErrorMessage
    ) {
        jobActionErrorMessage?.let { message ->
            jobErrorSnackbarHostState
                .showSnackbar(
                    message = message,
                    duration =
                        SnackbarDuration.Short
                )
        }
    }

    val canShowNavigationPanel =
        !jobState.isAddressEditorOpen &&
            jobNotificationState.currentNotification == null
    val hasVisibleNavigationPanel =
        canShowNavigationPanel &&
            jobState.navigation.phase != NavigationPhase.None &&
            jobState.navigation.status != NavigationStatus.Idle
    val showRecenterButton =
        canShowNavigationPanel &&
            locationState is LocationState.Available &&
            !isFollowingLocation
    val useLandscapeNavigationStack =
        isLandscape && hasVisibleNavigationPanel
    val density = LocalDensity.current
    val landscapeCompassTopOffsetPixels =
        if (useLandscapeNavigationStack) {
            if (landscapeNavigationPanelHeightPixels > 0) {
                landscapeNavigationPanelHeightPixels +
                    with(density) {
                        28.dp.roundToPx()
                    }
            } else {
                with(density) {
                    164.dp.roundToPx()
                }
            }
        } else {
            null
        }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AtlasMap(
            locationState = locationState,
            liveMapUsers = liveMapUsers,
            routePoints = jobState.navigation.route
                ?.let { route ->
                    RouteProgressCalculator.remainingRoutePoints(
                        route = route,
                        progress = jobState.navigation.progress
                    )
                }
                .orEmpty(),
            recenterRequestId =
                recenterRequestId,
            isFollowingLocation =
                isFollowingLocation,
            onUserCameraMove = {
                isFollowingLocation = false
            },
            styleUrl = styleUrl,
            landscapeCompassTopOffsetPixels =
                landscapeCompassTopOffsetPixels,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            if (useLandscapeNavigationStack) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = 16.dp,
                            top = 12.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {
                    NavigationPanel(
                        state = jobState.navigation,
                        isExpandable = false,
                        modifier = Modifier
                            .widthIn(max = 340.dp)
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                landscapeNavigationPanelHeightPixels =
                                    size.height
                            }
                    )

                    Box(
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (showRecenterButton) {
                            MapRecenterButton(
                                onClick = {
                                    isFollowingLocation = true
                                    recenterRequestId += 1
                                }
                            )
                        }
                    }

                    JobPanel(
                        state = jobState,
                        onToggleExpanded =
                            onToggleJobList,
                        onRetry = onRetryJobs,
                        onEditDestination =
                            onEditDestination,
                        isExpandable = false
                    )
                }
            } else {
                if (canShowNavigationPanel) {
                    NavigationPanel(
                        state = jobState.navigation,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(
                                horizontal = 80.dp,
                                vertical = 12.dp
                            )
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                    )
                }

                JobPanel(
                    state = jobState,
                    onToggleExpanded =
                        onToggleJobList,
                    onRetry = onRetryJobs,
                    onEditDestination =
                        onEditDestination,
                    isExpandable = !isLandscape,
                    modifier = Modifier
                        .align(
                            Alignment.BottomStart
                        )
                        .padding(
                            start = 8.dp,
                            bottom = 8.dp
                        )
                )

                if (showRecenterButton) {
                    MapRecenterButton(
                        onClick = {
                            isFollowingLocation = true
                            recenterRequestId += 1
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(
                        Alignment.BottomEnd
                    )
                    .padding(
                        end = 8.dp,
                        bottom = 8.dp
                    ),
                horizontalAlignment =
                    Alignment.End,
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                SnackbarHost(
                    hostState =
                        jobErrorSnackbarHostState
                )

                JobActionButtons(
                    hasCurrentJob =
                        jobState.currentJob != null,
                    hasNextJob =
                        jobState.queuedJobs.isNotEmpty(),
                    isStartingNextJob =
                        jobState.isStartingNextJob,
                    isCancellingCurrentJob =
                        jobState.isCancellingCurrentJob,
                    isFinishingCurrentJob =
                        jobState.isFinishingCurrentJob ||
                            jobState.isPreparingFinishConfirmation,
                    isPersonCollected =
                        jobState.isPersonCollected,
                    isPrimaryJobActionEnabled =
                        !jobState.isLoading &&
                                !jobState.isStartingNextJob &&
                                !jobState.isCancellingCurrentJob &&
                                !jobState.isFinishingCurrentJob &&
                                !jobState.isPreparingFinishConfirmation &&
                                jobState.finishConfirmation == null &&
                                !jobState.isCancelConfirmationVisible &&
                                !jobState.addressSearch.isSaving,
                    onNextJobClick =
                        onStartNextJob,
                    onCancelCurrentJobClick =
                        onCancelCurrentJob,
                    onPersonCollectedClick =
                        onPersonCollected,
                    onJobFinishedClick =
                        onJobFinished,
                    additionalActions = {
                        if (role == ShiftRole.DISPATCHER) {
                            DispatcherActionButtons(
                                unassignedJobCount =
                                    unassignedJobCount,
                                onUnassignedJobsClick =
                                    onUnassignedJobsClick,
                                onNewJobClick =
                                    onNewJobClick
                            )
                        }
                    }
                )
            }

            jobNotificationState
                .currentNotification
                ?.let { notification ->
                    val expiration =
                        jobNotificationState
                            .currentNotificationExpiresAtElapsedRealtime

                    if (expiration != null) {
                        AssignedJobNotificationBanner(
                            notification =
                                notification,
                            expiresAtElapsedRealtime =
                                expiration,
                            isDeclining =
                                jobNotificationState
                                    .decliningJobId ==
                                        notification.jobId,
                            onDecline =
                                onDeclineJobNotification,
                            onAssign =
                                onAssignUnassignedNotification,
                            onExpired =
                                onDismissJobNotification,
                            modifier = Modifier
                                .align(
                                    Alignment.TopCenter
                                )
                                .padding(16.dp)
                                .widthIn(
                                    max = 520.dp
                                )
                                .fillMaxWidth()
                        )
                    }
                }

            if (
                !isProfileOpen &&
                !jobState.isAddressEditorOpen &&
                jobNotificationState
                    .currentNotification == null
            ) {
                ProfileButton(
                    userName = userName,
                    onClick = {
                        isProfileOpen = true
                    },
                    modifier = Modifier
                        .align(
                            Alignment.TopEnd
                        )
                        .padding(16.dp)
                )
            }

            if (isProfileOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource =
                                remember {
                                    MutableInteractionSource()
                                },
                            indication = null,
                            onClick = {
                                isProfileOpen = false
                            }
                        )
                )
            }

            AnimatedVisibility(
                visible = isProfileOpen,
                modifier = Modifier.align(
                    Alignment.TopEnd
                ),
                enter = slideInHorizontally(
                    initialOffsetX = { width ->
                        width
                    }
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { width ->
                        width
                    }
                ) + fadeOut()
            ) {
                ProfileSidebar(
                    userName = userName,
                    role = role,
                    onClose = {
                        isProfileOpen = false
                    },
                    onLogout = {
                        isProfileOpen = false
                        onLogout()
                    }
                )
            }

            if (
                jobState.isAddressEditorOpen
            ) {
                val addressLabel = when (
                    jobState.editedLocationField
                ) {
                    JobLocationField.FROM -> {
                        stringResource(
                            R.string
                                .address_search_origin
                        )
                    }

                    JobLocationField.TO,
                    null -> {
                        stringResource(
                            R.string
                                .address_search_destination
                        )
                    }
                }

                AddressSearchField(
                    state =
                        jobState.addressSearch,
                    label = addressLabel,
                    onQueryChanged =
                        onAddressQueryChanged,
                    onSuggestionSelected =
                        onAddressSuggestionSelected,
                    onClose =
                        onCloseAddressEditor,
                    modifier = Modifier
                        .align(
                            Alignment.TopCenter
                        )
                        .padding(
                            horizontal = 16.dp,
                            vertical = 16.dp
                        )
                        .widthIn(
                            max = 600.dp
                        )
                        .fillMaxWidth()
                )
            }
        }

        jobNotificationState
            .declineConfirmation
            ?.let { notification ->
                AssignedJobDeclineDialog(
                    notification = notification,
                    isDeclining =
                        jobNotificationState
                            .decliningJobId ==
                                notification.jobId,
                    onConfirm =
                        onConfirmDecline,
                    onDismiss =
                        onDismissDeclineConfirmation
                )
            }
    }
}

@Composable
private fun MapRecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = stringResource(
                R.string.map_recenter_location
            )
        )
    }
}

fun String.initial(): String {
    return trim()
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "?"
}

fun ShiftRole.displayNameResource(): Int {
    return when (this) {
        ShiftRole.DRIVER ->
            R.string.role_driver

        ShiftRole.DISPATCHER ->
            R.string.role_dispatcher
    }
}
