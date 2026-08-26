package org.gtlv.atlas.assign

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.gtlv.atlas.R
import org.gtlv.atlas.address.AddressSearchField
import org.gtlv.atlas.address.AddressSearchPresentation
import org.gtlv.atlas.map.AtlasMap
import org.gtlv.atlas.map.MapConfiguration
import org.gtlv.atlas.unassigned.formatDueDate
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobCoordinates
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
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
                    styleUrl = MapConfiguration.createStyleUrl(
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
                        onDueDateChanged = onDueDateChanged,
                        onSaveChanges = onSaveChanges,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                    )
                }

                CandidatePanel(
                    state = state,
                    onRetry = retryCandidates,
                    onCandidateClick = requestAssignment,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .height(184.dp)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )

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

@Composable
private fun AddressFields(
    state: AssignJobUiState,
    job: Job,
    enabled: Boolean,
    onEditAddress: (JobLocationField) -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected: (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    onDueDateChanged: (String) -> Unit,
    onSaveChanges: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = 0.86f
        ),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.unassigned_jobs_from
                    ),
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .width(36.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme
                        .onSurfaceVariant
                )

                Text(
                    text = job.fromAddress
                        ?: job.from?.displayValue()
                        ?: stringResource(
                            R.string
                                .unassigned_jobs_unknown_location
                        ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                DueDateButton(
                    dueDate = job.dueDate,
                    enabled = enabled,
                    onOpen = {
                        if (state.isAddressEditorOpen) {
                            onCloseAddressEditor()
                        }
                    },
                    onDueDateChanged = onDueDateChanged
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.unassigned_jobs_to
                    ),
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .width(36.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme
                        .onSurfaceVariant
                )

                if (
                    state.isAddressEditorOpen &&
                    state.editedLocationField ==
                    JobLocationField.TO
                ) {
                    AddressSearchField(
                        state = state.addressSearch,
                        label = stringResource(
                            R.string
                                .assign_job_destination_placeholder
                        ),
                        onQueryChanged = onAddressQueryChanged,
                        onSuggestionSelected =
                            onAddressSuggestionSelected,
                        onClose = onCloseAddressEditor,
                        presentation =
                            AddressSearchPresentation.INLINE,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                    )
                } else {
                    Text(
                        text = job.toAddress
                            ?: job.to?.displayValue()
                            ?: stringResource(
                                R.string
                                    .assign_job_destination_placeholder
                            ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                enabled = enabled,
                                onClick = {
                                    onEditAddress(
                                        JobLocationField.TO
                                    )
                                }
                            )
                            .padding(
                                horizontal = 6.dp,
                                vertical = 16.dp
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (job.to == null) {
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = {
                        if (state.isAddressEditorOpen) {
                            onCloseAddressEditor()
                        }
                        onSaveChanges()
                    },
                    enabled =
                        state.hasUnsavedChanges &&
                                !state.isSavingChanges &&
                                !state.isAssigning,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .defaultMinSize(
                            minWidth = 0.dp,
                            minHeight = 36.dp
                        ),
                    contentPadding = PaddingValues(
                        horizontal = 10.dp,
                        vertical = 0.dp
                    )
                ) {
                    if (state.isSavingChanges) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = stringResource(
                            if (state.isSavingChanges) {
                                R.string.assign_job_saving_changes
                            } else {
                                R.string.assign_job_save_changes
                            }
                        ),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            if (state.saveChangesFailed) {
                Text(
                    text = stringResource(
                        R.string.assign_job_save_changes_failed
                    ),
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 8.dp
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDateButton(
    dueDate: String?,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDueDateChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val zoneId = ZoneId.systemDefault()
    val currentDateTime = dueDate
        ?.let { value ->
            runCatching {
                Instant.parse(value).atZone(zoneId)
            }.getOrNull()
        }
        ?: ZonedDateTime.now(zoneId)
    var pickerStep by remember {
        mutableIntStateOf(DUE_DATE_PICKER_CLOSED)
    }
    var selectedDateEpochDay by remember {
        mutableLongStateOf(
            currentDateTime.toLocalDate().toEpochDay()
        )
    }

    TextButton(
        onClick = {
            onOpen()
            selectedDateEpochDay = currentDateTime
                .toLocalDate()
                .toEpochDay()
            pickerStep = DUE_DATE_PICKER_DATE
        },
        enabled = enabled,
        modifier = Modifier
            .defaultMinSize(minWidth = 0.dp)
            .padding(end = 4.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(17.dp)
        )

        Text(
            text = dueDate?.let(::formatDueDate)
                ?: stringResource(
                    R.string.assign_job_set_due_date
                ),
            modifier = Modifier.padding(start = 5.dp),
            maxLines = 1
        )
    }

    if (pickerStep == DUE_DATE_PICKER_DATE) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis =
                selectedDateEpochDay * MILLIS_PER_DAY
        )

        DatePickerDialog(
            onDismissRequest = {
                pickerStep = DUE_DATE_PICKER_CLOSED
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis
                            ?.let { selectedMillis ->
                                selectedDateEpochDay =
                                    selectedMillis /
                                    MILLIS_PER_DAY
                            }
                        pickerStep = DUE_DATE_PICKER_TIME
                    }
                ) {
                    Text(
                        text = stringResource(
                            R.string.assign_job_date_next
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pickerStep =
                            DUE_DATE_PICKER_CLOSED
                    }
                ) {
                    Text(
                        text = stringResource(
                            R.string.button_cancel
                        )
                    )
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (pickerStep == DUE_DATE_PICKER_TIME) {
        val timePickerState = rememberTimePickerState(
            initialHour = currentDateTime.hour,
            initialMinute = currentDateTime.minute,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        var timePickerMode by remember {
            mutableIntStateOf(TIME_PICKER_MODE_DIAL)
        }

        BasicAlertDialog(
            onDismissRequest = {
                pickerStep = DUE_DATE_PICKER_CLOSED
            }
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.assign_job_select_time
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (
                            timePickerMode ==
                            TIME_PICKER_MODE_DIAL
                        ) {
                            TimePicker(state = timePickerState)
                        } else {
                            TimeInput(state = timePickerState)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                timePickerMode =
                                    if (
                                        timePickerMode ==
                                        TIME_PICKER_MODE_DIAL
                                    ) {
                                        TIME_PICKER_MODE_INPUT
                                    } else {
                                        TIME_PICKER_MODE_DIAL
                                    }
                            }
                        ) {
                            Icon(
                                imageVector =
                                    if (
                                        timePickerMode ==
                                        TIME_PICKER_MODE_DIAL
                                    ) {
                                        Icons.Default.Keyboard
                                    } else {
                                        Icons.Default.Schedule
                                    },
                                contentDescription =
                                    stringResource(
                                        if (
                                            timePickerMode ==
                                            TIME_PICKER_MODE_DIAL
                                        ) {
                                            R.string
                                                .assign_job_use_time_input
                                        } else {
                                            R.string
                                                .assign_job_use_time_dial
                                        }
                                    )
                            )
                        }

                        Box(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                pickerStep =
                                    DUE_DATE_PICKER_CLOSED
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.button_cancel
                                )
                            )
                        }

                        TextButton(
                            onClick = {
                                val selectedDate = LocalDate
                                    .ofEpochDay(
                                        selectedDateEpochDay
                                    )
                                val selectedTime = LocalTime.of(
                                    timePickerState.hour,
                                    timePickerState.minute
                                )

                                onDueDateChanged(
                                    ZonedDateTime.of(
                                        selectedDate,
                                        selectedTime,
                                        zoneId
                                    ).toInstant().toString()
                                )
                                pickerStep =
                                    DUE_DATE_PICKER_CLOSED
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    R.string
                                        .assign_job_date_time_confirm
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val DUE_DATE_PICKER_CLOSED = 0
private const val DUE_DATE_PICKER_DATE = 1
private const val DUE_DATE_PICKER_TIME = 2
private const val TIME_PICKER_MODE_DIAL = 0
private const val TIME_PICKER_MODE_INPUT = 1
private const val MILLIS_PER_DAY = 86_400_000L

@Composable
private fun CandidatePanel(
    state: AssignJobUiState,
    onRetry: () -> Unit,
    onCandidateClick: (JobCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val otherDrivers = remember(
        state.candidates,
        state.allDrivers
    ) {
        val recommendedIds = state.candidates
            .mapTo(mutableSetOf()) { candidate ->
                candidate.driverId
            }

        state.allDrivers.filterNot { driver ->
            driver.driverId in recommendedIds
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.assign_job_candidates
                ),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    state.isLoadingCandidates &&
                            state.candidates.isEmpty() -> {
                        item(key = "recommended-loading") {
                            LoadingDriversIndicator()
                        }
                    }

                    state.candidatesFailed -> {
                        item(key = "recommended-error") {
                            DriversError(
                                message = stringResource(
                                    R.string
                                        .assign_job_candidates_error
                                ),
                                onRetry = onRetry
                            )
                        }
                    }

                    state.candidates.isEmpty() -> {
                        item(key = "recommended-empty") {
                            Text(
                                text = stringResource(
                                    R.string
                                        .assign_job_no_candidates
                                ),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    else -> {
                        items(
                            items = state.candidates,
                            key = { candidate ->
                                "recommended:${candidate.driverId}"
                            }
                        ) { candidate ->
                            DriverButton(
                                candidate = candidate,
                                recommended = true,
                                enabled = !state.isAssigning,
                                onClick = {
                                    onCandidateClick(candidate)
                                }
                            )
                        }
                    }
                }

                item(key = "drivers-divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                item(key = "drivers-heading") {
                    Text(
                        text = stringResource(
                            R.string.assign_job_all_drivers
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                when {
                    state.isLoadingDrivers &&
                            state.allDrivers.isEmpty() -> {
                        item(key = "drivers-loading") {
                            LoadingDriversIndicator()
                        }
                    }

                    state.driversFailed -> {
                        item(key = "drivers-error") {
                            DriversError(
                                message = stringResource(
                                    R.string
                                        .assign_job_drivers_error
                                ),
                                onRetry = onRetry
                            )
                        }
                    }

                    otherDrivers.isEmpty() -> {
                        item(key = "drivers-empty") {
                            Text(
                                text = stringResource(
                                    R.string
                                        .assign_job_no_other_drivers
                                ),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    else -> {
                        items(
                            items = otherDrivers,
                            key = { driver ->
                                "driver:${driver.driverId}"
                            }
                        ) { driver ->
                            DriverButton(
                                candidate = driver,
                                recommended = false,
                                enabled = !state.isAssigning,
                                onClick = {
                                    onCandidateClick(driver)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverButton(
    candidate: JobCandidate,
    recommended: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (recommended) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            DriverButtonContent(
                candidate = candidate,
                showRank = true
            )
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            DriverButtonContent(
                candidate = candidate,
                showRank = false
            )
        }
    }
}

@Composable
private fun DriverButtonContent(
    candidate: JobCandidate,
    showRank: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = candidate.driverName,
            style = MaterialTheme.typography.titleMedium
        )

        if (showRank) {
            Text(
                text = stringResource(
                    R.string.assign_job_recommended_rank,
                    candidate.rank
                ),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun LoadingDriversIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DriversError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = message)

        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(
                    R.string.unassigned_jobs_retry
                )
            )
        }
    }
}

@Composable
private fun AssignmentConfirmationDialog(
    candidate: JobCandidate,
    isAssigning: Boolean,
    assignmentFailed: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isAssigning) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(
                    R.string.assign_job_confirm_title
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.assign_job_confirm_message,
                        candidate.driverName
                    )
                )

                if (assignmentFailed) {
                    Text(
                        text = stringResource(
                            R.string.assign_job_failed
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isAssigning
            ) {
                if (isAssigning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.assign_job_confirm
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isAssigning
            ) {
                Text(
                    text = stringResource(
                        R.string.button_cancel
                    )
                )
            }
        }
    )
}

private fun JobCoordinates.displayValue(): String {
    return String.format(
        Locale.getDefault(),
        "%.5f, %.5f",
        latitude,
        longitude
    )
}
