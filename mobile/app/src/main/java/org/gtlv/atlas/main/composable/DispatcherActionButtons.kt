package org.gtlv.atlas.main.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R

@Composable
internal fun DispatcherActionButtons(
    unassignedJobCount: Int?,
    onUnassignedJobsClick: () -> Unit,
    onNewJobClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BadgedBox(
            badge = {
                unassignedJobCount?.let { count ->
                    Badge {
                        Text(text = count.toString())
                    }
                }
            }
        ) {
            FilledTonalIconButton(
                onClick = onUnassignedJobsClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AssignmentLate,
                    contentDescription =
                        if (unassignedJobCount == null) {
                            stringResource(
                                R.string
                                    .dispatcher_action_unassigned_jobs
                            )
                        } else {
                            pluralStringResource(
                                R.plurals
                                    .dispatcher_action_unassigned_jobs_count,
                                unassignedJobCount,
                                unassignedJobCount
                            )
                        }
                )
            }
        }

        FilledTonalIconButton(
            onClick = onNewJobClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(
                    R.string.dispatcher_action_new_job
                )
            )
        }
    }
}
