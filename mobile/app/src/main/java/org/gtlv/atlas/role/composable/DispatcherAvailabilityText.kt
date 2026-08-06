package org.gtlv.atlas.role.composable

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.gtlv.atlas.R
import org.gtlv.atlas.role.RoleSelectionUiState

@Composable
internal fun DispatcherAvailabilityText(
    state: RoleSelectionUiState
) {
    if (!state.availabilityLoaded) {
        return
    }

    if (!state.dispatcherAvailable) {
        Text(
            text = stringResource(
                R.string.dispatcher_unavailable
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        return
    }

    Text(
        text = if (state.dispatcherSpotsFree == 1) {
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