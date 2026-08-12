package org.gtlv.atlas.main.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R

@Composable
internal fun JobActionButtons(
    hasCurrentJob: Boolean,
    hasNextJob: Boolean,
    isStartingNextJob: Boolean,
    onNextJobClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (hasCurrentJob) {
            /*
             * These buttons are intentionally disabled.
             * Their business logic will be implemented later.
             */
            FilledTonalIconButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(
                        R.string.job_action_cancel
                    )
                )
            }

            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.widthIn(
                    min = 108.dp
                )
            ) {
                Text(
                    text = stringResource(
                        R.string.job_action_person_collected
                    )
                )
            }
        } else {
            Button(
                onClick = onNextJobClick,
                enabled =
                    hasNextJob &&
                            !isStartingNextJob,
                modifier = Modifier.widthIn(
                    min = 108.dp
                )
            ) {
                if (isStartingNextJob) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.job_action_next_job
                        )
                    )

                    Icon(
                        imageVector =
                            Icons.Default.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            }
        }
    }
}