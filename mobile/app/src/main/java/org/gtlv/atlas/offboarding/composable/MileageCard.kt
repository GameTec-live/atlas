package org.gtlv.atlas.offboarding.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R

@Composable
internal fun MileageCard(
    startKilometer: String,
    endKilometer: String,
    drivenKilometers: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Metric(
                label = stringResource(R.string.offboarding_km_start),
                value = startKilometer,
                modifier = Modifier.weight(1f)
            )
            MetricDivider()
            Metric(
                label = stringResource(R.string.offboarding_km_end),
                value = endKilometer,
                modifier = Modifier.weight(1f)
            )
            MetricDivider()
            Metric(
                label = stringResource(R.string.offboarding_km_driven),
                value = drivenKilometers,
                emphasize = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
