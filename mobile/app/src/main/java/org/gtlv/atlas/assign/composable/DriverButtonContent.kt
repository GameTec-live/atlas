package org.gtlv.atlas.assign.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.gtlv.atlas.R
import org.gtlv.atlas.ui.truncatedUserName
import org.gtlv.core.job.JobCandidate

@Composable
internal fun DriverButtonContent(
    candidate: JobCandidate,
    showRank: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = candidate.driverName.truncatedUserName(),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (showRank) {
            Text(
                text = stringResource(
                    R.string.assign_job_recommended_rank,
                    candidate.rank
                ),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
