package org.gtlv.core.job

sealed interface JobNotification {
    val jobId: String
    val from: String
    val to: String?
    val note: String?
}

data class AssignedJobNotification(
    override val jobId: String,
    override val from: String,
    override val to: String?,
    override val note: String? = null
) : JobNotification

data class UnassignedJobNotification(
    override val jobId: String,
    override val from: String,
    override val to: String?,
    override val note: String? = null
) : JobNotification
