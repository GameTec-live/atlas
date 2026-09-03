package org.gtlv.core.fleet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FleetRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: FleetRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = FleetRepositoryImpl(
            networkClient = NetworkClient(),
            serverSettingsRepository = FakeServerSettingsRepository(
                server.url("/").toString()
            )
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `looks up and parses a vehicle by fingerprint`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(VEHICLE_JSON)
        )

        val result = repository.getVehicleByFingerprint("car-fingerprint")

        val vehicle = (result as VehicleLookupResult.Success).vehicle
        assertEquals("vehicle-1", vehicle.id)
        assertEquals("Volkswagen Transporter", vehicle.displayName)
        assertEquals("ATLAS-1", vehicle.licensePlate)
        assertEquals(
            "/api/fleet/fingerprint/car-fingerprint",
            server.takeRequest().path
        )
    }

    @Test
    fun `parses nested fleet rows including an unpaired vehicle`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"vehicle":$VEHICLE_JSON,"maintenance":null}]"""
            )
        )

        val vehicles = (repository.getVehicles() as VehiclesResult.Success)
            .vehicles

        assertEquals(1, vehicles.size)
        assertNull(vehicles.single().fingerprint)
        assertEquals("/api/fleet/vehicles", server.takeRequest().path)
    }

    @Test
    fun `assigns only the fingerprint with a partial vehicle update`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.assignFingerprint(
            vehicleId = "vehicle-1",
            fingerprint = "car-fingerprint"
        )

        assertEquals(AssignFingerprintResult.Success, result)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/fleet/vehicles/vehicle-1", request.path)
        assertEquals(
            "car-fingerprint",
            JSONObject(request.body.readUtf8()).getString("fingerprint")
        )
    }

    private class FakeServerSettingsRepository(
        initialAddress: String
    ) : ServerSettingsRepository {
        private val address = MutableStateFlow(initialAddress)
        override val serverAddress: StateFlow<String> = address
        override suspend fun setServerAddress(address: String) {
            this.address.value = address
        }
    }

    private companion object {
        const val VEHICLE_JSON = """
            {
              "id":"vehicle-1",
              "fingerprint":null,
              "brand":"Volkswagen",
              "model":"Transporter",
              "year":2024,
              "licensePlate":"ATLAS-1",
              "odometer":12500,
              "fuelLevel":75
            }
        """
    }
}
