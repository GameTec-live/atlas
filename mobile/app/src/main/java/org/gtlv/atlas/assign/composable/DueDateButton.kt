package org.gtlv.atlas.assign.composable

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.gtlv.atlas.R
import org.gtlv.atlas.unassigned.formatDueDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueDateButton(
    dueDate: String?,
    enabled: Boolean,
    onOpen: () -> Unit,
    openRequestId: Int = 0,
    onPickerClosed: () -> Unit = {},
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

    LaunchedEffect(openRequestId) {
        if (openRequestId > 0) {
            onOpen()
            selectedDateEpochDay = currentDateTime
                .toLocalDate()
                .toEpochDay()
            pickerStep = DUE_DATE_PICKER_DATE
        }
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
                onPickerClosed()
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
                        onPickerClosed()
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
                onPickerClosed()
            }
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme
                    .surfaceContainerHigh,
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
                                onPickerClosed()
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
                                onPickerClosed()
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
