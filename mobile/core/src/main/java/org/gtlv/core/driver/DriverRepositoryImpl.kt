package org.gtlv.core.driver

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONArray
import org.json.JSONException

class DriverRepositoryImpl(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository: ServerSettingsRepository
) : DriverRepository {
    override suspend fun getDrivers(): DriversResult = withContext(Dispatchers.IO) {
        val server = serverSettingsRepository.serverAddress.first()
            .trim().removeSuffix("/")
        val request = Request.Builder()
            .url("$server/api/drivers/")
            .header("Origin", server)
            .get()
            .build()
        try {
            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext DriversResult.Failed
                parseDrivers(response.body?.string().orEmpty())
            }
        } catch (_: IOException) {
            DriversResult.Failed
        }
    }
}

internal fun parseDrivers(body: String): DriversResult = try {
    val array = JSONArray(body)
    val drivers = (0 until array.length()).map { index ->
        val entry = array.getJSONObject(index)
        val id = entry.getString("driverId").trim()
        require(id.isNotEmpty())
        Driver(id, entry.optString("name").trim().ifBlank { id })
    }
    DriversResult.Success(drivers.distinctBy(Driver::id).sortedBy { it.name.lowercase() })
} catch (_: JSONException) {
    DriversResult.Failed
} catch (_: IllegalArgumentException) {
    DriversResult.Failed
}
