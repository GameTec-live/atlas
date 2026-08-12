package org.gtlv.core.job

interface JobRepository {

    suspend fun getJobs(): JobsResult

    suspend fun startJob(
        jobId: String
    ): StartJobResult
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

sealed interface StartJobResult {

    data object Success : StartJobResult

    data object Unauthorized : StartJobResult

    data object NetworkError : StartJobResult

    data object InvalidResponse : StartJobResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : StartJobResult
}