package org.gtlv.atlas.auth.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.auth.ServerLogo

@Composable
internal fun Branding(
    serverAddress: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ServerLogo(
            serverAddress = serverAddress
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium
        )

        Text(
            text = stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}