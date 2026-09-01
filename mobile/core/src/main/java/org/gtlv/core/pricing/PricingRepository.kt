package org.gtlv.core.pricing

interface PricingRepository {
    suspend fun getPricePerKilometer(): PriceResult
}

sealed interface PriceResult {
    data class Success(
        val pricePerKilometer: Double
    ) : PriceResult

    data object Unavailable : PriceResult
}

interface PricingRepositoryProvider {
    val pricingRepository: PricingRepository
}
