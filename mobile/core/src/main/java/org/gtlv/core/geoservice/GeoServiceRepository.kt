package org.gtlv.core.geoservice

interface GeoServiceRepository {

    suspend fun resolveAddress(
        address: String
    ): ResolveAddressResult

    suspend fun requestRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        language: String = "en",
        headingDegrees: Int? = null
    ): RouteResult
}

/** Exposes the process-wide routing service to app surfaces such as Android Auto. */
interface GeoServiceRepositoryProvider {
    val geoServiceRepository: GeoServiceRepository
}

sealed interface ResolveAddressResult {

    data class Success(
        val suggestions: List<AddressSuggestion>
    ) : ResolveAddressResult

    data object Unauthorized : ResolveAddressResult

    data object NetworkError : ResolveAddressResult

    data object InvalidResponse : ResolveAddressResult

    data class ServiceError(
        val message: String?
    ) : ResolveAddressResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : ResolveAddressResult
}

sealed interface RouteResult {

    data class Success(
        val route: Route
    ) : RouteResult

    data object Unauthorized : RouteResult

    data object NetworkError : RouteResult

    data object MalformedJson : RouteResult

    data object InvalidResponse : RouteResult

    data class RouterError(
        val errorCode: Int?,
        val statusCode: Int,
        val message: String?,
        val status: String?
    ) : RouteResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : RouteResult
}
