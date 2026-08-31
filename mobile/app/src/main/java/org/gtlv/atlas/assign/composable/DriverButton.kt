package org.gtlv.atlas.assign.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gtlv.core.job.JobCandidate

@Composable
internal fun DriverButton(
    candidate: JobCandidate,
    recommended: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (recommended) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            DriverButtonContent(
                candidate = candidate,
                showRank = true
            )
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            DriverButtonContent(
                candidate = candidate,
                showRank = false
            )
        }
    }
}
