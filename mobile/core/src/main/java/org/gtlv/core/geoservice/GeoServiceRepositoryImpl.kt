package org.gtlv.core.geoservice

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONObject

class GeoServiceRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository:
    ServerSettingsRepository
) : GeoServiceRepository {

    override suspend fun resolveAddress(
        address: String
    ): ResolveAddressResult =
        withContext(Dispatchers.IO) {
            val normalizedAddress = address.trim()

            if (normalizedAddress.isEmpty()) {
                return@withContext ResolveAddressResult.Success(
                    suggestions = emptyList()
                )
            }

            val serverAddress = serverSettingsRepository
                .serverAddress
                .first()
                .removeSuffix("/")

            val url = serverAddress
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments(
                    "api/geoservices/resolve"
                )
                ?.addQueryParameter(
                    "address",
                    normalizedAddress
                )
                ?.build()

            if (url == null) {
                return@withContext ResolveAddressResult.InvalidResponse
            }

            val request = Request.Builder()
                .url(url)
                .header("Origin", serverAddress)
                .header("Accept", "application/json")
                .get()
                .build()

            try {
                networkClient.okHttpClient
                    .newCall(request)
                    .execute()
                    .use { response ->
                        val responseText =
                            response.body
                                ?.string()
                                .orEmpty()

                        when {
                            response.code == 401 -> {
                                ResolveAddressResult.Unauthorized
                            }

                            !response.isSuccessful -> {
                                ResolveAddressResult.ServerError(
                                    statusCode = response.code,
                                    message = readServerMessage(
                                        responseText
                                    )
                                )
                            }

                            else -> {
                                parseResponse(responseText)
                            }
                        }
                    }
            } catch (_: IOException) {
                ResolveAddressResult.NetworkError
            } catch (_: Exception) {
                ResolveAddressResult.InvalidResponse
            }
        }

    private fun parseResponse(
        responseText: String
    ): ResolveAddressResult {
        return try {
            val root = JSONObject(responseText)
            val results = root.optJSONArray(
                "results"
            )

            if (results == null) {
                val hasServiceError =
                    root.has("error") ||
                            root.has("message")

                if (!hasServiceError) {
                    return ResolveAddressResult
                        .InvalidResponse
                }

                val message = root
                    .optString("error")
                    .ifBlank {
                        root.optString("message")
                    }
                    .ifBlank { null }

                return ResolveAddressResult
                    .ServiceError(
                        message = message
                    )
            }

            val suggestions = buildList {
                for (
                index in 0 until results.length()
                ) {
                    val json =
                        results.getJSONObject(index)

                    val displayName = json
                        .optString("display_name")
                        .trim()

                    val latitude = json.optDouble(
                        "lat",
                        Double.NaN
                    )

                    val longitude = json.optDouble(
                        "lon",
                        Double.NaN
                    )

                    if (
                        displayName.isBlank() ||
                        latitude.isNaN() ||
                        longitude.isNaN() ||
                        latitude !in -90.0..90.0 ||
                        longitude !in -180.0..180.0
                    ) {
                        continue
                    }

                    val sourceId = json
                        .optString("source_id")
                        .trim()

                    add(
                        AddressSuggestion(
                            id = sourceId.ifBlank {
                                "$displayName:" +
                                        "$latitude:$longitude"
                            },
                            displayName = displayName,
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                }
            }

            ResolveAddressResult.Success(
                suggestions = suggestions
            )
        } catch (_: Exception) {
            ResolveAddressResult.InvalidResponse
        }
    }

    private fun readServerMessage(
        responseText: String
    ): String? {
        return runCatching {
            JSONObject(responseText)
                .optString("message")
                .ifBlank { null }
        }.getOrNull()
    }
}