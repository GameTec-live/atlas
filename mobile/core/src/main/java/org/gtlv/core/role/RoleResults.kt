package org.gtlv.core.role

sealed interface RoleAvailabilityResult {

    data class Success(
        val availability: RoleAvailability
    ) : RoleAvailabilityResult

    data object Unauthorized : RoleAvailabilityResult

    data object NetworkError : RoleAvailabilityResult

    data object InvalidResponse : RoleAvailabilityResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : RoleAvailabilityResult
}

sealed interface SelectRoleResult {

    data object Success : SelectRoleResult

    data class RoleUnavailable(
        val message: String?
    ) : SelectRoleResult

    data object Unauthorized : SelectRoleResult

    data object NetworkError : SelectRoleResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : SelectRoleResult
}