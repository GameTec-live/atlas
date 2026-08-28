package org.gtlv.atlas.newjob.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.address.AddressSearchField
import org.gtlv.atlas.address.AddressSearchPresentation
import org.gtlv.atlas.assign.composable.DueDateButton
import org.gtlv.atlas.newjob.NEW_JOB_NOTE_MAX_LENGTH
import org.gtlv.atlas.newjob.NewJobUiState
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.job.JobLocationField

@Composable
internal fun NewJobFields(
    state: NewJobUiState,
    onEditAddress: (JobLocationField) -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected: (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    onDueDateChanged: (String) -> Unit,
    onUnassignedDueDateSelected: (String) -> Unit,
    onDueDatePickerClosed: () -> Unit,
    onNoteChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp
    ) {
        Column {
            AddressRow(
                label = stringResource(R.string.unassigned_jobs_from),
                value = state.fromAddress,
                placeholder = stringResource(R.string.new_job_pickup_placeholder),
                field = JobLocationField.FROM,
                state = state,
                enabled = !state.isCreating,
                onEditAddress = onEditAddress,
                onAddressQueryChanged = onAddressQueryChanged,
                onAddressSuggestionSelected = onAddressSuggestionSelected,
                onCloseAddressEditor = onCloseAddressEditor,
                trailingContent = {
                    DueDateButton(
                        dueDate = state.dueDate,
                        enabled = !state.isCreating,
                        onOpen = {
                            if (state.isAddressEditorOpen) {
                                onCloseAddressEditor()
                            }
                        },
                        openRequestId = state.dueDatePickerRequestId,
                        onPickerClosed = onDueDatePickerClosed,
                        onDueDateChanged = { dueDate ->
                            if (state.isSelectingUnassignedDueDate) {
                                onUnassignedDueDateSelected(dueDate)
                            } else {
                                onDueDateChanged(dueDate)
                            }
                        }
                    )
                }
            )

            HorizontalDivider()

            AddressRow(
                label = stringResource(R.string.unassigned_jobs_to),
                value = state.toAddress,
                placeholder = stringResource(
                    R.string.new_job_destination_placeholder
                ),
                field = JobLocationField.TO,
                state = state,
                enabled = !state.isCreating,
                onEditAddress = onEditAddress,
                onAddressQueryChanged = onAddressQueryChanged,
                onAddressSuggestionSelected = onAddressSuggestionSelected,
                onCloseAddressEditor = onCloseAddressEditor
            )

            HorizontalDivider()

            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChanged,
                enabled = !state.isCreating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                label = { Text(stringResource(R.string.new_job_note)) },
                placeholder = {
                    Text(stringResource(R.string.new_job_note_placeholder))
                },
                supportingText = {
                    Text(
                        text = stringResource(
                            R.string.new_job_note_count,
                            state.note.length,
                            NEW_JOB_NOTE_MAX_LENGTH
                        )
                    )
                },
                singleLine = true
            )
        }
    }
}

@Composable
private fun AddressRow(
    label: String,
    value: String?,
    placeholder: String,
    field: JobLocationField,
    state: NewJobUiState,
    enabled: Boolean,
    onEditAddress: (JobLocationField) -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected: (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier
                .padding(start = 12.dp)
                .width(42.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (
            state.isAddressEditorOpen &&
            state.editedLocationField == field
        ) {
            AddressSearchField(
                state = state.addressSearch,
                label = placeholder,
                onQueryChanged = onAddressQueryChanged,
                onSuggestionSelected = onAddressSuggestionSelected,
                onClose = onCloseAddressEditor,
                presentation = AddressSearchPresentation.INLINE,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = value ?: placeholder,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = enabled,
                        onClick = { onEditAddress(field) }
                    )
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (value == null) {
                    FontWeight.Normal
                } else {
                    FontWeight.SemiBold
                },
                color = if (value == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        trailingContent?.invoke()
    }
}
