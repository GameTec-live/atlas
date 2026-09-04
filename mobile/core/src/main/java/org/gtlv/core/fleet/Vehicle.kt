package org.gtlv.core.fleet

data class Vehicle(
    val id: String,
    val fingerprint: String?,
    val brand: String,
    val model: String,
    val year: Int,
    val licensePlate: String,
    val odometer: Double?,
    val fuelLevel: Double?
) {
    val displayName: String
        get() = listOf(brand, model)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { licensePlate }
}
