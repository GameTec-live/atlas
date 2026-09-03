package org.gtlv.atlas.offboarding

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OffboardingFormattingTest {
    @Test
    fun `formats whole and partial shift hours`() {
        val start = Instant.parse("2026-09-03T07:30:00Z")

        assertEquals(
            "8h",
            formatDuration(start, Instant.parse("2026-09-03T15:30:00Z"))
        )
        assertEquals(
            "9h 30m",
            formatDuration(start, Instant.parse("2026-09-03T17:00:00Z"))
        )
    }
}
