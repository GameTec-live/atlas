package org.gtlv.atlas.main

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.gtlv.core.job.Job

internal fun Job.isDueToday(
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Boolean = dueDate?.let { value ->
    runCatching { Instant.parse(value).atZone(zone).toLocalDate() }
        .getOrElse { runCatching { LocalDate.parse(value) }.getOrNull() } == today
} ?: false

internal fun MainScreenUiState.visibleQueuedJobs(
    today: LocalDate = LocalDate.now()
): List<Job> = if (showAllJobs) queuedJobs else queuedJobs.filter { it.isDueToday(today) }
