package org.gtlv.atlas.main.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.unassigned.formatDueDate
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobCoordinates

@Composable
internal fun StartJobDateConfirmationDialog(
    job: Job,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val unknownLocation = stringResource(R.string.unassigned_jobs_unknown_location)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.job_start_other_day_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.job_start_other_day_message))
                JobDetail(
                    stringResource(R.string.unassigned_jobs_from),
                    job.fromAddress?.takeIf(String::isNotBlank)
                        ?: job.from?.displayCoordinates() ?: unknownLocation
                )
                JobDetail(
                    stringResource(R.string.unassigned_jobs_to),
                    job.toAddress?.takeIf(String::isNotBlank)
                        ?: job.to?.displayCoordinates() ?: unknownLocation
                )
                JobDetail(
                    stringResource(R.string.unassigned_jobs_due),
                    job.dueDate?.takeIf(String::isNotBlank)?.let(::formatDueDate)
                        ?: stringResource(R.string.unassigned_jobs_no_due_date)
                )
                JobDetail(
                    stringResource(R.string.unassigned_jobs_note),
                    job.note?.takeIf(String::isNotBlank) ?: "—"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.job_start_other_day_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.job_start_other_day_cancel))
            }
        }
    )
}

@Composable
private fun JobDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun JobCoordinates.displayCoordinates() = "$latitude, $longitude"
