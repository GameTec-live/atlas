package org.gtlv.core.job

data class AssignedJobNotification(
    val jobId: String,
    val from: String,
    val to: String,
    val note: String? = null
)
