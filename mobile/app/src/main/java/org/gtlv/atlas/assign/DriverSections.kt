package org.gtlv.atlas.assign

import org.gtlv.core.job.JobCandidate

internal fun offlineDrivers(
    directory: List<JobCandidate>,
    online: List<JobCandidate>,
    candidates: List<JobCandidate>
): List<JobCandidate> {
    val activeIds = (online + candidates).mapTo(mutableSetOf()) { it.driverId }
    return directory.filterNot { it.driverId in activeIds }
        .distinctBy { it.driverId }
        .sortedBy { it.driverName.lowercase() }
}
