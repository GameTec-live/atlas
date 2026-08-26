package org.gtlv.atlas.assign

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.assign.composable.AddressFields
import org.gtlv.atlas.assign.composable.AssignmentConfirmationDialog
import org.gtlv.atlas.assign.composable.CandidatePanel
import org.gtlv.atlas.map.AtlasMap
import org.gtlv.atlas.map.MapConfiguration
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
            BottomSheetScaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                containerColor = Color.Transparent,
                sheetPeekHeight = 184.dp,
                sheetShape = MaterialTheme.shapes.extraLarge,
                sheetContainerColor =
                    MaterialTheme.colorScheme.surface,
                sheetShadowElevation = 6.dp,
                sheetDragHandle = {
                    BottomSheetDefaults.DragHandle()
                },
                sheetContent = {
                    CandidatePanel(
                        state = state,
                        onRetry = retryCandidates,
                        onCandidateClick = requestAssignment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    )
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AtlasMap(
                        locationState = locationState,
                        liveMapUsers = liveMapUsers,
                        routePoints = state.route
                            ?.points
                            .orEmpty(),
                        showRouteEndpoints = true,
                        recenterRequestId = 0,
                        isFollowingLocation = false,
                        onUserCameraMove = {
                            if (state.isAddressEditorOpen) {
                                dismissAddressEditor()
                            }
                        },
                        onMapClick = {
                            if (state.isAddressEditorOpen) {
                                dismissAddressEditor()
                            }
                        },
                        styleUrl =
                            MapConfiguration.createStyleUrl(
                                serverAddress
                            ),
                        cameraFocusPoints =
                            state.cameraFocusPoints,
                        cameraFocusRequestId =
                            state.cameraFocusRequestId,
                        cameraFocusPadding = 112.dp,
                        modifier = Modifier.fillMaxSize()
                    )

                    state.job?.let { job ->
                        AddressFields(
                            state = state,
                            job = job,
                            enabled =
                                !state.isAssigning &&
                                        !state.isSavingChanges &&
                                        !state.addressSearch.isSaving,
                            onEditAddress = onEditAddress,
                            onAddressQueryChanged =
                                onAddressQueryChanged,
                            onAddressSuggestionSelected =
                                onAddressSuggestionSelected,
                            onCloseAddressEditor =
                                dismissAddressEditor,
                            onDueDateChanged =
                                onDueDateChanged,
                            onSaveChanges = onSaveChanges,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                        )
                    }
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
