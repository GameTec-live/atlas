package org.gtlv.atlas.auth.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R

@Composable
internal fun ServerAddressButton(
    serverAddress: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = serverAddress.ifBlank {
                stringResource(
                    R.string.login_set_serveraddress
                )
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(
                R.string.server_address_edit
            )
        )
    }
}
