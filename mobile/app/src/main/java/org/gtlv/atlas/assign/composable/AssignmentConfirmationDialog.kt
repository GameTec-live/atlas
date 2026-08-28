package org.gtlv.atlas.assign.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.core.job.JobCandidate

@Composable
internal fun AssignmentConfirmationDialog(
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
