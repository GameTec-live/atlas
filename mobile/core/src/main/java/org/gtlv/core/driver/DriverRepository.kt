package org.gtlv.core.driver

data class Driver(val id: String, val name: String)

sealed interface DriversResult {
    data class Success(val drivers: List<Driver>) : DriversResult
    data object Failed : DriversResult
}

fun interface DriverRepository {
    suspend fun getDrivers(): DriversResult
}
