package org.gtlv.atlas.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.ui.asString


@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordVisibilityChanged: () -> Unit,
    onLogin: () -> Unit,
    onEditServer: () -> Unit,
    onServerAddressChanged: (String) -> Unit,
    onSaveServerAddress: () -> Unit,
    onDismissServerDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize()
    ) {

        if (state.isCheckingSession) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium
            )

            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(stringResource(R.string.username))
                },
                singleLine = true,
                enabled = !state.isLoading,
                isError = state.usernameError != null,
                supportingText = state.usernameError?.let { error ->
                    {
                        Text(error.asString())
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                enabled = !state.isLoading,
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { message ->
                    { Text(message.asString()) }
                },
                visualTransformation = if (state.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = onPasswordVisibilityChanged) {
                        Icon(
                            imageVector = if (state.passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (state.passwordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            }
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onLogin() }
                )
            )

            state.loginError?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = error.asString(),
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onLogin,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(0.55f)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.login))
                }


            }

            if (state.loginSuccessful) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.login_successful),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = onEditServer
            ) {
                Text(
                    text = state.serverAddress.ifBlank {
                        stringResource(R.string.login_set_serveraddress)
                    }
                )

                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit server address"
                )
            }
        }
    }

    if (state.showServerDialog) {
        AlertDialog(
            onDismissRequest = onDismissServerDialog,
            title = {
                Text(stringResource(R.string.login_set_serveraddress))
            },
            text = {
                OutlinedTextField(
                    value = state.serverAddressInput,
                    onValueChange = onServerAddressChanged,
                    label = {
                        Text(stringResource(R.string.login_alert_dialog_label))
                    },
                    placeholder = {
                        Text("https://example.com")
                    },
                    singleLine = true,
                    isError = state.serverAddressError != null,
                    supportingText = state.serverAddressError?.let { error ->
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
                    Text(stringResource(R.string.buttom_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissServerDialog
                ) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }

}