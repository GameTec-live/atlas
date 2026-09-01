package org.gtlv.core.shift

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.Instant

private val Context.shiftSessionDataStore by preferencesDataStore(
    name = "shift_session"
)

class DataStoreShiftSessionStore(
    context: Context
) : ShiftSessionStore {

    private val dataStore =
        context.applicationContext.shiftSessionDataStore

    override suspend fun restore(): ShiftSession? {
        val preferences = dataStore.data.first()

        val roleValue = preferences[ROLE_KEY]
            ?: return null

        val startTimeValue = preferences[START_TIME_KEY]
            ?: return null

        val role = ShiftRole.fromApiValue(roleValue)
            ?: return null

        val startTime = runCatching {
            Instant.parse(startTimeValue)
        }.getOrNull() ?: return null

        return ShiftSession(
            role = role,
            startTimeUtc = startTime,
            startKilometer = preferences[START_KILOMETER_KEY]
        )
    }

    override suspend fun save(session: ShiftSession) {
        dataStore.edit { preferences ->
            preferences[ROLE_KEY] =
                session.role.apiValue

            preferences[START_TIME_KEY] =
                session.startTimeUtc.toString()

            if (session.startKilometer == null) {
                preferences.remove(START_KILOMETER_KEY)
            } else {
                preferences[START_KILOMETER_KEY] =
                    session.startKilometer
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(ROLE_KEY)
            preferences.remove(START_TIME_KEY)
            preferences.remove(START_KILOMETER_KEY)
        }
    }

    private companion object {
        val ROLE_KEY =
            stringPreferencesKey("shift_role")

        val START_TIME_KEY =
            stringPreferencesKey("shift_start_time_utc")

        val START_KILOMETER_KEY =
            doublePreferencesKey("shift_start_kilometer")
    }
}
