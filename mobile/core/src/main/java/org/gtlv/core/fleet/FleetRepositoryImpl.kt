package org.gtlv.core.fleet

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONArray
import org.json.JSONObject

class FleetRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository: ServerSettingsRepository
) : FleetRepository {
    override suspend fun getVehicleByFingerprint(
        fingerprint: String
    ): VehicleLookupResult = withContext(Dispatchers.IO) {
        val request = requestBuilder(
            "api/fleet/fingerprint",
            fingerprint
        )?.get()?.build()
            ?: return@withContext VehicleLookupResult.InvalidResponse

        try {
            networkClient.okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        VehicleLookupResult.Unauthorized
                    response.code == 404 -> VehicleLookupResult.NotFound
                    !response.isSuccessful ->
                        VehicleLookupResult.ServerError(response.code)
                    else -> parseVehicle(response.body?.string().orEmpty())
                        ?.let(VehicleLookupResult::Success)
                        ?: VehicleLookupResult.InvalidResponse
                }
            }
        } catch (_: IOException) {
            VehicleLookupResult.NetworkError
        }
    }

    override suspend fun getVehicles(): VehiclesResult =
        withContext(Dispatchers.IO) {
            val request = requestBuilder("api/fleet/vehicles")
                ?.get()?.build()
                ?: return@withContext VehiclesResult.InvalidResponse

            try {
                networkClient.okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 || response.code == 403 ->
                            VehiclesResult.Unauthorized
                        !response.isSuccessful ->
                            VehiclesResult.ServerError(response.code)
                        else -> parseVehicleList(response.body?.string().orEmpty())
                            ?.let(VehiclesResult::Success)
                            ?: VehiclesResult.InvalidResponse
                    }
                }
            } catch (_: IOException) {
                VehiclesResult.NetworkError
            }
        }

    override suspend fun getFingerprintCandidates(): VehiclesResult =
        withContext(Dispatchers.IO) {
            val request = requestBuilder("api/fleet/fingerprint/candidates")
                ?.get()?.build()
                ?: return@withContext VehiclesResult.InvalidResponse

            try {
                networkClient.okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 || response.code == 403 ->
                            VehiclesResult.Unauthorized
                        !response.isSuccessful ->
                            VehiclesResult.ServerError(response.code)
                        else -> parseVehicleList(response.body?.string().orEmpty())
                            ?.let(VehiclesResult::Success)
                            ?: VehiclesResult.InvalidResponse
                    }
                }
            } catch (_: IOException) {
                VehiclesResult.NetworkError
            }
        }

    override suspend fun assignFingerprint(
        vehicleId: String,
        fingerprint: String
    ): AssignFingerprintResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("vehicleId", vehicleId)
            .put("fingerprint", fingerprint)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = requestBuilder("api/fleet/fingerprint/pair")
            ?.post(body)?.build()
            ?: return@withContext AssignFingerprintResult.InvalidResponse

        try {
            networkClient.okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        AssignFingerprintResult.Unauthorized
                    response.code == 404 -> AssignFingerprintResult.NotFound
                    response.isSuccessful -> AssignFingerprintResult.Success
                    else -> AssignFingerprintResult.ServerError(response.code)
                }
            }
        } catch (_: IOException) {
            AssignFingerprintResult.NetworkError
        }
    }

    private suspend fun requestBuilder(
        vararg pathSegments: String
    ): Request.Builder? {
        val serverAddress = serverSettingsRepository.serverAddress
            .first().removeSuffix("/")
        val baseUrl = serverAddress.toHttpUrlOrNull() ?: return null
        val urlBuilder = baseUrl.newBuilder()
        pathSegments.forEach { value ->
            urlBuilder.addPathSegmentsSafely(value)
        }

        return Request.Builder()
            .url(urlBuilder.build())
            .header("Origin", serverAddress)
    }

    private fun HttpUrl.Builder.addPathSegmentsSafely(value: String) {
        value.split('/').filter(String::isNotBlank).forEach(::addPathSegment)
    }

    private fun parseVehicleList(text: String): List<Vehicle>? = try {
        val array = JSONArray(text)
        buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: return null
                val vehicleJson = row.optJSONObject("vehicle") ?: row
                add(parseVehicle(vehicleJson) ?: return null)
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun parseVehicle(text: String): Vehicle? = try {
        parseVehicle(JSONObject(text))
    } catch (_: Exception) {
        null
    }

    private fun parseVehicle(json: JSONObject): Vehicle? {
        val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
        return Vehicle(
            id = id,
            fingerprint = json.nullableString("fingerprint"),
            brand = json.optString("brand"),
            model = json.optString("model"),
            year = json.optInt("year"),
            licensePlate = json.optString("licensePlate"),
            odometer = json.nullableDouble("odometer"),
            fuelLevel = json.nullableDouble("fuelLevel")
        )
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf(Double::isFinite)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
