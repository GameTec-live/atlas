package org.gtlv.core.job

data class JobCandidate(
    val driverId: String,
    val driverName: String,
    val rank: Int,
    val summary: String?
)
