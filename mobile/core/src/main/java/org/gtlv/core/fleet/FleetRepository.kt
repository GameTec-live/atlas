package org.gtlv.core.fleet

interface FleetRepository {
    suspend fun getVehicleByFingerprint(
        fingerprint: String
    ): VehicleLookupResult

    suspend fun getVehicles(): VehiclesResult

    suspend fun assignFingerprint(
        vehicleId: String,
        fingerprint: String
    ): AssignFingerprintResult
}

sealed interface VehicleLookupResult {
    data class Success(val vehicle: Vehicle) : VehicleLookupResult
    data object NotFound : VehicleLookupResult
    data object Unauthorized : VehicleLookupResult
    data object NetworkError : VehicleLookupResult
    data object InvalidResponse : VehicleLookupResult
    data class ServerError(val statusCode: Int) : VehicleLookupResult
}

sealed interface VehiclesResult {
    data class Success(val vehicles: List<Vehicle>) : VehiclesResult
    data object Unauthorized : VehiclesResult
    data object NetworkError : VehiclesResult
    data object InvalidResponse : VehiclesResult
    data class ServerError(val statusCode: Int) : VehiclesResult
}

sealed interface AssignFingerprintResult {
    data object Success : AssignFingerprintResult
    data object NotFound : AssignFingerprintResult
    data object Unauthorized : AssignFingerprintResult
    data object NetworkError : AssignFingerprintResult
    data object InvalidResponse : AssignFingerprintResult
    data class ServerError(val statusCode: Int) : AssignFingerprintResult
}

interface FleetRepositoryProvider {
    val fleetRepository: FleetRepository
}
