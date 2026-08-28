package org.gtlv.atlas.main.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.main.displayNameResource
import org.gtlv.atlas.main.initial
import org.gtlv.atlas.ui.MAX_PROFILE_USER_NAME_LENGTH
import org.gtlv.atlas.ui.truncatedUserName
import org.gtlv.core.shift.ShiftRole

@Composable
internal fun ProfileSidebar(
    userName: String,
    role: ShiftRole,
    onClose: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(224.dp),
        shape = RoundedCornerShape(
            topStart = 24.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        ),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onClose
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close profile"
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.CenterHorizontally),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor =
                    MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.initial(),
                        style =
                            MaterialTheme.typography.headlineSmall
                    )
                }
            }

            Text(
                text = userName.truncatedUserName(
                    MAX_PROFILE_USER_NAME_LENGTH
                ),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme.secondaryContainer,
                contentColor =
                    MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    text = stringResource(
                        role.displayNameResource()
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 6.dp
                    )
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(
                    top = 24.dp,
                    bottom = 16.dp
                )
            )

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.logout)
                )
            }
        }
    }
}
