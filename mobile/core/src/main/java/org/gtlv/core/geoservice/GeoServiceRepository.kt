package org.gtlv.core.geoservice

interface GeoServiceRepository {

    suspend fun resolveAddress(
        address: String
    ): ResolveAddressResult
}

sealed interface ResolveAddressResult {

    data class Success(
        val suggestions: List<AddressSuggestion>
    ) : ResolveAddressResult

    data object Unauthorized : ResolveAddressResult

    data object NetworkError : ResolveAddressResult

    data object InvalidResponse : ResolveAddressResult

    data class ServerError(
        val statusCode: Int,
        val message: String?
    ) : ResolveAddressResult
}