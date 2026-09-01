package org.gtlv.atlas.main.composable

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
internal fun StartKilometerDialog(
    value: String,
    isInvalid: Boolean,
    isSaving: Boolean,
    onValueChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(
                    R.string.start_kilometer_dialog_title
                )
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                enabled = !isSaving,
                singleLine = true,
                label = {
                    Text(
                        text = stringResource(
                            R.string.start_kilometer_label
                        )
                    )
                },
                suffix = {
                    Text(text = stringResource(R.string.kilometers_unit))
                },
                isError = isInvalid,
                supportingText = if (isInvalid) {
                    {
                        Text(
                            text = stringResource(
                                R.string.start_kilometer_invalid
                            )
                        )
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
            TextButton(
                onClick = onConfirm,
                enabled = !isSaving
            ) {
                Text(
                    text = stringResource(
                        R.string.start_kilometer_continue
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
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
