package org.gtlv.atlas.unassigned

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.gtlv.core.job.Job

class UnassignedJobsFormattingTest {

    @Test
    fun formatDueDate_formatsIsoInstantInRequestedZone() {
        val formatted = formatDueDate(
            value = "2026-08-01T12:00:00.000Z",
            zoneId = ZoneId.of("Europe/Vienna"),
            locale = Locale.GERMANY
        )

        assertEquals("01.08.26, 14:00", formatted)
    }

    @Test
    fun formatDueDate_keepsUnknownFormatReadable() {
        assertEquals(
            "sometime later",
            formatDueDate(
                value = "sometime later",
                zoneId = ZoneId.of("UTC"),
                locale = Locale.US
            )
        )
    }

    @Test
    fun dueDateForSection_usesTheRequestedLocalZone() {
        assertEquals(
            LocalDate.of(2026, 8, 2),
            dueDateForSection(
                value = "2026-08-01T23:30:00.000Z",
                zoneId = ZoneId.of("Europe/Vienna")
            )
        )
    }

    @Test
    fun formatSectionDate_usesFullYear() {
        assertEquals(
            "8.7.2026",
            formatSectionDate(
                date = LocalDate.of(2026, 7, 8),
                locale = Locale.GERMANY
            )
        )
    }

    @Test
    fun groupJobsByDueDate_putsTodayThenUpcomingThenPast() {
        val sections = groupJobsByDueDate(
            jobs = listOf(
                job(
                    id = "today",
                    dueDate =
                        "2026-08-02T08:00:00.000Z"
                ),
                job(
                    id = "upcoming",
                    dueDate =
                        "2026-08-03T08:00:00.000Z"
                ),
                job(id = "undated", dueDate = null),
                job(
                    id = "past",
                    dueDate =
                        "2026-08-01T08:00:00.000Z"
                ),
                job(
                    id = "older",
                    dueDate =
                        "2026-07-31T08:00:00.000Z"
                )
            ),
            zoneId = ZoneId.of("UTC"),
            today = LocalDate.of(2026, 8, 2)
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 31),
                null
            ),
            sections.map { it.date }
        )

        assertEquals(
            listOf(
                "today",
                "upcoming",
                "past",
                "older",
                "undated"
            ),
            sections.flatMap { section ->
                section.jobs.map(Job::id)
            }
        )
    }

    private fun job(
        id: String,
        dueDate: String?
    ): Job {
        return Job(
            id = id,
            assignedDriverId = null,
            vehicleId = null,
            from = null,
            to = null,
            fromAddress = null,
            toAddress = null,
            dueDate = dueDate,
            note = null,
            startedAt = null,
            completedAt = null,
            createdAt = null,
            updatedAt = null
        )
    }
}
