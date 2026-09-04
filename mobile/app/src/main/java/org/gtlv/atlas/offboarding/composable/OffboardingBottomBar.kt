package org.gtlv.atlas.offboarding.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.offboarding.OffboardingError
import org.gtlv.atlas.offboarding.OffboardingUiState

@Composable
internal fun OffboardingBottomBar(
    state: OffboardingUiState,
    onConfirmationChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.isConfirmed,
                        enabled = !state.isSubmitting,
                        role = Role.Checkbox,
                        onValueChange = onConfirmationChanged
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.isConfirmed,
                    onCheckedChange = null,
                    enabled = !state.isSubmitting
                )
                Text(
                    text = stringResource(R.string.offboarding_confirmation),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.error?.let { error ->
                Text(
                    text = stringResource(error.messageResource()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = onSubmit,
                enabled = state.isConfirmed && !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.offboarding_log_off),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private fun OffboardingError.messageResource(): Int = when (this) {
    OffboardingError.VEHICLE_REQUIRED -> R.string.offboarding_vehicle_required
    OffboardingError.START_KILOMETER_UNAVAILABLE ->
        R.string.offboarding_start_kilometer_unavailable
    OffboardingError.SUBMISSION_FAILED -> R.string.offboarding_submit_failed
}
