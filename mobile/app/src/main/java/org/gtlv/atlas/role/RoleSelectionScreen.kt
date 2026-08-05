package org.gtlv.atlas.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.ui.asString
import org.gtlv.core.shift.ShiftRole
import androidx.compose.foundation.layout.padding

@Composable
fun RoleSelectionScreen(
    state: RoleSelectionUiState,
    onDispatcherSelected: () -> Unit,
    onDriverSelected: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(
                    R.string.select_role_title
                ),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.select_role_description
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (state.isLoadingAvailability) {
                CircularProgressIndicator()

                Spacer(modifier = Modifier.height(24.dp))
            }

            RoleButton(
                text = stringResource(R.string.role_dispatcher),
                enabled = state.availabilityLoaded &&
                        state.dispatcherAvailable &&
                        !state.isLoadingAvailability &&
                        !state.isSelectingRole,
                isLoading = state.isSelectingRole &&
                        state.selectedRole == ShiftRole.DISPATCHER,
                onClick = onDispatcherSelected
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (
                state.availabilityLoaded &&
                state.dispatcherAvailable
            ) {
                Text(
                    text = if (
                        state.dispatcherSpotsFree == 1
                    ) {
                        stringResource(
                            R.string.dispatcher_spot_available
                        )
                    } else {
                        stringResource(
                            R.string.dispatcher_spots_available,
                            state.dispatcherSpotsFree
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (
                state.availabilityLoaded &&
                !state.dispatcherAvailable
            ) {
                Text(
                    text = stringResource(
                        R.string.dispatcher_unavailable
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            RoleButton(
                text = stringResource(R.string.role_driver),
                enabled = state.availabilityLoaded &&
                        !state.isLoadingAvailability &&
                        !state.isSelectingRole,
                isLoading = state.isSelectingRole &&
                        state.selectedRole == ShiftRole.DRIVER,
                onClick = onDriverSelected
            )

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = error.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            if (
                state.error != null &&
                !state.availabilityLoaded &&
                !state.isLoadingAvailability
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onRetry,
                    enabled = !state.isSelectingRole
                ) {
                    Text(
                        text = stringResource(
                            R.string.role_retry
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleButton(
    text: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(64.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}