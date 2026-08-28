package org.gtlv.atlas.newjob.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.gtlv.atlas.R
import org.gtlv.core.job.JobCandidate

@Composable
internal fun CreateJobConfirmationDialog(
    candidate: JobCandidate?,
    isCreating: Boolean,
    creationFailed: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_job_confirm_title)) },
        text = {
            Text(
                text = when {
                    creationFailed -> stringResource(R.string.new_job_create_failed)
                    candidate == null -> stringResource(
                        R.string.new_job_confirm_unassigned
                    )
                    else -> stringResource(
                        R.string.new_job_confirm_driver,
                        candidate.driverName
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isCreating) {
                Text(
                    stringResource(
                        if (isCreating) {
                            R.string.new_job_creating
                        } else {
                            R.string.new_job_create
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
