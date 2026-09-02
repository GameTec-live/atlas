package org.gtlv.atlas.main.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.main.MainScreenUiState
import org.gtlv.core.job.Job

@Composable
internal fun JobPanel(
    state: MainScreenUiState,
    onToggleExpanded: () -> Unit,
    onRetry: () -> Unit,
    onEditDestination: () -> Unit,
    modifier: Modifier = Modifier,
    isExpandable: Boolean = true
) {
    Surface(
        modifier = modifier.widthIn(
            min = 150.dp,
            max = 210.dp
        ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor =
            MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp
    ) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onRetry
        ) {
            when {
                state.hasError -> {
                    JobError(
                        onRetry = onRetry
                    )
                }

                isExpandable &&
                    state.isJobListExpanded -> {
                    ExpandedJobs(
                        state = state,
                        onToggleExpanded =
                            onToggleExpanded,
                        onEditDestination =
                            onEditDestination
                    )
                }

                else -> {
                    CollapsedJobs(
                        state = state,
                        onToggleExpanded =
                            onToggleExpanded,
                        onEditDestination =
                            onEditDestination,
                        isExpandable =
                            isExpandable
                    )
                }
            }
        }
    }
}

@Composable
private fun JobError(
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = stringResource(
                R.string.job_panel_load_error
            )
        )

        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.job_panel_retry
                )
            )
        }
    }
}

@Composable
private fun CollapsedJobs(
    state: MainScreenUiState,
    onToggleExpanded: () -> Unit,
    onEditDestination: () -> Unit,
    isExpandable: Boolean
) {
    val nextJob =
        state.queuedJobs.firstOrNull()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = isExpandable,
                    onClick = onToggleExpanded
                )
                .padding(
                    start = 8.dp,
                    top = 5.dp,
                    end = 2.dp,
                    bottom = 5.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals
                            .job_panel_next_job,
                        count =
                            state.queuedJobs.size,
                        state.queuedJobs.size
                    ),
                    style =
                        MaterialTheme.typography
                            .labelLarge,
                    color =
                        MaterialTheme.colorScheme
                            .onSurface
                )

                Text(
                    text = nextJob?.let { job ->
                        stringResource(
                            R.string.job_panel_from,
                            job.fromDisplayAddress()
                        )
                    } ?: stringResource(
                        R.string.job_panel_no_jobs
                    ),
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )
            }

            if (isExpandable) {
                Icon(
                    imageVector =
                        Icons.Default.ExpandLess,
                    contentDescription =
                        "Show assigned jobs",
                    tint =
                        MaterialTheme.colorScheme
                            .onSurface
                )
            }
        }

        HorizontalDivider()

        CurrentJobSection(
            currentJob = state.currentJob,
            onEditDestination =
                onEditDestination,
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 5.dp
            ),
            compact = true
        )
    }
}

@Composable
private fun ExpandedJobs(
    state: MainScreenUiState,
    onToggleExpanded: () -> Unit,
    onEditDestination: () -> Unit
) {
    Column(
        modifier =
            Modifier.fillMaxHeight(0.75f)
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
                text = stringResource(
                    R.string.job_panel_total_jobs,
                    state.queuedJobs.size
                ),
                style =
                    MaterialTheme.typography
                        .titleSmall
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onToggleExpanded
            ) {
                Icon(
                    imageVector =
                        Icons.Default.ExpandMore,
                    contentDescription =
                        "Hide assigned jobs"
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            if (state.queuedJobs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            R.string
                                .job_panel_no_jobs
                        ),
                        modifier =
                            Modifier.padding(12.dp)
                    )
                }
            } else {
                items(
                    items = state.queuedJobs,
                    key = Job::id
                ) { job ->
                    JobRow(job = job)
                    HorizontalDivider()
                }
            }
        }

        HorizontalDivider()

        CurrentJobSection(
            currentJob = state.currentJob,
            onEditDestination =
                onEditDestination,
            modifier = Modifier.padding(
                12.dp
            ),
            compact = false
        )
    }
}

@Composable
private fun CurrentJobSection(
    currentJob: Job?,
    onEditDestination: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = stringResource(
                R.string.job_panel_current_job
            ),
            style = if (compact) {
                MaterialTheme.typography
                    .labelLarge
            } else {
                MaterialTheme.typography
                    .titleSmall
            },
            color =
                MaterialTheme.colorScheme
                    .onSurface
        )

        if (currentJob == null) {
            Text(
                text = stringResource(
                    R.string
                        .job_panel_no_current_job
                ),
                style =
                    MaterialTheme.typography
                        .bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        } else {
            AddressLine(
                labelResource =
                    R.string.job_panel_from,
                address =
                    currentJob
                        .fromDisplayAddress(),
                compact = compact
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                AddressLine(
                    labelResource =
                        R.string.job_panel_to,
                    address =
                        currentJob.toDisplayAddress(),
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription =
                        stringResource(
                            R.string
                                .job_panel_edit_destination
                        ),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                        .clickable(
                            onClick = onEditDestination
                        ),
                    tint =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
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
        AddressLine(
            labelResource =
                R.string.job_panel_from,
            address =
                job.fromDisplayAddress(),
            compact = false
        )

        AddressLine(
            labelResource =
                R.string.job_panel_to,
            address =
                job.toDisplayAddress(),
            compact = false
        )
    }
}

@Composable
private fun AddressLine(
    labelResource: Int,
    address: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(
            labelResource,
            address
        ),
        modifier = modifier,
        style = if (compact) {
            MaterialTheme.typography.bodySmall
        } else {
            MaterialTheme.typography.bodyMedium
        },
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
        maxLines = if (compact) {
            1
        } else {
            2
        },
        softWrap = true,
        overflow = TextOverflow.Ellipsis
    )
}

private fun Job.fromDisplayAddress(): String {
    return fromAddress
        ?: from?.let { coordinates ->
            "${coordinates.latitude}, " +
                    "${coordinates.longitude}"
        }
        ?: " "
}

private fun Job.toDisplayAddress(): String {
    return toAddress
        ?: to?.let { coordinates ->
            "${coordinates.latitude}, " +
                    "${coordinates.longitude}"
        }
        ?: " "
}
