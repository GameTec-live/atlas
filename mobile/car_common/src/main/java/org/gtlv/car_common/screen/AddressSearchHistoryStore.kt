package org.gtlv.car_common.screen

import android.content.Context
import org.gtlv.core.geoservice.AddressSuggestion

/** Persists a small, ordered list of addresses selected on Android Auto. */
internal class AddressSearchHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun recentAddresses(): List<AddressSuggestion> {
        val count = preferences.getInt(KEY_COUNT, 0)
            .coerceIn(0, MAX_HISTORY_SIZE)
        return (0 until count).mapNotNull(::readAddress)
    }

    fun record(suggestion: AddressSuggestion) {
        val updatedHistory = buildList {
            add(suggestion)
            addAll(
                recentAddresses().filterNot { recent ->
                    recent.matches(suggestion)
                },
            )
        }.take(MAX_HISTORY_SIZE)

        preferences.edit()
            .clear()
            .putInt(KEY_COUNT, updatedHistory.size)
            .also { editor ->
                updatedHistory.forEachIndexed { index, address ->
                    editor
                        .putString(key(index, "id"), address.id)
                        .putString(key(index, "name"), address.displayName)
                        .putString(
                            key(index, "latitude"),
                            address.latitude.toString(),
                        )
                        .putString(
                            key(index, "longitude"),
                            address.longitude.toString(),
                        )
                }
            }
            .apply()
    }

    private fun readAddress(index: Int): AddressSuggestion? {
        val displayName = preferences.getString(key(index, "name"), null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val latitude = preferences
            .getString(key(index, "latitude"), null)
            ?.toDoubleOrNull()
            ?: return null
        val longitude = preferences
            .getString(key(index, "longitude"), null)
            ?.toDoubleOrNull()
            ?: return null

        return AddressSuggestion(
            id = preferences.getString(key(index, "id"), null).orEmpty(),
            displayName = displayName,
            latitude = latitude,
            longitude = longitude,
        )
    }

    private fun AddressSuggestion.matches(other: AddressSuggestion): Boolean =
        (id.isNotBlank() && id == other.id) ||
            displayName.equals(other.displayName, ignoreCase = true) ||
            (latitude == other.latitude && longitude == other.longitude)

    private fun key(index: Int, field: String): String = "address_${index}_$field"

    private companion object {
        const val PREFERENCES_NAME = "atlas_car_address_search_history"
        const val KEY_COUNT = "address_count"
        const val MAX_HISTORY_SIZE = 6
    }
}
