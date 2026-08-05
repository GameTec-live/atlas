package org.gtlv.core.role

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.gtlv.core.network.AccessTokenProvider
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.gtlv.core.shift.ShiftRole
import org.json.JSONObject
import java.io.IOException
import android.util.Log

class RoleRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository: ServerSettingsRepository,
    private val accessTokenProvider: AccessTokenProvider
) : RoleRepository {

    override suspend fun getAvailability(): RoleAvailabilityResult =
        withContext(Dispatchers.IO) {
            val requestData = createRequestData()
                ?: return@withContext RoleAvailabilityResult.Unauthorized

            val request = Request.Builder()
                .url("${requestData.serverAddress}/roles/")
                .header(
                    "Authorization",
                    "Bearer ${requestData.accessToken}"
                )
                .header(
                    "Origin",
                    requestData.serverAddress
                )
                .get()
                .build()

            try {
                networkClient.okHttpClient
                    .newCall(request)
                    .execute()
                    .use { response ->
                        val responseText =
                            response.body?.string().orEmpty()

                        when {
                            response.code == 401 -> {
                                RoleAvailabilityResult.Unauthorized
                            }

                            !response.isSuccessful -> {
                                RoleAvailabilityResult.ServerError(
                                    statusCode = response.code,
                                    message = readServerMessage(responseText)
                                )
                            }

                            else -> {
                                parseAvailability(responseText)
                            }
                        }
                    }
            } catch (_: IOException) {
                RoleAvailabilityResult.NetworkError
            }
        }

    override suspend fun selectRole(
        role: ShiftRole
    ): SelectRoleResult = withContext(Dispatchers.IO) {
        val requestData = createRequestData()
            ?: return@withContext SelectRoleResult.Unauthorized

        val requestJson = JSONObject()
            .put("role", role.apiValue)
            .toString()

        val requestBody = requestJson.toRequestBody(
            "application/json".toMediaType()
        )

        val request = Request.Builder()
            .url("${requestData.serverAddress}/roles/")
            .header(
                "Authorization",
                "Bearer ${requestData.accessToken}"
            )
            .header(
                "Origin",
                requestData.serverAddress
            )
            .post(requestBody)
            .build()

        try {
            networkClient.okHttpClient
                .newCall(request)
                .execute()
                .use { response ->
                    val responseText =
                        response.body?.string().orEmpty()

                    Log.e(
                        "RoleRepository",
                        "POST /roles/ code=${response.code}, " +
                                "request=$requestJson, body=$responseText"
                    )

                    when {
                        response.isSuccessful -> {
                            SelectRoleResult.Success
                        }

                        response.code == 409 -> {
                            SelectRoleResult.RoleUnavailable(
                                message = readServerMessage(responseText)
                            )
                        }

                        response.code == 401 -> {
                            SelectRoleResult.Unauthorized
                        }

                        else -> {
                            SelectRoleResult.ServerError(
                                statusCode = response.code,
                                message = readServerMessage(responseText)
                            )
                        }
                    }
                }
        } catch (_: IOException) {
            SelectRoleResult.NetworkError
        }
    }

    private suspend fun createRequestData(): RequestData? {
        val token = accessTokenProvider
            .currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val serverAddress = serverSettingsRepository
            .serverAddress
            .first()
            .trim()
            .removeSuffix("/")

        return RequestData(
            serverAddress = serverAddress,
            accessToken = token
        )
    }

    private fun parseAvailability(
        responseText: String
    ): RoleAvailabilityResult {
        return try {
            val root = JSONObject(responseText)

            if (
                !root.has("numFree") ||
                !root.has("free")
            ) {
                return RoleAvailabilityResult.InvalidResponse
            }

            val numFree = root.getInt("numFree")
            val free = root.getBoolean("free")

            if (numFree < 0) {
                return RoleAvailabilityResult.InvalidResponse
            }

            RoleAvailabilityResult.Success(
                availability = RoleAvailability(
                    dispatcherSpotsFree = numFree,
                    dispatcherAvailable =
                        free && numFree > 0
                )
            )
        } catch (_: Exception) {
            RoleAvailabilityResult.InvalidResponse
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

    private data class RequestData(
        val serverAddress: String,
        val accessToken: String
    )
}