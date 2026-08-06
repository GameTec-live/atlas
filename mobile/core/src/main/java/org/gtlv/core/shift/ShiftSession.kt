package org.gtlv.core.shift

import java.time.Instant

data class ShiftSession(
    val role: ShiftRole,
    val startTimeUtc: Instant

    // frisi: will add the rest at a later time of the project!
)