package org.gtlv.core.fleet

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.connectedVehicleDataStore by preferencesDataStore(
    name = "connected_vehicle"
)

interface ConnectedVehicleCache {
    suspend fun restore(fingerprint: String): Vehicle?
    suspend fun save(fingerprint: String, vehicle: Vehicle)
}

class ConnectedVehicleStore(context: Context) : ConnectedVehicleCache {
    private val dataStore = context.applicationContext.connectedVehicleDataStore

    override suspend fun restore(fingerprint: String): Vehicle? {
        val values = dataStore.data.first()
        if (values[FINGERPRINT] != fingerprint) return null
        val id = values[ID] ?: return null
        return Vehicle(
            id = id,
            fingerprint = fingerprint,
            brand = values[BRAND].orEmpty(),
            model = values[MODEL].orEmpty(),
            year = values[YEAR] ?: 0,
            licensePlate = values[LICENSE_PLATE].orEmpty(),
            odometer = values[ODOMETER],
            fuelLevel = values[FUEL_LEVEL]
        )
    }

    override suspend fun save(fingerprint: String, vehicle: Vehicle) {
        dataStore.edit { values ->
            values[FINGERPRINT] = fingerprint
            values[ID] = vehicle.id
            values[BRAND] = vehicle.brand
            values[MODEL] = vehicle.model
            values[YEAR] = vehicle.year
            values[LICENSE_PLATE] = vehicle.licensePlate
            vehicle.odometer?.let { values[ODOMETER] = it } ?: values.remove(ODOMETER)
            vehicle.fuelLevel?.let { values[FUEL_LEVEL] = it } ?: values.remove(FUEL_LEVEL)
        }
    }

    private companion object {
        val FINGERPRINT = stringPreferencesKey("fingerprint")
        val ID = stringPreferencesKey("vehicle_id")
        val BRAND = stringPreferencesKey("brand")
        val MODEL = stringPreferencesKey("model")
        val YEAR = intPreferencesKey("year")
        val LICENSE_PLATE = stringPreferencesKey("license_plate")
        val ODOMETER = doublePreferencesKey("odometer")
        val FUEL_LEVEL = doublePreferencesKey("fuel_level")
    }
}
