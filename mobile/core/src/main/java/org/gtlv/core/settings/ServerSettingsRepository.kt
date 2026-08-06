package org.gtlv.core.settings

import kotlinx.coroutines.flow.Flow

interface ServerSettingsRepository {

    val serverAddress: Flow<String>

    suspend fun setServerAddress(address: String)
}