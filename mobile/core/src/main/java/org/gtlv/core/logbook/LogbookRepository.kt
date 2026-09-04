package org.gtlv.core.logbook

import java.time.Instant

data class LogbookSubmission(
    val vehicleId: String,
    val vehicleFingerprint: String?,
    val startedAt: Instant,
    val startOdometer: Long,
    val endOdometer: Long,
    val endedAt: Instant,
    val revenue: Double
)

interface LogbookRepository {
    suspend fun submit(submission: LogbookSubmission): SubmitLogbookResult
}

sealed interface SubmitLogbookResult {
    data object Success : SubmitLogbookResult
    data object Unauthorized : SubmitLogbookResult
    data object InvalidResponse : SubmitLogbookResult
    data object NetworkError : SubmitLogbookResult
    data class ServerError(val statusCode: Int) : SubmitLogbookResult
}

interface LogbookRepositoryProvider {
    val logbookRepository: LogbookRepository
}
