package org.gtlv.core.job

interface JobRepository {

    suspend fun getJobs(): JobsResult

    suspend fun startJob(
        jobId: String
    ): JobActionResult

    suspend fun cancelJob(
        jobId: String
    ): JobActionResult

    suspend fun updateJobLocation(
        jobId: String,
        field: JobLocationField,
        latitude: Double,
        longitude: Double
    ): JobActionResult
}

enum class JobLocationField(
    val apiName: String
) {
    FROM("from"),
    TO("to")
}

sealed interface JobsResult {

    data class Success(
        val queuedJobs: List<Job>,
        val currentJob: Job?
    ) : JobsResult

    data object Unauthorized : JobsResult

    data object NetworkError : JobsResult

    data object InvalidResponse : JobsResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : JobsResult
}

sealed interface JobActionResult {

    data object Success : JobActionResult

    data object Unauthorized : JobActionResult

    data object NetworkError : JobActionResult

    data object InvalidResponse : JobActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : JobActionResult
}