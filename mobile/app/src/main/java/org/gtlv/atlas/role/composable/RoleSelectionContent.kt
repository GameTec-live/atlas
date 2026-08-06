package org.gtlv.atlas.role.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.role.RoleSelectionUiState
import org.gtlv.atlas.ui.asString
import org.gtlv.core.shift.ShiftRole

@Composable
internal fun RoleSelectionContent(
    state: RoleSelectionUiState,
    onDispatcherSelected: () -> Unit,
    onDriverSelected: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.select_role_title),
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

        Spacer(modifier = Modifier.height(32.dp))

        if (state.isLoadingAvailability) {
            CircularProgressIndicator()

            Spacer(modifier = Modifier.height(24.dp))
        }

        RoleButton(
            text = stringResource(R.string.role_dispatcher),
            enabled = state.availabilityLoaded &&
                    state.dispatcherAvailable &&
                    !state.isLoadingAvailability &&
                    !state.isSelectingRole &&
                    state.pendingShiftRole == null,
            isLoading = state.isSelectingRole &&
                    state.selectedRole == ShiftRole.DISPATCHER,
            onClick = onDispatcherSelected
        )

        Spacer(modifier = Modifier.height(8.dp))

        DispatcherAvailabilityText(state = state)

        Spacer(modifier = Modifier.height(28.dp))

        RoleButton(
            text = stringResource(R.string.role_driver),
            enabled = state.availabilityLoaded &&
                    !state.isLoadingAvailability &&
                    !state.isSelectingRole &&
                    state.pendingShiftRole == null,
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
            !state.isLoadingAvailability &&
            !state.isSelectingRole &&
            (
                    !state.availabilityLoaded ||
                            state.pendingShiftRole != null
                    )
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onRetry,
                enabled = !state.isSelectingRole
            ) {
                Text(
                    text = stringResource(R.string.role_retry)
                )
            }
        }
    }
}