package org.gtlv.core.geoservice

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeoServiceRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository:
    ServerSettingsRepository
) : GeoServiceRepository {

    override suspend fun requestRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        language: String
    ): RouteResult = withContext(Dispatchers.IO) {
        if (
            !origin.isValid() ||
            !destination.isValid() ||
            language.length !in 2..5
        ) {
            return@withContext RouteResult.InvalidResponse
        }

        val serverAddress = serverSettingsRepository
            .serverAddress
            .first()
            .removeSuffix("/")

        val url = serverAddress
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/geoservices/route")
            ?.addQueryParameter("fromlat", origin.latitude.toString())
            ?.addQueryParameter("fromlon", origin.longitude.toString())
            ?.addQueryParameter(
                "tolat",
                destination.latitude.toString()
            )
            ?.addQueryParameter(
                "tolon",
                destination.longitude.toString()
            )
            ?.addQueryParameter("lang", language)
            ?.build()
            ?: return@withContext RouteResult.InvalidResponse

        val request = Request.Builder()
            .url(url)
            .header("Origin", serverAddress)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            executeCancellable(request).use { response ->
                val responseText = response.body
                    ?.string()
                    .orEmpty()

                when {
                    response.code == 401 -> {
                        RouteResult.Unauthorized
                    }

                    responseText.isBlank() -> {
                        RouteResult.InvalidResponse
                    }

                    else -> {
                        RouteResponseParser.parse(
                            responseText = responseText,
                            httpStatusCode = response.code
                        )
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            RouteResult.NetworkError
        } catch (_: Exception) {
            RouteResult.InvalidResponse
        }
    }

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

    private suspend fun executeCancellable(
        request: Request
    ): Response = suspendCancellableCoroutine { continuation ->
        val call = networkClient.okHttpClient.newCall(request)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }
}
