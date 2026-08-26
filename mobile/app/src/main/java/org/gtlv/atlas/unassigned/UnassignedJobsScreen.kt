package org.gtlv.atlas.unassigned

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.gtlv.atlas.R
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobCoordinates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnassignedJobsScreen(
    state: UnassignedJobsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onJobClick: (Job) -> Unit,
    onRequestDeletion: (Job) -> Unit,
    onDismissDeletion: () -> Unit,
    onConfirmDeletion: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            R.string.unassigned_jobs_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                stringResource(
                                    R.string.unassigned_jobs_back
                                )
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing =
                state.isLoading &&
                        state.jobs.isNotEmpty(),
            onRefresh = onRetry,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            when {
                state.isLoading &&
                        state.jobs.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(
                            Alignment.Center
                        )
                    )
                }

                state.hasError &&
                        state.jobs.isEmpty() -> {
                    LoadError(
                        onRetry = onRetry,
                        modifier = Modifier.align(
                            Alignment.Center
                        )
                    )
                }

                state.jobs.isEmpty() -> {
                    Text(
                        text = stringResource(
                            R.string.unassigned_jobs_empty
                        ),
                        style =
                            MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant,
                        modifier = Modifier.align(
                            Alignment.Center
                        )
                    )
                }

                else -> {
                    val zoneId = ZoneId.systemDefault()
                    val today = LocalDate.now(zoneId)
                    val sections = groupJobsByDueDate(
                        jobs = state.jobs,
                        zoneId = zoneId,
                        today = today
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        sections.forEachIndexed {
                                sectionIndex,
                                section ->
                            item(
                                key = "section-${section.date}"
                            ) {
                                DateSectionHeader(
                                    title = when {
                                        section.date == null -> {
                                            stringResource(
                                                R.string
                                                    .unassigned_jobs_no_due_date
                                            )
                                        }

                                        section.date == today -> {
                                            stringResource(
                                                R.string
                                                    .unassigned_jobs_today
                                            )
                                        }

                                        else -> {
                                            formatSectionDate(
                                                section.date
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(
                                        top =
                                            if (sectionIndex == 0) {
                                                0.dp
                                            } else {
                                                20.dp
                                            },
                                        bottom = 8.dp
                                    )
                                )
                            }

                            items(
                                items = section.jobs,
                                key = Job::id
                            ) { job ->
                                UnassignedJobCard(
                                    job = job,
                                    deletionEnabled =
                                        !state.isDeleting,
                                    isDeleting =
                                        state.deletingJobId ==
                                                job.id,
                                    onDelete = {
                                        onRequestDeletion(job)
                                    },
                                    onClick = {
                                        onJobClick(job)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            bottom = 12.dp
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingDeletion?.let { job ->
        DeleteJobConfirmationDialog(
            job = job,
            isDeleting = state.isDeleting,
            deleteFailed = state.deleteFailed,
            onConfirm = onConfirmDeletion,
            onDismiss = onDismissDeletion
        )
    }
}

@Composable
private fun DateSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme
                .onSurfaceVariant
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun LoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(
                R.string.unassigned_jobs_load_error
            ),
            style = MaterialTheme.typography.bodyLarge
        )

        Button(onClick = onRetry) {
            Text(
                text = stringResource(
                    R.string.unassigned_jobs_retry
                )
            )
        }
    }
}

@Composable
private fun UnassignedJobCard(
    job: Job,
    deletionEnabled: Boolean,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                top = 12.dp,
                end = 8.dp,
                bottom = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {
                JobDetail(
                    label = stringResource(
                        R.string.unassigned_jobs_from
                    ),
                    value = job.fromDisplayValue()
                )

                JobDetail(
                    label = stringResource(
                        R.string.unassigned_jobs_to
                    ),
                    value = job.toDisplayValue()
                )

                job.dueDate?.let { dueDate ->
                    JobDetail(
                        label = stringResource(
                            R.string.unassigned_jobs_due
                        ),
                        value = formatDueDate(dueDate)
                    )
                }

                job.note?.let { note ->
                    JobDetail(
                        label = stringResource(
                            R.string.unassigned_jobs_note
                        ),
                        value = note
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                enabled = deletionEnabled
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(
                            R.string.unassigned_jobs_delete
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteJobConfirmationDialog(
    job: Job,
    isDeleting: Boolean,
    deleteFailed: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(
                    R.string.unassigned_jobs_delete_title
                )
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.unassigned_jobs_delete_message,
                        job.fromDisplayValue()
                    )
                )

                if (deleteFailed) {
                    Text(
                        text = stringResource(
                            R.string
                                .unassigned_jobs_delete_error
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string
                                .unassigned_jobs_delete_confirm
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text(
                    text = stringResource(
                        R.string.button_cancel
                    )
                )
            }
        }
    )
}

@Composable
private fun JobDetail(
    label: String,
    value: String
) {
    Text(
        text = stringResource(
            R.string.unassigned_jobs_detail,
            label,
            value
        ),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun Job.fromDisplayValue(): String {
    return fromAddress
        ?: from?.displayValue()
        ?: stringResource(
            R.string.unassigned_jobs_unknown_location
        )
}

@Composable
private fun Job.toDisplayValue(): String {
    return toAddress
        ?: to?.displayValue()
        ?: stringResource(
            R.string.unassigned_jobs_no_destination
        )
}

private fun JobCoordinates.displayValue(): String {
    return String.format(
        Locale.getDefault(),
        "%.5f, %.5f",
        latitude,
        longitude
    )
}

internal fun formatDueDate(
    value: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val instant = runCatching {
        Instant.parse(value)
    }.getOrNull() ?: return value

    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(instant)
}

internal data class UnassignedJobsDateSection(
    val date: LocalDate?,
    val jobs: List<Job>
)

internal fun groupJobsByDueDate(
    jobs: List<Job>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zoneId)
): List<UnassignedJobsDateSection> {
    val grouped = jobs.groupBy { job ->
        dueDateForSection(
            value = job.dueDate,
            zoneId = zoneId
        )
    }

    fun section(date: LocalDate):
            UnassignedJobsDateSection {
        return UnassignedJobsDateSection(
            date = date,
            jobs = grouped[date].orEmpty()
        )
    }

    val datedKeys = grouped.keys
        .filterNotNull()

    val datedSections = buildList {
        if (today in datedKeys) {
            add(section(today))
        }

        datedKeys
            .filter { date -> date > today }
            .sorted()
            .forEach { date ->
                add(section(date))
            }

        datedKeys
            .filter { date -> date < today }
            .sortedDescending()
            .forEach { date ->
                add(section(date))
            }
    }

    val jobsWithoutDueDate = grouped[null]
        .orEmpty()

    return if (jobsWithoutDueDate.isEmpty()) {
        datedSections
    } else {
        datedSections + UnassignedJobsDateSection(
            date = null,
            jobs = jobsWithoutDueDate
        )
    }
}

internal fun dueDateForSection(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault()
): LocalDate? {
    val instant = value?.let {
        runCatching {
            Instant.parse(it)
        }.getOrNull()
    } ?: return null

    return instant.atZone(zoneId).toLocalDate()
}

internal fun formatSectionDate(
    date: LocalDate,
    locale: Locale = Locale.getDefault()
): String {
    return DateTimeFormatter
        .ofPattern("d.M.yyyy", locale)
        .format(date)
}
