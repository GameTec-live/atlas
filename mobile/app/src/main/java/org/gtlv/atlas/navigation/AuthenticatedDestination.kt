package org.gtlv.atlas.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MainDestination

@Serializable
data object UnassignedJobsDestination

@Serializable
data object NewJobDestination

@Serializable
data class AssignJobDestination(
    val jobId: String
)
