package org.gtlv.atlas.assign

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.assign.composable.AssignJobMapContent
import org.gtlv.atlas.assign.composable.AssignmentConfirmationDialog
import org.gtlv.atlas.assign.composable.CandidatePanel
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.location.LocationState
import org.gtlv.core.telemetry.LiveMapUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssignJobScreen(
    state: AssignJobUiState,
    serverAddress: String,
    locationState: LocationState,
    liveMapUsers: Collection<LiveMapUser>,
    onBack: () -> Unit,
    onEditAddress: (JobLocationField) -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected: (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    onDueDateChanged: (String) -> Unit,
    onSaveChanges: () -> Unit,
    onRetryCandidates: () -> Unit,
    onRequestAssignment: (JobCandidate) -> Unit,
    onDismissAssignment: () -> Unit,
    onConfirmAssignment: () -> Unit,
    onAssignmentCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
    val landscapeDriverPanelWidth =
        (configuration.screenWidthDp * 0.36f)
            .coerceIn(320f, 420f)
            .dp
    val focusManager = LocalFocusManager.current
    val keyboardController =
        LocalSoftwareKeyboardController.current
    val dismissAddressEditor = remember(
        focusManager,
        keyboardController,
        onCloseAddressEditor
    ) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            onCloseAddressEditor()
        }
    }
    val retryCandidates = remember(
        state.isAddressEditorOpen,
        dismissAddressEditor,
        onRetryCandidates
    ) {
        {
            if (state.isAddressEditorOpen) {
                dismissAddressEditor()
            }
            onRetryCandidates()
        }
    }
    val requestAssignment = remember(
        state.isAddressEditorOpen,
        dismissAddressEditor,
        onRequestAssignment
    ) {
        { candidate: JobCandidate ->
            if (state.isAddressEditorOpen) {
                dismissAddressEditor()
            }
            onRequestAssignment(candidate)
        }
    }

    LaunchedEffect(state.assignmentCompleted) {
        if (state.assignmentCompleted) {
            onAssignmentCompleted()
        }
    }

    BackHandler(
        enabled = state.isAddressEditorOpen
    ) {
        dismissAddressEditor()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(
                                R.string.assign_job_title
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (state.isAddressEditorOpen) {
                                    dismissAddressEditor()
                                } else {
                                    onBack()
                                }
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription =
                                    stringResource(
                                        R.string.assign_job_back
                                    )
                            )
                        }
                    }
                )
            }
        ) { contentPadding ->
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    AssignJobMapContent(
                        state = state,
                        serverAddress = serverAddress,
                        locationState = locationState,
                        liveMapUsers = liveMapUsers,
                        onEditAddress = onEditAddress,
                        onAddressQueryChanged =
                            onAddressQueryChanged,
                        onAddressSuggestionSelected =
                            onAddressSuggestionSelected,
                        onCloseAddressEditor =
                            dismissAddressEditor,
                        onDueDateChanged = onDueDateChanged,
                        onSaveChanges = onSaveChanges,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    Surface(
                        modifier = Modifier
                            .width(landscapeDriverPanelWidth)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(
                            topStart = 28.dp,
                            bottomStart = 28.dp
                        ),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        CandidatePanel(
                            candidates = state.candidates,
                            isLoadingCandidates = state.isLoadingCandidates,
                            candidatesFailed = state.candidatesFailed,
                            allDrivers = state.allDrivers,
                            isLoadingDrivers = state.isLoadingDrivers,
                            driversFailed = state.driversFailed,
                            isActionInProgress = state.isAssigning,
                            onRetry = retryCandidates,
                            onCandidateClick =
                                requestAssignment,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                BottomSheetScaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    containerColor = Color.Transparent,
                    sheetPeekHeight = 184.dp,
                    sheetShape =
                        MaterialTheme.shapes.extraLarge,
                    sheetContainerColor =
                        MaterialTheme.colorScheme.surface,
                    sheetShadowElevation = 6.dp,
                    sheetDragHandle = {
                        BottomSheetDefaults.DragHandle()
                    },
                    sheetContent = {
                        CandidatePanel(
                            candidates = state.candidates,
                            isLoadingCandidates = state.isLoadingCandidates,
                            candidatesFailed = state.candidatesFailed,
                            allDrivers = state.allDrivers,
                            isLoadingDrivers = state.isLoadingDrivers,
                            driversFailed = state.driversFailed,
                            isActionInProgress = state.isAssigning,
                            onRetry = retryCandidates,
                            onCandidateClick =
                                requestAssignment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                        )
                    }
                ) {
                    AssignJobMapContent(
                        state = state,
                        serverAddress = serverAddress,
                        locationState = locationState,
                        liveMapUsers = liveMapUsers,
                        onEditAddress = onEditAddress,
                        onAddressQueryChanged =
                            onAddressQueryChanged,
                        onAddressSuggestionSelected =
                            onAddressSuggestionSelected,
                        onCloseAddressEditor =
                            dismissAddressEditor,
                        onDueDateChanged = onDueDateChanged,
                        onSaveChanges = onSaveChanges,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        state.pendingCandidate?.let { candidate ->
            AssignmentConfirmationDialog(
                candidate = candidate,
                isAssigning = state.isAssigning,
                assignmentFailed = state.assignmentFailed,
                onConfirm = onConfirmAssignment,
                onDismiss = onDismissAssignment
            )
        }
    }
}
