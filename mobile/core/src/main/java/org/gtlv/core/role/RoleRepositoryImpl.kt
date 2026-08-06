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

                    when {
                        response.isSuccessful -> {
                            SelectRoleResult.Success
                        }

                        response.code == 409 || response.code == 418 -> {
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
                !root.has("free") ||
                !root.has("roles")
            ) {
                return RoleAvailabilityResult.InvalidResponse
            }

            val numFree = root.getInt("numFree")
            val free = root.getBoolean("free")
            val rolesJson = root.getJSONArray("roles")

            if (numFree < 0) {
                return RoleAvailabilityResult.InvalidResponse
            }

            val assignedRoles = mutableListOf<AssignedRole>()

            for (index in 0 until rolesJson.length()) {
                val roleJson = rolesJson.getJSONObject(index)

                val driverId = roleJson
                    .optString("driverId")
                    .trim()

                val roleValue = roleJson
                    .optString("role")
                    .trim()
                    .lowercase()

                val name = roleJson
                    .optString("name")
                    .trim()
                    .ifBlank { null }

                if (driverId.isBlank()) {
                    return RoleAvailabilityResult.InvalidResponse
                }

                val role = when (roleValue) {
                    "driver" -> ShiftRole.DRIVER
                    "dispatcher" -> ShiftRole.DISPATCHER

                    else -> {
                        return RoleAvailabilityResult.InvalidResponse
                    }
                }

                assignedRoles.add(
                    AssignedRole(
                        driverId = driverId,
                        role = role,
                        name = name
                    )
                )
            }

            RoleAvailabilityResult.Success(
                availability = RoleAvailability(
                    dispatcherSpotsFree = numFree,
                    dispatcherAvailable = free && numFree > 0,
                    assignedRoles = assignedRoles
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
            val root = JSONObject(responseText)

            root.optString("message")
                .ifBlank { root.optString("error") }
                .ifBlank { null }
        }.getOrNull()
    }

    private data class RequestData(
        val serverAddress: String,
        val accessToken: String
    )
}