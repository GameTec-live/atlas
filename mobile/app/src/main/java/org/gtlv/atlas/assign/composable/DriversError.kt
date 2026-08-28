package org.gtlv.atlas.assign.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.gtlv.atlas.R

@Composable
internal fun DriversError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = message)

        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(
                    R.string.unassigned_jobs_retry
                )
            )
        }
    }
}
