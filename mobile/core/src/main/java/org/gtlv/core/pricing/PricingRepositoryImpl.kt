package org.gtlv.core.pricing

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONObject

class PricingRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository:
    ServerSettingsRepository
) : PricingRepository {

    override suspend fun getPricePerKilometer():
            PriceResult = withContext(Dispatchers.IO) {
        val serverAddress = serverSettingsRepository
            .serverAddress
            .first()
            .trim()
            .removeSuffix("/")

        if (serverAddress.isBlank()) {
            return@withContext PriceResult.Unavailable
        }

        val request = runCatching {
            Request.Builder()
                .url("$serverAddress/api/config/price")
                .header("Origin", serverAddress)
                .get()
                .build()
        }.getOrNull()
            ?: return@withContext PriceResult.Unavailable

        try {
            networkClient.okHttpClient
                .newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        return@withContext PriceResult.Unavailable
                    }

                    parsePrice(
                        response.body?.string().orEmpty()
                    )
                }
        } catch (_: IOException) {
            PriceResult.Unavailable
        }
    }

    private fun parsePrice(responseText: String): PriceResult {
        return runCatching {
            val value = JSONObject(responseText)
                .getDouble("pricePerKilometer")

            if (!value.isFinite() || value < 0.0) {
                PriceResult.Unavailable
            } else {
                PriceResult.Success(value)
            }
        }.getOrDefault(PriceResult.Unavailable)
    }
}
