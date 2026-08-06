package org.gtlv.atlas.auth.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.gtlv.atlas.R
import org.gtlv.atlas.auth.LoginUiState
import org.gtlv.atlas.ui.asString

@Composable
internal fun ServerAddressDialog(
    state: LoginUiState,
    onServerAddressChanged: (String) -> Unit,
    onSaveServerAddress: () -> Unit,
    onDismissServerDialog: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissServerDialog,
        title = {
            Text(
                stringResource(
                    R.string.login_set_serveraddress
                )
            )
        },
        text = {
            OutlinedTextField(
                value = state.serverAddressInput,
                onValueChange = onServerAddressChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        stringResource(
                            R.string.login_alert_dialog_label
                        )
                    )
                },
                placeholder = {
                    Text("https://example.com")
                },
                singleLine = true,
                isError = state.serverAddressError != null,
                supportingText =
                    state.serverAddressError?.let { error ->
                        {
                            Text(error.asString())
                        }
                    }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onSaveServerAddress
            ) {
                Text(
                    stringResource(R.string.buttom_save)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissServerDialog
            ) {
                Text(
                    stringResource(R.string.button_cancel)
                )
            }
        }
    )
}