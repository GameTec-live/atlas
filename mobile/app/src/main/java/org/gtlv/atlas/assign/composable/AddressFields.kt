package org.gtlv.atlas.assign.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.gtlv.atlas.R
import org.gtlv.atlas.address.AddressSearchField
import org.gtlv.atlas.address.AddressSearchPresentation
import org.gtlv.atlas.assign.AssignJobUiState
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.job.JobLocationField

@Composable
internal fun AddressFields(
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
        color = MaterialTheme.colorScheme.surface,
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

private fun JobCoordinates.displayValue(): String {
    return String.format(
        Locale.getDefault(),
        "%.5f, %.5f",
        latitude,
        longitude
    )
}
