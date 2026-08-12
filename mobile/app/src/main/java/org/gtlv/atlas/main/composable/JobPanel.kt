package org.gtlv.atlas.main.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.main.MainScreenUiState
import org.gtlv.core.job.Job

@Composable
internal fun JobPanel(
    state: MainScreenUiState,
    onToggleExpanded: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(
            min = 150.dp,
            max = 210.dp
        ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.padding(24.dp)
                )
            }

            state.hasError -> {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(text = "Could not load jobs")

                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(text = "Retry")
                    }
                }
            }

            state.isJobListExpanded -> {
                ExpandedJobs(
                    state = state,
                    onToggleExpanded = onToggleExpanded
                )
            }

            else -> {
                CollapsedJobs(
                    state = state,
                    onToggleExpanded = onToggleExpanded
                )
            }
        }
    }
}

@Composable
private fun CollapsedJobs(
    state: MainScreenUiState,
    onToggleExpanded: () -> Unit
) {
    val nextJob = state.queuedJobs.firstOrNull()

    val hasQueuedJobs = state.queuedJobs.isNotEmpty()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = hasQueuedJobs,
                    onClick = onToggleExpanded
                )
                .padding(
                    start = 8.dp,
                    top = 5.dp,
                    end = 2.dp,
                    bottom = 5.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Next Job (${state.queuedJobs.size} in queue)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = nextJob?.let {
                        "From: ${it.fromDisplayAddress()}"
                    } ?: "No jobs in queue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if(hasQueuedJobs) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = "Show assigned jobs",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 5.dp
            )
        ) {
            Text(
                text = "Current Job",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            val currentJob = state.currentJob

            Text(
                text = if (currentJob == null) {
                    "No current job"
                } else {
                    "From: ${currentJob.fromDisplayAddress()}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpandedJobs(
    state: MainScreenUiState,
    onToggleExpanded: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight(0.75f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = 4.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = "Total: ${state.queuedJobs.size}",
                style =
                    MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onToggleExpanded
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Hide assigned jobs"
                )
            }
        }

        if (state.queuedJobs.isEmpty()) {
            Text(
                text = "No jobs in queue",
                modifier = Modifier.padding(12.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = state.queuedJobs,
                    key = Job::id
                ) { job ->
                    JobRow(job = job)
                    Divider()
                }
            }
        }

        Divider()

        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Current Job",
                style =
                    MaterialTheme.typography.titleSmall
            )

            val currentJob = state.currentJob

            if (currentJob == null) {
                Text(text = "No current Job")
            } else {
                Text(
                    text =
                        "From: ${currentJob.fromDisplayAddress()}"
                )
                Text(
                    text =
                        "To: ${currentJob.toDisplayAddress()}"
                )
            }
        }
    }
}

@Composable
private fun JobRow(
    job: Job
) {
    Column(
        modifier = Modifier.padding(12.dp)
    ) {
        Text(
            text = "From: ${job.fromDisplayAddress()}"
        )

        Text(
            text = "To: ${job.toDisplayAddress()}"
        )
    }
}

private fun Job.fromDisplayAddress(): String {
    return fromAddress
        ?: from?.let {
            "${it.latitude}, ${it.longitude}"
        }
        ?: " "
}

private fun Job.toDisplayAddress(): String {
    return toAddress
        ?: to?.let {
            "${it.latitude}, ${it.longitude}"
        }
        ?: " "
}