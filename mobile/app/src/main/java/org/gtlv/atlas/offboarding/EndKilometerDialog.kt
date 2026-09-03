package org.gtlv.atlas.offboarding

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.gtlv.atlas.R

@Composable
internal fun EndKilometerDialog(
    value: String,
    isInvalid: Boolean,
    onValueChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.end_kilometer_dialog_title))
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                singleLine = true,
                label = {
                    Text(stringResource(R.string.end_kilometer_label))
                },
                suffix = {
                    Text(stringResource(R.string.kilometers_unit))
                },
                isError = isInvalid,
                supportingText = if (isInvalid) {
                    {
                        Text(stringResource(R.string.end_kilometer_invalid))
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.start_kilometer_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
