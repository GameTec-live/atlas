package org.gtlv.atlas.main.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.MaterialTheme

@Composable
internal fun JobActionButtons(
    hasCurrentJob: Boolean,
    hasNextJob: Boolean,
    isStartingNextJob: Boolean,
    isCancellingCurrentJob: Boolean,
    isPersonCollected: Boolean,
    onNextJobClick: () -> Unit,
    onCancelCurrentJobClick: () -> Unit,
    onPersonCollectedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (hasCurrentJob) {
            FilledTonalIconButton(
                onClick = onCancelCurrentJobClick,
                enabled = !isCancellingCurrentJob,
                modifier = Modifier.size(48.dp)
            ) {
                if (isCancellingCurrentJob) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(
                            R.string.job_action_cancel
                        )
                    )
                }
            }

            /*
             * Person-collected logic will be implemented later.
             */
            Button(
                onClick = onPersonCollectedClick,
                enabled =
                    !isPersonCollected &&
                            !isCancellingCurrentJob,
                modifier = Modifier.widthIn(
                    min = 108.dp
                )
            ) {
                Text(
                    text = stringResource(
                        if (isPersonCollected) {
                            R.string.job_action_job_finished
                        } else {
                            R.string.job_action_person_collected
                        }
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
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            }
        }
    }
}