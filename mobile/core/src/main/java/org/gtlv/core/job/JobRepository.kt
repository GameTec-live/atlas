package org.gtlv.core.job

interface JobRepository {
    suspend fun getJobs(): JobsResult
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