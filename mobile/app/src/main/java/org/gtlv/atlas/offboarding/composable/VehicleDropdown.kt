package org.gtlv.atlas.offboarding.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.core.fleet.Vehicle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleDropdown(
    vehicles: List<Vehicle>,
    isLoading: Boolean,
    loadFailed: Boolean,
    enabled: Boolean,
    onVehicleSelected: (Vehicle) -> Unit,
    onRetry: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    when {
        isLoading -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Text(stringResource(R.string.offboarding_loading_vehicles))
        }

        loadFailed -> Column {
            Text(
                text = stringResource(R.string.offboarding_vehicles_load_failed),
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry, enabled = enabled) {
                Text(stringResource(R.string.offboarding_retry))
            }
        }

        vehicles.isEmpty() -> Text(
            text = stringResource(R.string.offboarding_no_vehicles),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        else -> ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = enabled
                    )
                    .fillMaxWidth(),
                enabled = enabled,
                readOnly = true,
                label = { Text(stringResource(R.string.offboarding_select_vehicle)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                vehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(vehicle.displayName)
                                Text(
                                    text = vehicle.licensePlate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onVehicleSelected(vehicle)
                        }
                    )
                }
            }
        }
    }
}
