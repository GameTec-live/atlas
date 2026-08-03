package org.gtlv.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "atlas_settings"

private const val DEFAULT_SERVER_ADDRESS =
    "https://example.com"

private val Context.atlasDataStore by preferencesDataStore(
    name = DATASTORE_NAME
)

class DataStoreServerSettingsRepository(
    context: Context
) : ServerSettingsRepository {

    private val dataStore = context.applicationContext.atlasDataStore

    override val serverAddress: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[SERVER_ADDRESS]
                ?: DEFAULT_SERVER_ADDRESS
        }

    override suspend fun setServerAddress(address: String) {
        val normalizedAddress = normalizeAddress(address)

        dataStore.edit { preferences ->
            preferences[SERVER_ADDRESS] = normalizedAddress
        }
    }

    private fun normalizeAddress(address: String): String {
        return address
            .trim()
            .removeSuffix("/")
    }

    private companion object {
        val SERVER_ADDRESS =
            stringPreferencesKey("server_address")
    }
}