package org.gtlv.atlas.offboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gtlv.atlas.offboarding.composable.OffboardingContent
import org.gtlv.core.fleet.Vehicle

@Composable
internal fun OffboardingScreen(
    state: OffboardingUiState,
    onRevenueChanged: (String) -> Unit,
    onConfirmationChanged: (Boolean) -> Unit,
    onVehicleSelected: (Vehicle) -> Unit,
    onRetryVehicles: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    OffboardingContent(
        state = state,
        onRevenueChanged = onRevenueChanged,
        onConfirmationChanged = onConfirmationChanged,
        onVehicleSelected = onVehicleSelected,
        onRetryVehicles = onRetryVehicles,
        onSubmit = onSubmit,
        onBack = onBack,
        modifier = modifier
    )
}
