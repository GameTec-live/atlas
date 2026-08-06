package org.gtlv.core.session

sealed interface SessionRestoreResult {

    data class Valid(
        val userId: String,
        val userName: String
    ) : SessionRestoreResult

    data object NoStoredSession : SessionRestoreResult

    data object Expired : SessionRestoreResult

    data object NetworkError : SessionRestoreResult

    data object InvalidResponse : SessionRestoreResult

    data class ServerError(
        val statusCode: Int
    ) : SessionRestoreResult
}