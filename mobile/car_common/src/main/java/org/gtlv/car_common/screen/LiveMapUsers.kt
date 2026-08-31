package org.gtlv.car_common.screen

import org.gtlv.core.telemetry.LiveMapUser

internal fun Collection<LiveMapUser>.excludingUser(
    userId: String?,
): List<LiveMapUser> = filter { user -> user.userId != userId }
