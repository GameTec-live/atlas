package org.gtlv.atlas.main.composable

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R

@Composable
internal fun CancelJobConfirmationDialog(
    isCancelling: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isCancelling) onDismiss()
        },
        title = {
            Text(
                text = stringResource(
                    R.string.job_cancel_confirmation_title
                )
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string.job_cancel_confirmation_message
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isCancelling
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.job_cancel_yes
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCancelling
            ) {
                Text(
                    text = stringResource(
                        R.string.job_cancel_no
                    )
                )
            }
        }
    )
}
