package org.gtlv.atlas.newjob

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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
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
import org.gtlv.atlas.assign.composable.CandidatePanel
import org.gtlv.atlas.newjob.composable.CreateJobConfirmationDialog
import org.gtlv.atlas.newjob.composable.NewJobMapContent
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.location.LocationState
import org.gtlv.core.telemetry.LiveMapUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewJobScreen(
    state: NewJobUiState,
    serverAddress: String,
    locationState: LocationState,
    liveMapUsers: Collection<LiveMapUser>,
    onBack: () -> Unit,
    onEditAddress: (JobLocationField) -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected: (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    onDueDateChanged: (String) -> Unit,
    onUnassignedDueDateSelected: (String) -> Unit,
    onDueDatePickerClosed: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onRetryCandidates: () -> Unit,
    onRequestDriverCreation: (JobCandidate) -> Unit,
    onRequestUnassignedCreation: () -> Unit,
    onDismissCreation: () -> Unit,
    onConfirmCreation: () -> Unit,
    onCreationCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
    val landscapeDriverPanelWidth =
        (configuration.screenWidthDp * 0.36f)
            .coerceIn(320f, 420f)
            .dp
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
            dismissAddressEditor()
            onRetryCandidates()
        }
    }
    val requestDriverCreation = remember(
        state.isAddressEditorOpen,
        dismissAddressEditor,
        onRequestDriverCreation
    ) {
        { candidate: JobCandidate ->
            dismissAddressEditor()
            onRequestDriverCreation(candidate)
        }
    }
    val requestUnassignedCreation = remember(
        state.isAddressEditorOpen,
        dismissAddressEditor,
        onRequestUnassignedCreation
    ) {
        {
            dismissAddressEditor()
            onRequestUnassignedCreation()
        }
    }

    LaunchedEffect(state.creationCompleted) {
        if (state.creationCompleted) onCreationCompleted()
    }

    BackHandler {
        if (state.isAddressEditorOpen) {
            dismissAddressEditor()
        } else {
            onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.new_job_title)) },
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
                                contentDescription = stringResource(
                                    R.string.new_job_back
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
                    NewJobMapContent(
                        state = state,
                        serverAddress = serverAddress,
                        locationState = locationState,
                        liveMapUsers = liveMapUsers,
                        onEditAddress = onEditAddress,
                        onAddressQueryChanged = onAddressQueryChanged,
                        onAddressSuggestionSelected =
                            onAddressSuggestionSelected,
                        onCloseAddressEditor = dismissAddressEditor,
                        onDueDateChanged = onDueDateChanged,
                        onUnassignedDueDateSelected =
                            onUnassignedDueDateSelected,
                        onDueDatePickerClosed = onDueDatePickerClosed,
                        onNoteChanged = onNoteChanged,
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
                        NewJobCandidatePanel(
                            state = state,
                            onRetry = retryCandidates,
                            onCandidateClick = requestDriverCreation,
                            onCreateUnassigned = requestUnassignedCreation,
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
                    sheetShape = MaterialTheme.shapes.extraLarge,
                    sheetContainerColor = MaterialTheme.colorScheme.surface,
                    sheetShadowElevation = 6.dp,
                    sheetDragHandle = { BottomSheetDefaults.DragHandle() },
                    sheetContent = {
                        NewJobCandidatePanel(
                            state = state,
                            onRetry = retryCandidates,
                            onCandidateClick = requestDriverCreation,
                            onCreateUnassigned = requestUnassignedCreation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                        )
                    }
                ) {
                    NewJobMapContent(
                        state = state,
                        serverAddress = serverAddress,
                        locationState = locationState,
                        liveMapUsers = liveMapUsers,
                        onEditAddress = onEditAddress,
                        onAddressQueryChanged = onAddressQueryChanged,
                        onAddressSuggestionSelected =
                            onAddressSuggestionSelected,
                        onCloseAddressEditor = dismissAddressEditor,
                        onDueDateChanged = onDueDateChanged,
                        onUnassignedDueDateSelected =
                            onUnassignedDueDateSelected,
                        onDueDatePickerClosed = onDueDatePickerClosed,
                        onNoteChanged = onNoteChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (
            state.pendingCandidate != null ||
            state.isConfirmingUnassigned
        ) {
            CreateJobConfirmationDialog(
                candidate = state.pendingCandidate,
                isCreating = state.isCreating,
                creationFailed = state.creationFailed,
                onConfirm = onConfirmCreation,
                onDismiss = onDismissCreation
            )
        }
    }
}

@Composable
private fun NewJobCandidatePanel(
    state: NewJobUiState,
    onRetry: () -> Unit,
    onCandidateClick: (JobCandidate) -> Unit,
    onCreateUnassigned: () -> Unit,
    modifier: Modifier = Modifier
) {
    CandidatePanel(
        candidates = state.candidates,
        isLoadingCandidates = state.isLoadingCandidates,
        candidatesFailed = state.candidatesFailed,
        allDrivers = state.allDrivers,
        isLoadingDrivers = state.isLoadingDrivers,
        driversFailed = state.driversFailed,
        isActionInProgress = state.isCreating,
        candidateButtonsEnabled = state.canCreate,
        recommendationsEnabled = state.from != null,
        showCreateUnassigned = true,
        canCreateUnassigned = state.canCreate,
        isCreatingUnassigned =
            state.isCreating && state.isConfirmingUnassigned,
        onCreateUnassigned = onCreateUnassigned,
        onRetry = onRetry,
        onCandidateClick = onCandidateClick,
        modifier = modifier
    )
}
