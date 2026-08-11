package org.gtlv.core.repository



import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.gtlv.core.network.AccessTokenProvider
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.session.SecureSessionStore
import org.gtlv.core.session.SessionRestoreResult
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONObject
import java.io.IOException

class AuthRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository: ServerSettingsRepository,
    private val secureSessionStore: SecureSessionStore
) : AuthRepository, AccessTokenProvider {

    private var accessToken: String? = null

    override suspend fun login(
        username: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {

        val serverAddress = serverSettingsRepository
            .serverAddress
            .first()
            .removeSuffix("/")

        val loginUrl =
            "$serverAddress/api/api/auth/sign-in/username"

        val requestJson = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("rememberMe", true)
            .toString()

        val requestBody = requestJson.toRequestBody(
            "application/json".toMediaType()
        )

        val request = Request.Builder()
            .url(loginUrl)
            .header("Origin", serverAddress)
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
                        response.code == 401 -> {
                            AuthResult.InvalidCredentials
                        }

                        !response.isSuccessful -> {
                            AuthResult.ServerError(
                                statusCode = response.code,
                                message = readServerMessage(responseText)
                            )
                        }

                        else -> {
                            parseSuccessfulLogin(responseText)
                        }
                    }
                }
        } catch (_: IOException) {
            AuthResult.NetworkError
        }
    }

    override suspend fun restoreStoredSession(): SessionRestoreResult =
        withContext(Dispatchers.IO) {
            val storedSession = secureSessionStore.restore()
                ?: return@withContext SessionRestoreResult.NoStoredSession

            networkClient.cookieJar.restore(
                storedSession.cookies
            )

            accessToken = storedSession.token

            val serverAddress = serverSettingsRepository
                .serverAddress
                .first()
                .removeSuffix("/")

            val request = Request.Builder()
                .url("$serverAddress/api/api/auth/get-session")
                .header("Origin", serverAddress)
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
                                clearLocalSession()
                                SessionRestoreResult.Expired
                            }

                            !response.isSuccessful -> {
                                SessionRestoreResult.ServerError(
                                    statusCode = response.code
                                )
                            }

                            responseText.isBlank() ||
                                    responseText.trim() == "null" -> {
                                clearLocalSession()
                                SessionRestoreResult.Expired
                            }

                            else -> {
                                parseRestoredSession(responseText)
                            }
                        }
                    }
            } catch (_: IOException) {
                SessionRestoreResult.NetworkError
            }
        }

    private suspend fun clearLocalSession() {
        accessToken = null
        networkClient.cookieJar.clear()
        secureSessionStore.clear()
    }

    private suspend fun parseRestoredSession(
        responseText: String
    ): SessionRestoreResult {
        return try {
            val root = JSONObject(responseText)
            val user = root.optJSONObject("user")
                ?: return SessionRestoreResult.InvalidResponse

            val userId = user.optString("id")
            val email = user.optString("email")
            val name = user
                .optString("name")
                .ifBlank { email }

            if (userId.isBlank() || name.isBlank()) {
                SessionRestoreResult.InvalidResponse
            } else {
                SessionRestoreResult.Valid(
                    userId = userId,
                    userName = name
                )
            }
        } catch (_: Exception) {
            SessionRestoreResult.InvalidResponse
        }
    }

    private suspend fun parseSuccessfulLogin(
        responseText: String
    ): AuthResult {
        return try {
            val root = JSONObject(responseText)
            val token = root.optString("token")
            val user = root.optJSONObject("user")
                ?: return AuthResult.InvalidResponse

            val userId = user.optString("id")
            val email = user.optString("email")
            val name = user
                .optString("name")
                .ifBlank { email }

            if (
                token.isBlank() ||
                userId.isBlank() ||
                name.isBlank()
            ) {
                return AuthResult.InvalidResponse
            }

            val cookies = networkClient.cookieJar.snapshot()

            secureSessionStore.save(
                token = token,
                cookies = cookies
            )

            accessToken = token

            AuthResult.Success(
                userId = userId,
                userName = name
            )
        } catch (_: Exception) {
            accessToken = null
            networkClient.cookieJar.clear()

            AuthResult.InvalidResponse
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

    override fun currentAccessToken(): String? {
        return accessToken
    }

    override suspend fun logout() =
        withContext(Dispatchers.IO) {
            clearLocalSession()
        }
}