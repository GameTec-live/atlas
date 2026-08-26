package org.gtlv.core.job

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONArray
import org.json.JSONObject

class JobRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository:
    ServerSettingsRepository
) : JobRepository {

    private val _jobChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1
    )

    override val jobChanges: SharedFlow<Unit> =
        _jobChanges.asSharedFlow()

    override suspend fun getJobs(): JobsResult =
        withContext(Dispatchers.IO) {
            val serverAddress = serverSettingsRepository
                .serverAddress
                .first()
                .removeSuffix("/")

            val assignedUrl = createUrl(
                serverAddress = serverAddress,
                path = "api/jobs/assigned"
            ) ?: return@withContext JobsResult.InvalidResponse

            val currentUrl = createUrl(
                serverAddress = serverAddress,
                path = "api/jobs/current"
            ) ?: return@withContext JobsResult.InvalidResponse

            when (
                val assignedResult = requestAssignedJobs(
                    url = assignedUrl,
                    serverAddress = serverAddress
                )
            ) {
                is EndpointResult.Success -> {
                    when (
                        val currentResult = requestCurrentJob(
                            url = currentUrl,
                            serverAddress = serverAddress
                        )
                    ) {
                        is EndpointResult.Success -> {
                            val currentJob = currentResult.value

                            JobsResult.Success(
                                queuedJobs =
                                    assignedResult.value.pendingJobs(),
                                currentJob = currentJob
                            )
                        }

                        EndpointResult.NotFound -> {
                            JobsResult.Success(
                                queuedJobs =
                                    assignedResult.value.pendingJobs(),
                                currentJob = null
                            )
                        }

                        is EndpointResult.Failure -> {
                            currentResult.toJobsResult()
                        }
                    }
                }

                is EndpointResult.Failure -> {
                    assignedResult.toJobsResult()
                }

                EndpointResult.NotFound -> {
                    JobsResult.InvalidResponse
                }
            }
        }

    override suspend fun getUnassignedJobs():
            UnassignedJobsResult =
        withContext(Dispatchers.IO) {
            val serverAddress = serverSettingsRepository
                .serverAddress
                .first()
                .removeSuffix("/")

            val unassignedUrl = createUrl(
                serverAddress = serverAddress,
                path = "api/jobs/unassigned"
            ) ?: return@withContext UnassignedJobsResult
                .InvalidResponse

            when (
                val result = executeRequest(
                    url = unassignedUrl,
                    serverAddress = serverAddress
                ) { responseText ->
                    parseJobs(responseText)
                }
            ) {
                is EndpointResult.Success -> {
                    UnassignedJobsResult.Success(
                        jobs = result.value
                    )
                }

                is EndpointResult.Failure -> {
                    result.toUnassignedJobsResult()
                }

                EndpointResult.NotFound -> {
                    UnassignedJobsResult.InvalidResponse
                }
            }
        }

    override suspend fun startJob(
        jobId: String
    ): JobActionResult {
        return executeAndNotifyJobAction(
            jobId = jobId,
            action = "start"
        )
    }

    override suspend fun deleteUnassignedJob(
        jobId: String
    ): JobActionResult {
        val result = executeDeleteJob(jobId)

        if (result == JobActionResult.Success) {
            _jobChanges.emit(Unit)
        }

        return result
    }

    override suspend fun cancelJob(
        jobId: String
    ): JobActionResult {
        return executeAndNotifyJobAction(
            jobId = jobId,
            action = "cancel"
        )
    }

    override suspend fun completeJob(
        jobId: String
    ): JobActionResult {
        return executeAndNotifyJobAction(
            jobId = jobId,
            action = "complete"
        )
    }

    private suspend fun executeAndNotifyJobAction(
        jobId: String,
        action: String
    ): JobActionResult {
        val result = executeJobAction(
            jobId = jobId,
            action = action
        )

        if (result == JobActionResult.Success) {
            _jobChanges.emit(Unit)
        }

        return result
    }

    override suspend fun updateJobLocation(
        jobId: String,
        field: JobLocationField,
        latitude: Double,
        longitude: Double
    ): JobActionResult = withContext(Dispatchers.IO) {
        if (
            jobId.isBlank() ||
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            return@withContext JobActionResult.InvalidResponse
        }

        val serverAddress = serverSettingsRepository
            .serverAddress
            .first()
            .removeSuffix("/")

        val updateUrl = serverAddress
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/jobs")
            ?.addPathSegment(jobId)
            ?.build()

        if (updateUrl == null) {
            return@withContext JobActionResult.InvalidResponse
        }

        val coordinates = JSONArray()
            .put(latitude)
            .put(longitude)

        val requestJson = JSONObject()
            .put(field.apiName, coordinates)
            .toString()

        val requestBody = requestJson.toRequestBody(
            "application/json".toMediaType()
        )

        val request = Request.Builder()
            .url(updateUrl)
            .header("Origin", serverAddress)
            .header("Accept", "application/json")
            .put(requestBody)
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
                            JobActionResult.Unauthorized
                        }

                        !response.isSuccessful -> {
                            JobActionResult.ServerError(
                                statusCode = response.code,
                                message = readServerMessage(
                                    responseText
                                )
                            )
                        }

                        else -> {
                            JobActionResult.Success
                        }
                    }
                }
        } catch (_: IOException) {
            JobActionResult.NetworkError
        } catch (_: Exception) {
            JobActionResult.InvalidResponse
        }
    }

    private suspend fun executeJobAction(
        jobId: String,
        action: String
    ): JobActionResult = withContext(Dispatchers.IO) {
        if (jobId.isBlank()) {
            return@withContext JobActionResult.InvalidResponse
        }

        val serverAddress = serverSettingsRepository
            .serverAddress
            .first()
            .removeSuffix("/")

        val actionUrl = serverAddress
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/jobs")
            ?.addPathSegment(jobId)
            ?.addPathSegment(action)
            ?.build()
            ?: return@withContext JobActionResult.InvalidResponse

        val emptyRequestBody = ByteArray(0).toRequestBody(
            "application/json".toMediaType()
        )

        val request = Request.Builder()
            .url(actionUrl)
            .header("Origin", serverAddress)
            .header("Accept", "application/json")
            .post(emptyRequestBody)
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
                            JobActionResult.Unauthorized
                        }

                        !response.isSuccessful -> {
                            JobActionResult.ServerError(
                                statusCode = response.code,
                                message = readServerMessage(
                                    responseText
                                )
                            )
                        }

                        else -> {
                            JobActionResult.Success
                        }
                    }
                }
        } catch (_: IOException) {
            JobActionResult.NetworkError
        } catch (_: Exception) {
            JobActionResult.InvalidResponse
        }
    }

    private suspend fun executeDeleteJob(
        jobId: String
    ): JobActionResult = withContext(Dispatchers.IO) {
        if (jobId.isBlank()) {
            return@withContext JobActionResult.InvalidResponse
        }

        val serverAddress = serverSettingsRepository
            .serverAddress
            .first()
            .removeSuffix("/")

        val deleteUrl = serverAddress
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/jobs")
            ?.addPathSegment(jobId)
            ?.build()
            ?: return@withContext JobActionResult.InvalidResponse

        val request = Request.Builder()
            .url(deleteUrl)
            .header("Origin", serverAddress)
            .header("Accept", "application/json")
            .delete()
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
                            JobActionResult.Unauthorized
                        }

                        !response.isSuccessful -> {
                            JobActionResult.ServerError(
                                statusCode = response.code,
                                message = readServerMessage(
                                    responseText
                                )
                            )
                        }

                        else -> {
                            JobActionResult.Success
                        }
                    }
                }
        } catch (_: IOException) {
            JobActionResult.NetworkError
        } catch (_: Exception) {
            JobActionResult.InvalidResponse
        }
    }

    private fun createUrl(
        serverAddress: String,
        path: String
    ): HttpUrl? {
        return serverAddress
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments(path)
            ?.addQueryParameter("geocode", null)
            ?.build()
    }

    private fun requestAssignedJobs(
        url: HttpUrl,
        serverAddress: String
    ): EndpointResult<List<Job>> {
        return executeRequest(
            url = url,
            serverAddress = serverAddress
        ) { responseText ->
            parseJobs(responseText)
        }
    }

    private fun requestCurrentJob(
        url: HttpUrl,
        serverAddress: String
    ): EndpointResult<Job> {
        return executeRequest(
            url = url,
            serverAddress = serverAddress,
            allowNotFound = true
        ) { responseText ->
            parseJob(JSONObject(responseText))
                ?: throw IllegalArgumentException(
                    "Invalid job response"
                )
        }
    }

    private fun <T> executeRequest(
        url: HttpUrl,
        serverAddress: String,
        allowNotFound: Boolean = false,
        parse: (String) -> T
    ): EndpointResult<T> {
        val request = Request.Builder()
            .url(url)
            .header("Origin", serverAddress)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            networkClient.okHttpClient
                .newCall(request)
                .execute()
                .use { response ->
                    response.toEndpointResult(
                        allowNotFound = allowNotFound,
                        parse = parse
                    )
                }
        } catch (_: IOException) {
            EndpointResult.Failure.Network
        } catch (_: Exception) {
            EndpointResult.Failure.InvalidResponse
        }
    }

    private fun <T> Response.toEndpointResult(
        allowNotFound: Boolean,
        parse: (String) -> T
    ): EndpointResult<T> {
        val responseText = body?.string().orEmpty()

        return when {
            code == 401 -> {
                EndpointResult.Failure.Unauthorized
            }

            code == 404 && allowNotFound -> {
                EndpointResult.NotFound
            }

            !isSuccessful -> {
                EndpointResult.Failure.Server(
                    statusCode = code,
                    message = readServerMessage(
                        responseText
                    )
                )
            }

            else -> {
                runCatching {
                    parse(responseText)
                }.fold(
                    onSuccess = {
                        EndpointResult.Success(it)
                    },
                    onFailure = {
                        EndpointResult.Failure.InvalidResponse
                    }
                )
            }
        }
    }

    private fun parseJobs(
        responseText: String
    ): List<Job> {
        val array = JSONArray(responseText)

        return buildList {
            for (index in 0 until array.length()) {
                val job = parseJob(
                    array.getJSONObject(index)
                ) ?: throw IllegalArgumentException(
                    "Invalid job"
                )

                add(job)
            }
        }
    }

    private fun List<Job>.pendingJobs(): List<Job> {
        return filter { job ->
            job.startedAt == null &&
                    job.completedAt == null
        }
    }

    private fun parseJob(
        json: JSONObject
    ): Job? {
        val id = json.optString("id")

        if (id.isBlank()) {
            return null
        }

        return Job(
            id = id,
            assignedDriverId =
                json.nullableString("assignedDriverId"),
            vehicleId =
                json.nullableString("vehicleId"),
            from =
                json.coordinatesOrNull("from"),
            to =
                json.coordinatesOrNull("to"),
            fromAddress =
                json.nullableString("fromAddress"),
            toAddress =
                json.nullableString("toAddress"),
            dueDate =
                json.nullableString("dueDate"),
            note =
                json.nullableString("note"),
            startedAt =
                json.nullableString("startedAt"),
            completedAt =
                json.nullableString("completedAt"),
            createdAt =
                json.nullableString("createdAt"),
            updatedAt =
                json.nullableString("updatedAt")
        )
    }

    private fun JSONObject.coordinatesOrNull(
        name: String
    ): JobCoordinates? {
        val coordinates = optJSONArray(name)
            ?: return null

        if (coordinates.length() < 2) {
            return null
        }

        val latitude = coordinates.optDouble(
            0,
            Double.NaN
        )

        val longitude = coordinates.optDouble(
            1,
            Double.NaN
        )

        if (
            latitude.isNaN() ||
            longitude.isNaN()
        ) {
            return null
        }

        return JobCoordinates(
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun JSONObject.nullableString(
        name: String
    ): String? {
        if (isNull(name)) {
            return null
        }

        return optString(name)
            .ifBlank { null }
    }

    private fun readServerMessage(
        responseText: String
    ): String? {
        return runCatching {
            val json = JSONObject(responseText)

            json.optString("message")
                .ifBlank {
                    json.optString("error")
                }
                .ifBlank { null }
        }.getOrNull()
    }

    private sealed interface EndpointResult<out T> {

        data class Success<T>(
            val value: T
        ) : EndpointResult<T>

        data object NotFound :
            EndpointResult<Nothing>

        sealed interface Failure :
            EndpointResult<Nothing> {

            data object Unauthorized : Failure

            data object Network : Failure

            data object InvalidResponse : Failure

            data class Server(
                val statusCode: Int,
                val message: String?
            ) : Failure
        }
    }

    private fun EndpointResult.Failure.toJobsResult():
            JobsResult {
        return when (this) {
            EndpointResult.Failure.Unauthorized ->
                JobsResult.Unauthorized

            EndpointResult.Failure.Network ->
                JobsResult.NetworkError

            EndpointResult.Failure.InvalidResponse ->
                JobsResult.InvalidResponse

            is EndpointResult.Failure.Server ->
                JobsResult.ServerError(
                    statusCode = statusCode,
                    message = message
                )
        }
    }

    private fun EndpointResult.Failure
        .toUnassignedJobsResult(): UnassignedJobsResult {
        return when (this) {
            EndpointResult.Failure.Unauthorized ->
                UnassignedJobsResult.Unauthorized

            EndpointResult.Failure.Network ->
                UnassignedJobsResult.NetworkError

            EndpointResult.Failure.InvalidResponse ->
                UnassignedJobsResult.InvalidResponse

            is EndpointResult.Failure.Server ->
                UnassignedJobsResult.ServerError(
                    statusCode = statusCode,
                    message = message
                )
        }
    }
}
