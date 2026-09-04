package org.gtlv.atlas.offboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.gtlv.core.fleet.ConnectedVehicleState
import org.gtlv.core.fleet.AssignFingerprintResult
import org.gtlv.core.fleet.FleetRepository
import org.gtlv.core.fleet.Vehicle
import org.gtlv.core.fleet.VehicleLookupResult
import org.gtlv.core.fleet.VehiclesResult
import org.gtlv.core.logbook.LogbookRepository
import org.gtlv.core.logbook.LogbookSubmission
import org.gtlv.core.logbook.SubmitLogbookResult
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.shift.ShiftSession
import org.gtlv.core.shift.ShiftSessionManager
import org.gtlv.core.shift.ShiftSessionStore
import org.gtlv.core.telemetry.TelemetryData
import org.gtlv.core.telemetry.TelemetryProvider
import org.gtlv.core.telemetry.TelemetryVehicleState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OffboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `logout uses telemetry odometer and submits before logout`() =
        runTest(dispatcher) {
            val shiftManager = createShiftManager()
            val telemetry = FakeTelemetryProvider(12_695.0)
            val repository = FakeLogbookRepository()
            var loggedOut = false
            val viewModel = createViewModel(
                shiftManager = shiftManager,
                telemetry = telemetry,
                repository = repository,
                logout = { loggedOut = true }
            )
            advanceUntilIdle()

            viewModel.requestLogout()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isVisible)
            assertFalse(
                viewModel.uiState.value.isEndKilometerDialogVisible
            )
            assertEquals(
                12_695.0,
                viewModel.uiState.value.session?.endKilometer
            )

            viewModel.updateRevenue("325,50")
            viewModel.setConfirmed(true)
            viewModel.submitAndLogout()
            advanceUntilIdle()

            assertNotNull(repository.submission)
            assertEquals(12_345L, repository.submission?.startOdometer)
            assertEquals(12_695L, repository.submission?.endOdometer)
            assertEquals(325.5, repository.submission?.revenue)
            assertTrue(loggedOut)
        }

    @Test
    fun `missing telemetry asks for a valid ending odometer`() =
        runTest(dispatcher) {
            val viewModel = createViewModel(
                shiftManager = createShiftManager(),
                telemetry = FakeTelemetryProvider(null),
                repository = FakeLogbookRepository(),
                logout = {}
            )
            advanceUntilIdle()

            viewModel.requestLogout()
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.isEndKilometerDialogVisible
            )

            viewModel.updateEndKilometerInput("12000")
            viewModel.confirmEndKilometer()
            assertTrue(viewModel.uiState.value.isEndKilometerInvalid)

            viewModel.updateEndKilometerInput("12695")
            viewModel.confirmEndKilometer()
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.isEndKilometerDialogVisible
            )
            assertTrue(viewModel.uiState.value.isVisible)
        }

    @Test
    fun `dispatcher profile logout also opens offboarding`() =
        runTest(dispatcher) {
            var loggedOut = false
            val viewModel = createViewModel(
                shiftManager = createShiftManager(ShiftRole.DISPATCHER),
                telemetry = FakeTelemetryProvider(12_695.0),
                repository = FakeLogbookRepository(),
                logout = { loggedOut = true }
            )
            advanceUntilIdle()

            viewModel.requestLogout()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isVisible)
            assertFalse(loggedOut)
        }

    @Test
    fun `missing vehicle loads fleet and requires a selection`() =
        runTest(dispatcher) {
            val repository = FakeLogbookRepository()
            val fleetRepository = FakeFleetRepository(
                VehiclesResult.Success(listOf(VEHICLE))
            )
            var loggedOut = false
            val viewModel = createViewModel(
                shiftManager = createShiftManager(),
                telemetry = FakeTelemetryProvider(12_695.0),
                repository = repository,
                logout = { loggedOut = true },
                connectedVehicleState = ConnectedVehicleState.Disconnected,
                fleetRepository = fleetRepository
            )
            advanceUntilIdle()

            viewModel.requestLogout()
            advanceUntilIdle()
            assertEquals(listOf(VEHICLE), viewModel.uiState.value.availableVehicles)

            viewModel.updateRevenue("0")
            viewModel.setConfirmed(true)
            viewModel.submitAndLogout()
            advanceUntilIdle()

            assertEquals(null, repository.submission)
            assertFalse(loggedOut)
            assertEquals(
                OffboardingError.VEHICLE_REQUIRED,
                viewModel.uiState.value.error
            )

            viewModel.selectVehicle(VEHICLE)
            viewModel.submitAndLogout()
            advanceUntilIdle()

            assertEquals(VEHICLE.id, repository.submission?.vehicleId)
            assertTrue(loggedOut)
        }

    @Test
    fun `back returns to the shift and clears pending completion`() =
        runTest(dispatcher) {
            val viewModel = createViewModel(
                shiftManager = createShiftManager(),
                telemetry = FakeTelemetryProvider(12_695.0),
                repository = FakeLogbookRepository(),
                logout = {}
            )
            advanceUntilIdle()
            viewModel.requestLogout()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isVisible)

            viewModel.cancelOffboarding()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isVisible)
            assertEquals(null, viewModel.uiState.value.session?.endTimeUtc)
            assertEquals(null, viewModel.uiState.value.session?.endKilometer)
        }

    private suspend fun createShiftManager(
        role: ShiftRole = ShiftRole.DRIVER
    ): ShiftSessionManager {
        val manager = ShiftSessionManager(FakeShiftSessionStore())
        manager.startShift(role)
        manager.setStartKilometerIfAbsent(12_345.0)
        return manager
    }

    private fun createViewModel(
        shiftManager: ShiftSessionManager,
        telemetry: TelemetryProvider,
        repository: LogbookRepository,
        logout: suspend () -> Unit,
        connectedVehicleState: ConnectedVehicleState =
            ConnectedVehicleState.Connected("fingerprint", VEHICLE),
        fleetRepository: FleetRepository = FakeFleetRepository(
            VehiclesResult.Success(listOf(VEHICLE))
        )
    ) = OffboardingViewModel(
        shiftSessionManager = shiftManager,
        telemetryProvider = telemetry,
        connectedVehicleState = MutableStateFlow(connectedVehicleState),
        fleetRepository = fleetRepository,
        logbookRepository = repository,
        logout = logout
    )

    private class FakeShiftSessionStore : ShiftSessionStore {
        private var session: ShiftSession? = null
        override suspend fun restore(): ShiftSession? = session
        override suspend fun save(session: ShiftSession) {
            this.session = session
        }
        override suspend fun clear() {
            session = null
        }
    }

    private class FakeLogbookRepository(
        private val result: SubmitLogbookResult = SubmitLogbookResult.Success
    ) : LogbookRepository {
        var submission: LogbookSubmission? = null
        override suspend fun submit(
            submission: LogbookSubmission
        ): SubmitLogbookResult {
            this.submission = submission
            return result
        }
    }

    private class FakeFleetRepository(
        private val vehiclesResult: VehiclesResult
    ) : FleetRepository {
        override suspend fun getVehicleByFingerprint(
            fingerprint: String
        ): VehicleLookupResult = VehicleLookupResult.NotFound

        override suspend fun getVehicles(): VehiclesResult = vehiclesResult

        override suspend fun assignFingerprint(
            vehicleId: String,
            fingerprint: String
        ): AssignFingerprintResult = AssignFingerprintResult.NotFound
    }

    private class FakeTelemetryProvider(
        odometer: Double?
    ) : TelemetryProvider {
        override val telemetry = MutableStateFlow<TelemetryData?>(null)
        override val odometerKilometers = MutableStateFlow(odometer)
        override val vehicleFingerprint = MutableStateFlow<String?>(null)
        override fun start() = Unit
        override fun stop() = Unit
        override fun setVehicleState(state: TelemetryVehicleState) = Unit
        override fun setResolvedVehicleId(vehicleId: String?) = Unit
        override fun refreshVehicleId() = Unit
    }

    private companion object {
        val VEHICLE = Vehicle(
            id = "7bb0de4d-bcdd-4c99-a852-a17a4bbdb3de",
            fingerprint = "fingerprint",
            brand = "BYD",
            model = "Sealion 7",
            year = 2026,
            licensePlate = "FRBYD2",
            odometer = 20_000.0,
            fuelLevel = null
        )
    }
}
