package org.gtlv.atlas.location

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.gtlv.atlas.R

internal enum class LocationPermissionPrompt {
    Hidden,
    Retry,
    Settings
}

@Composable
internal fun LocationPermissionDialog(
    prompt: LocationPermissionPrompt,
    onTryAgain: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (prompt == LocationPermissionPrompt.Hidden) {
        return
    }

    val messageResource =
        when (prompt) {
            LocationPermissionPrompt.Retry ->
                R.string.location_permission_retry_message

            LocationPermissionPrompt.Settings ->
                R.string.location_permission_settings_message

            LocationPermissionPrompt.Hidden ->
                return
        }

    val buttonResource =
        when (prompt) {
            LocationPermissionPrompt.Retry ->
                R.string.location_permission_allow_button

            LocationPermissionPrompt.Settings ->
                R.string.location_permission_settings_button

            LocationPermissionPrompt.Hidden ->
                return
        }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = stringResource(
                    R.string.location_permission_title
                )
            )
        },
        text = {
            Text(
                text = stringResource(messageResource)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    when (prompt) {
                        LocationPermissionPrompt.Retry ->
                            onTryAgain()

                        LocationPermissionPrompt.Settings ->
                            onOpenSettings()

                        LocationPermissionPrompt.Hidden ->
                            Unit
                    }
                }
            ) {
                Text(
                    text = stringResource(buttonResource)
                )
            }
        }
    )
}