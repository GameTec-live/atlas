package org.gtlv.core.repository



import org.gtlv.core.network.AtlasServerConfig
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.session.SecureSessionStore
import org.gtlv.core.session.SessionRestoreResult
import org.gtlv.core.settings.ServerSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.flow.first

class AuthRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository: ServerSettingsRepository,
    private val secureSessionStore: SecureSessionStore
) : AuthRepository {

    private var accessToken: String? = null

    override suspend fun login(
        email: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {

        val serverAddress = serverSettingsRepository
            .serverAddress
            .first()
            .removeSuffix("/")

        val loginUrl =
            "$serverAddress/api/auth/sign-in/email"

        val requestJson = JSONObject()
            .put("email", email)
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
                .url("$serverAddress/api/auth/get-session")
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

            val email = user.optString("email")
            val name = user
                .optString("name")
                .ifBlank { email }

            if (name.isBlank()) {
                SessionRestoreResult.InvalidResponse
            } else {
                SessionRestoreResult.Valid(
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
            val token = root.getString("token")
            val user = root.getJSONObject("user")

            val email = user.optString("email")
            val name = user
                .optString("name")
                .ifBlank { email }

            val cookies = networkClient.cookieJar.snapshot()

            secureSessionStore.save(
                token = token,
                cookies = cookies
            )

            accessToken = token

            AuthResult.Success(
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

    internal fun currentAccessToken(): String? {
        return accessToken
    }

    override suspend fun logout() =
        withContext(Dispatchers.IO) {
            clearLocalSession()
        }
}