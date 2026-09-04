package org.gtlv.core.fleet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.gtlv.core.session.SessionState
import org.gtlv.core.telemetry.TelemetryData
import org.gtlv.core.telemetry.TelemetryProvider
import org.gtlv.core.telemetry.TelemetryVehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectedVehicleManagerTest {
    @Test
    fun `admin can pair an unknown fingerprint and resolve the fleet id`() =
        runTest {
            val telemetry = FakeTelemetryProvider("fingerprint-1")
            val repository = FakeFleetRepository()
            val cache = FakeVehicleCache()
            val manager = ConnectedVehicleManager(
                telemetryProvider = telemetry,
                sessionState = MutableStateFlow(
                    SessionState.SignedIn(
                        userId = "admin-1",
                        userName = "Admin",
                        isAdmin = true
                    )
                ),
                fleetRepository = repository,
                store = cache
            )

            manager.start(backgroundScope)
            runCurrent()

            val pairing = manager.state.value as ConnectedVehicleState.PairingRequired
            assertEquals(listOf(UNPAIRED_VEHICLE), pairing.candidates)

            repository.lookupResult = VehicleLookupResult.Success(
                PAIRED_VEHICLE
            )
            manager.pair(UNPAIRED_VEHICLE.id)

            assertEquals(
                ConnectedVehicleState.Connected(
                    fingerprint = "fingerprint-1",
                    vehicle = PAIRED_VEHICLE
                ),
                manager.state.value
            )
            assertEquals(
                UNPAIRED_VEHICLE.id to "fingerprint-1",
                repository.assignedFingerprint
            )
            assertEquals(PAIRED_VEHICLE.id, telemetry.lastResolvedVehicleId)
            assertEquals(PAIRED_VEHICLE, cache.savedVehicle)
        }

    @Test
    fun `non-admin does not receive pairing candidates`() = runTest {
        val repository = FakeFleetRepository()
        val manager = ConnectedVehicleManager(
            telemetryProvider = FakeTelemetryProvider("fingerprint-1"),
            sessionState = MutableStateFlow(
                SessionState.SignedIn("user-1", "Driver")
            ),
            fleetRepository = repository,
            store = FakeVehicleCache()
        )

        manager.start(backgroundScope)
        runCurrent()

        assertTrue(manager.state.value is ConnectedVehicleState.Unavailable)
        assertEquals(0, repository.pairingCandidateRequests)
    }

    @Test
    fun `failed server validation never activates a cached vehicle`() = runTest {
        val telemetry = FakeTelemetryProvider("fingerprint-1")
        val repository = FakeFleetRepository().apply {
            lookupResult = VehicleLookupResult.NetworkError
        }
        val cache = FakeVehicleCache(restoredVehicle = PAIRED_VEHICLE)
        val manager = ConnectedVehicleManager(
            telemetryProvider = telemetry,
            sessionState = MutableStateFlow(
                SessionState.SignedIn("user-1", "Driver")
            ),
            fleetRepository = repository,
            store = cache
        )

        manager.start(backgroundScope)
        runCurrent()

        assertTrue(manager.state.value is ConnectedVehicleState.Unavailable)
        assertFalse(telemetry.resolvedVehicleIds.contains(PAIRED_VEHICLE.id))
        assertEquals("fingerprint-1", cache.clearedFingerprint)
    }

    private class FakeFleetRepository : FleetRepository {
        var lookupResult: VehicleLookupResult = VehicleLookupResult.NotFound
        var assignedFingerprint: Pair<String, String>? = null
        var pairingCandidateRequests = 0

        override suspend fun getVehicleByFingerprint(
            fingerprint: String
        ) = lookupResult

        override suspend fun getVehicles(): VehiclesResult {
            return VehiclesResult.Success(
                listOf(UNPAIRED_VEHICLE, PAIRED_VEHICLE)
            )
        }

        override suspend fun getFingerprintCandidates(): VehiclesResult {
            pairingCandidateRequests += 1
            return VehiclesResult.Success(listOf(UNPAIRED_VEHICLE))
        }

        override suspend fun assignFingerprint(
            vehicleId: String,
            fingerprint: String
        ): AssignFingerprintResult {
            assignedFingerprint = vehicleId to fingerprint
            return AssignFingerprintResult.Success
        }
    }

    private class FakeVehicleCache(
        private val restoredVehicle: Vehicle? = null
    ) : ConnectedVehicleCache {
        var savedVehicle: Vehicle? = null
        var clearedFingerprint: String? = null
        override suspend fun restore(fingerprint: String): Vehicle? = restoredVehicle
        override suspend fun save(fingerprint: String, vehicle: Vehicle) {
            savedVehicle = vehicle
        }
        override suspend fun clear(fingerprint: String) {
            clearedFingerprint = fingerprint
        }
    }

    private class FakeTelemetryProvider(
        fingerprint: String?
    ) : TelemetryProvider {
        override val telemetry = MutableStateFlow<TelemetryData?>(null)
        override val odometerKilometers = MutableStateFlow<Double?>(null)
        override val vehicleFingerprint = MutableStateFlow(fingerprint)
        var lastResolvedVehicleId: String? = null
        val resolvedVehicleIds = mutableListOf<String?>()

        override fun start() = Unit
        override fun stop() = Unit
        override fun setVehicleState(state: TelemetryVehicleState) = Unit
        override fun setResolvedVehicleId(vehicleId: String?) {
            lastResolvedVehicleId = vehicleId
            resolvedVehicleIds += vehicleId
        }
        override fun refreshVehicleId() = Unit
    }

    private companion object {
        val UNPAIRED_VEHICLE = Vehicle(
            id = "vehicle-1",
            fingerprint = null,
            brand = "Volkswagen",
            model = "Transporter",
            year = 2024,
            licensePlate = "ATLAS-1",
            odometer = 12_500.0,
            fuelLevel = 75.0
        )
        val PAIRED_VEHICLE = UNPAIRED_VEHICLE.copy(
            fingerprint = "fingerprint-1"
        )
    }
}
