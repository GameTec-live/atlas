package org.gtlv.atlas.assign

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.geoservice.ResolveAddressResult
import org.gtlv.core.geoservice.Route
import org.gtlv.core.geoservice.RouteManeuver
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.geoservice.RouteResult
import org.gtlv.core.geoservice.RouteSummary
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobCandidatesResult
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobsResult
import org.gtlv.core.job.UnassignedJobsResult
import org.gtlv.core.role.AssignedRole
import org.gtlv.core.role.RoleAvailability
import org.gtlv.core.role.RoleAvailabilityResult
import org.gtlv.core.role.RoleRepository
import org.gtlv.core.role.SelectRoleResult
import org.gtlv.core.shift.ShiftRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssignJobViewModelTest {

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
    fun openingDestinationEditor_prefillsAndSearchesAfterTyping() =
        runTest(dispatcher) {
            val viewModel = AssignJobViewModel(
                jobRepository = FakeJobRepository(),
                geoServiceRepository =
                    FakeGeoServiceRepository(testRoute()),
                roleRepository = FakeRoleRepository()
            )

            viewModel.load(
                testJob().copy(
                    to = JobCoordinates(48.3, 14.4),
                    toAddress = "Old destination"
                )
            )
            advanceUntilIdle()

            viewModel.openAddressEditor(
                JobLocationField.TO
            )

            assertEquals(
                "Old destination",
                viewModel.uiState.value.addressSearch.query
            )
            assertFalse(
                viewModel.uiState.value
                    .addressSearch.hasSearched
            )

            viewModel.onAddressQueryChanged("New origin")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value
                    .addressSearch.hasSearched
            )
        }

    @Test
    fun selectingSecondAddress_loadsRouteAndFitsRoutePoints() =
        runTest(dispatcher) {
            val repository = FakeJobRepository()
            val route = testRoute()
            val viewModel = AssignJobViewModel(
                jobRepository = repository,
                geoServiceRepository =
                    FakeGeoServiceRepository(route),
                roleRepository = FakeRoleRepository()
            )

            viewModel.load(testJob())
            advanceUntilIdle()

            assertEquals(
                1,
                viewModel.uiState.value
                    .cameraFocusPoints.size
            )

            viewModel.openAddressEditor(
                JobLocationField.TO
            )
            viewModel.selectAddressSuggestion(
                AddressSuggestion(
                    id = "destination",
                    displayName = "Destination",
                    latitude = 48.3,
                    longitude = 14.4
                )
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                "Destination",
                state.job?.toAddress
            )
            assertTrue(state.hasUnsavedChanges)
            assertNotNull(state.route)
            assertEquals(
                route.points,
                state.cameraFocusPoints
            )
            assertTrue(state.cameraFocusRequestId >= 2)
        }

    @Test
    fun closingDestinationEditor_discardsUnselectedInput() =
        runTest(dispatcher) {
            val originalJob = testJob().copy(
                to = JobCoordinates(48.3, 14.4),
                toAddress = "Saved destination"
            )
            val viewModel = AssignJobViewModel(
                jobRepository = FakeJobRepository(),
                geoServiceRepository =
                    FakeGeoServiceRepository(testRoute()),
                roleRepository = FakeRoleRepository()
            )

            viewModel.load(originalJob)
            advanceUntilIdle()
            viewModel.openAddressEditor(JobLocationField.TO)
            viewModel.onAddressQueryChanged("Unsaved text")

            viewModel.closeAddressEditor()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isAddressEditorOpen)
            assertEquals("", state.addressSearch.query)
            assertEquals(
                "Saved destination",
                state.job?.toAddress
            )
        }

    @Test
    fun savingChanges_persistsDestinationAndDueDate() =
        runTest(dispatcher) {
            val repository = FakeJobRepository()
            val viewModel = AssignJobViewModel(
                jobRepository = repository,
                geoServiceRepository =
                    FakeGeoServiceRepository(testRoute()),
                roleRepository = FakeRoleRepository()
            )

            viewModel.load(testJob())
            advanceUntilIdle()
            viewModel.openAddressEditor(JobLocationField.TO)
            viewModel.selectAddressSuggestion(
                AddressSuggestion(
                    id = "destination",
                    displayName = "Destination",
                    latitude = 48.3,
                    longitude = 14.4
                )
            )
            viewModel.updateDueDate(
                "2026-08-26T17:11:00Z"
            )

            viewModel.saveChanges()
            advanceUntilIdle()

            assertEquals(
                JobCoordinates(48.3, 14.4),
                repository.updatedDestination
            )
            assertEquals(
                "2026-08-26T17:11:00Z",
                repository.updatedDueDate
            )
            assertFalse(
                viewModel.uiState.value
                    .hasUnsavedChanges
            )
        }

    @Test
    fun confirmingCandidate_assignsJob() =
        runTest(dispatcher) {
            val repository = FakeJobRepository()
            val viewModel = AssignJobViewModel(
                jobRepository = repository,
                geoServiceRepository =
                    FakeGeoServiceRepository(testRoute()),
                roleRepository = FakeRoleRepository()
            )

            viewModel.load(testJob())
            advanceUntilIdle()

            val candidate = repository.candidate
            viewModel.requestAssignment(candidate)
            viewModel.confirmAssignment()
            advanceUntilIdle()

            assertEquals(
                "driver-1",
                repository.assignedDriverId
            )
            assertTrue(
                viewModel.uiState.value
                    .assignmentCompleted
            )
        }

    @Test
    fun confirmingCandidate_savesDraftBeforeAssignment() =
        runTest(dispatcher) {
            val repository = FakeJobRepository()
            val viewModel = AssignJobViewModel(
                jobRepository = repository,
                geoServiceRepository =
                    FakeGeoServiceRepository(testRoute()),
                roleRepository = FakeRoleRepository()
            )

            viewModel.load(testJob())
            advanceUntilIdle()
            viewModel.openAddressEditor(JobLocationField.TO)
            viewModel.selectAddressSuggestion(
                AddressSuggestion(
                    id = "destination",
                    displayName = "Destination",
                    latitude = 48.3,
                    longitude = 14.4
                )
            )
            viewModel.requestAssignment(repository.candidate)

            viewModel.confirmAssignment()
            advanceUntilIdle()

            assertEquals(
                JobCoordinates(48.3, 14.4),
                repository.updatedDestination
            )
            assertEquals(
                "driver-1",
                repository.assignedDriverId
            )
            assertTrue(
                viewModel.uiState.value.assignmentCompleted
            )
        }

    @Test
    fun loadingDrivers_excludesRecommendedFromOtherDrivers() =
        runTest(dispatcher) {
            val viewModel = AssignJobViewModel(
                jobRepository = FakeJobRepository(),
                geoServiceRepository =
                    FakeGeoServiceRepository(testRoute()),
                roleRepository = FakeRoleRepository()
            )

            viewModel.load(testJob())
            advanceUntilIdle()

            assertEquals(
                listOf("driver-2"),
                viewModel.uiState.value.otherDrivers
                    .map(JobCandidate::driverId)
            )
        }

    @Test
    fun candidatesAreAvailableWhileAllDriversAreStillLoading() =
        runTest(dispatcher) {
            val delayedRoles = DelayedRoleRepository()
            val viewModel = AssignJobViewModel(
                jobRepository = FakeJobRepository(),
                geoServiceRepository =
                    FakeGeoServiceRepository(testRoute()),
                roleRepository = delayedRoles
            )

            viewModel.load(testJob())
            runCurrent()

            val candidatesReady = viewModel.uiState.value
            assertEquals(
                listOf("driver-1"),
                candidatesReady.candidates.map(
                    JobCandidate::driverId
                )
            )
            assertFalse(candidatesReady.isLoadingCandidates)
            assertTrue(candidatesReady.isLoadingDrivers)

            delayedRoles.complete()
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.isLoadingDrivers
            )
        }

    private class FakeJobRepository : JobRepository {
        override val jobChanges: Flow<Unit> = emptyFlow()

        val candidate = JobCandidate(
            driverId = "driver-1",
            driverName = "Hermann",
            rank = 1,
            summary = null
        )

        var assignedDriverId: String? = null
        var updatedDestination: JobCoordinates? = null
        var updatedDueDate: String? = null

        override suspend fun getJobs(): JobsResult =
            JobsResult.InvalidResponse

        override suspend fun getUnassignedJobs():
                UnassignedJobsResult =
            UnassignedJobsResult.InvalidResponse

        override suspend fun deleteUnassignedJob(
            jobId: String
        ): JobActionResult =
            JobActionResult.InvalidResponse

        override suspend fun getJobCandidates(
            jobId: String
        ): JobCandidatesResult =
            JobCandidatesResult.Success(
                listOf(candidate)
            )

        override suspend fun assignJob(
            jobId: String,
            driverId: String
        ): JobActionResult {
            assignedDriverId = driverId
            return JobActionResult.Success
        }

        override suspend fun startJob(
            jobId: String
        ): JobActionResult =
            JobActionResult.InvalidResponse

        override suspend fun cancelJob(
            jobId: String
        ): JobActionResult =
            JobActionResult.InvalidResponse

        override suspend fun completeJob(
            jobId: String
        ): JobActionResult =
            JobActionResult.InvalidResponse

        override suspend fun updateJobLocation(
            jobId: String,
            field: JobLocationField,
            latitude: Double,
            longitude: Double
        ): JobActionResult = JobActionResult.Success

        override suspend fun updateJobDetails(
            jobId: String,
            destination: JobCoordinates?,
            dueDate: String
        ): JobActionResult {
            updatedDestination = destination
            updatedDueDate = dueDate
            return JobActionResult.Success
        }
    }

    private class FakeGeoServiceRepository(
        private val route: Route
    ) : GeoServiceRepository {
        override suspend fun resolveAddress(
            address: String
        ): ResolveAddressResult =
            ResolveAddressResult.Success(emptyList())

        override suspend fun requestRoute(
            origin: RoutePoint,
            destination: RoutePoint,
            language: String,
            headingDegrees: Int?
        ): RouteResult = RouteResult.Success(route)
    }

    private class FakeRoleRepository : RoleRepository {
        override suspend fun getAvailability():
                RoleAvailabilityResult =
            RoleAvailabilityResult.Success(
                RoleAvailability(
                    dispatcherSpotsFree = 1,
                    dispatcherAvailable = true,
                    assignedRoles = listOf(
                        AssignedRole(
                            driverId = "driver-1",
                            role = ShiftRole.DRIVER,
                            name = "Hermann"
                        ),
                        AssignedRole(
                            driverId = "driver-2",
                            role = ShiftRole.DRIVER,
                            name = "Birgit"
                        ),
                        AssignedRole(
                            driverId = "dispatcher-1",
                            role = ShiftRole.DISPATCHER,
                            name = "Dispatcher"
                        )
                    )
                )
            )

        override suspend fun selectRole(
            role: ShiftRole
        ): SelectRoleResult = SelectRoleResult.NetworkError
    }

    private class DelayedRoleRepository : RoleRepository {
        private val availability =
            CompletableDeferred<RoleAvailabilityResult>()

        fun complete() {
            availability.complete(
                RoleAvailabilityResult.Success(
                    RoleAvailability(
                        dispatcherSpotsFree = 1,
                        dispatcherAvailable = true
                    )
                )
            )
        }

        override suspend fun getAvailability():
                RoleAvailabilityResult = availability.await()

        override suspend fun selectRole(
            role: ShiftRole
        ): SelectRoleResult = SelectRoleResult.NetworkError
    }

    private fun testJob(): Job {
        return Job(
            id = "job-1",
            assignedDriverId = null,
            vehicleId = null,
            from = org.gtlv.core.job.JobCoordinates(
                latitude = 48.2,
                longitude = 14.3
            ),
            to = null,
            fromAddress = "Origin",
            toAddress = null,
            dueDate = null,
            note = null,
            startedAt = null,
            completedAt = null,
            createdAt = null,
            updatedAt = null
        )
    }

    private fun testRoute(): Route {
        return Route(
            points = listOf(
                RoutePoint(48.2, 14.3),
                RoutePoint(48.3, 14.4)
            ),
            maneuvers = emptyList<RouteManeuver>(),
            summary = RouteSummary(
                timeSeconds = 600.0,
                lengthKilometers = 12.0
            ),
            units = "kilometers",
            language = "en"
        )
    }
}
