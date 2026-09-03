package org.gtlv.atlas.fleet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.core.fleet.ConnectedVehicleState

@Composable
internal fun PairVehicleDialog(
    state: ConnectedVehicleState.PairingRequired,
    onVehicleSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.pair_vehicle_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.pair_vehicle_message))

                when {
                    state.isLoading -> CircularProgressIndicator()
                    state.hasError -> Text(
                        stringResource(R.string.pair_vehicle_error)
                    )
                    state.candidates.isEmpty() -> Text(
                        stringResource(R.string.pair_vehicle_empty)
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = state.candidates,
                            key = { it.id }
                        ) { vehicle ->
                            OutlinedButton(
                                onClick = { onVehicleSelected(vehicle.id) },
                                enabled = !state.isPairing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.pair_vehicle_item,
                                        vehicle.displayName,
                                        vehicle.licensePlate
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.hasError || (!state.isLoading && state.candidates.isEmpty())) {
                TextButton(
                    onClick = onRetry,
                    enabled = !state.isPairing
                ) {
                    Text(stringResource(R.string.pair_vehicle_retry))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isPairing
            ) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
