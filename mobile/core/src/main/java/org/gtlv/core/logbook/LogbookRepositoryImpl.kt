package org.gtlv.core.logbook

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONObject

class LogbookRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository: ServerSettingsRepository
) : LogbookRepository {
    override suspend fun submit(
        submission: LogbookSubmission
    ): SubmitLogbookResult = withContext(Dispatchers.IO) {
        val serverAddress = serverSettingsRepository.serverAddress
            .first()
            .removeSuffix("/")
        val url = serverAddress.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/logbooks/submit")
            ?.build()
            ?: return@withContext SubmitLogbookResult.InvalidResponse

        val requestJson = JSONObject()
            .put("startedAt", submission.startedAt.toString())
            .put("startOdometer", submission.startOdometer)
            .put("endOdometer", submission.endOdometer)
            .put("endedAt", submission.endedAt.toString())
            .put("revenue", submission.revenue)

        requestJson.put("vehicleId", submission.vehicleId)
        submission.vehicleFingerprint?.let {
            requestJson.put("vehicleFingerprint", it)
        }

        val requestBody = requestJson
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .header("Origin", serverAddress)
            .post(requestBody)
            .build()

        try {
            networkClient.okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        SubmitLogbookResult.Unauthorized
                    response.isSuccessful -> SubmitLogbookResult.Success
                    response.code in 400..499 ->
                        SubmitLogbookResult.InvalidResponse
                    else -> SubmitLogbookResult.ServerError(response.code)
                }
            }
        } catch (_: IOException) {
            SubmitLogbookResult.NetworkError
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
