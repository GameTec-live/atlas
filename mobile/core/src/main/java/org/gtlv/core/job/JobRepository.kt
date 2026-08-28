package org.gtlv.core.job

import kotlinx.coroutines.flow.Flow

interface JobRepository {

    val jobChanges: Flow<Unit>

    suspend fun getJobs(): JobsResult

    suspend fun getUnassignedJobs():
            UnassignedJobsResult

    suspend fun deleteUnassignedJob(
        jobId: String
    ): JobActionResult

    suspend fun getJobCandidates(
        jobId: String
    ): JobCandidatesResult

    suspend fun assignJob(
        jobId: String,
        driverId: String
    ): JobActionResult

    suspend fun startJob(
        jobId: String
    ): JobActionResult

    suspend fun cancelJob(
        jobId: String
    ): JobActionResult

    suspend fun completeJob(
        jobId: String
    ): JobActionResult

    suspend fun updateJobLocation(
        jobId: String,
        field: JobLocationField,
        latitude: Double,
        longitude: Double
    ): JobActionResult

    suspend fun updateJobDetails(
        jobId: String,
        destination: JobCoordinates?,
        dueDate: String
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

sealed interface UnassignedJobsResult {

    data class Success(
        val jobs: List<Job>
    ) : UnassignedJobsResult

    data object Unauthorized :
        UnassignedJobsResult

    data object NetworkError :
        UnassignedJobsResult

    data object InvalidResponse :
        UnassignedJobsResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : UnassignedJobsResult
}

sealed interface JobCandidatesResult {

    data class Success(
        val candidates: List<JobCandidate>
    ) : JobCandidatesResult

    data object Unauthorized : JobCandidatesResult

    data object NetworkError : JobCandidatesResult

    data object InvalidResponse : JobCandidatesResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : JobCandidatesResult
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

/** Makes the application's authenticated job repository available to car sessions. */
interface JobRepositoryProvider {
    val jobRepository: JobRepository
}
