package org.gtlv.core.job

data class JobCoordinates(
    val latitude: Double,
    val longitude: Double
)

data class Job(
    val id: String,
    val assignedDriverId: String?,
    val vehicleId: String?,
    val from: JobCoordinates?,
    val to: JobCoordinates?,
    val fromAddress: String?,
    val toAddress: String?,
    val dueDate: String?,
    val note: String?,
    val startedAt: String?,
    val completedAt: String?,
    val createdAt: String?,
    val updatedAt: String?
)