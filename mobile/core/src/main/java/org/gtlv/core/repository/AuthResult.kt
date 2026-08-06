package org.gtlv.core.repository

sealed interface AuthResult {

    data class Success(
        val userId: String,
        val userName: String
    ) : AuthResult

    data object InvalidCredentials : AuthResult

    data object NetworkError : AuthResult

    data object InvalidResponse : AuthResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : AuthResult
}