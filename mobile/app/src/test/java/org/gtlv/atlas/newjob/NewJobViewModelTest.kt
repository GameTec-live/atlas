package org.gtlv.atlas.newjob

import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.geoservice.ResolveAddressResult
import org.gtlv.core.geoservice.Route
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.geoservice.RouteResult
import org.gtlv.core.geoservice.RouteSummary
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobCandidatesResult
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.job.JobCreationResult
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobsResult
import org.gtlv.core.job.NewJobRequest
import org.gtlv.core.job.UnassignedJobsResult
import org.gtlv.core.role.RoleAvailability
import org.gtlv.core.role.RoleAvailabilityResult
import org.gtlv.core.role.RoleRepository
import org.gtlv.core.role.SelectRoleResult
import org.gtlv.core.shift.ShiftRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewJobViewModelTest {

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
    fun selectingAddresses_loadsCandidatesAndRoute() = runTest(dispatcher) {
        val repository = FakeJobRepository()
        val viewModel = createViewModel(repository)
        viewModel.load()

        viewModel.openAddressEditor(JobLocationField.FROM)
        viewModel.selectAddressSuggestion(
            suggestion("from", 48.2, 14.3)
        )
        advanceUntilIdle()

        assertEquals(
            JobCoordinates(48.2, 14.3),
            repository.candidateRequest?.from
        )
        assertEquals(1, viewModel.uiState.value.cameraFocusPoints.size)

        viewModel.openAddressEditor(JobLocationField.TO)
        viewModel.selectAddressSuggestion(
            suggestion("to", 48.4, 14.5)
        )
        advanceUntilIdle()

        assertEquals(
            JobCoordinates(48.4, 14.5),
            repository.candidateRequest?.to
        )
        assertNotNull(viewModel.uiState.value.route)
        assertEquals(2, viewModel.uiState.value.cameraFocusPoints.size)
    }

    @Test
    fun createUnassigned_sendsTrimmedDraftAndCompletes() =
        runTest(dispatcher) {
            val repository = FakeJobRepository()
            val viewModel = createViewModel(repository)
            viewModel.load()
            viewModel.openAddressEditor(JobLocationField.FROM)
            viewModel.selectAddressSuggestion(
                suggestion("from", 48.2, 14.3)
            )
            viewModel.updateNote(" x ".repeat(60))
            advanceUntilIdle()

            viewModel.requestUnassignedCreation()
            assertTrue(
                viewModel.uiState.value.isSelectingUnassignedDueDate
            )
            viewModel.confirmUnassignedDueDate(
                "2026-08-29T10:30:00Z"
            )
            viewModel.confirmCreation()
            advanceUntilIdle()

            val request = repository.creationRequest
            assertNotNull(request)
            assertEquals(null, request?.assignedDriverId)
            assertEquals(
                "2026-08-29T10:30:00Z",
                request?.dueDate
            )
            assertTrue(request?.note?.length ?: 0 <= NEW_JOB_NOTE_MAX_LENGTH)
            assertTrue(viewModel.uiState.value.creationCompleted)
        }

    @Test
    fun loadingAgainAfterConfigurationChange_keepsDraft() =
        runTest(dispatcher) {
            val viewModel = createViewModel(FakeJobRepository())
            viewModel.load()
            viewModel.updateNote("Keep this note")
            viewModel.openAddressEditor(JobLocationField.FROM)
            viewModel.onAddressQueryChanged("Lest 110")

            viewModel.load()
            advanceUntilIdle()

            assertEquals("Keep this note", viewModel.uiState.value.note)
            assertEquals(
                "Lest 110",
                viewModel.uiState.value.addressSearch.query
            )
        }

    @Test
    fun changingCandidateInputs_invalidatesPreviousRecommendation() =
        runTest(dispatcher) {
            val viewModel = createViewModel(FakeJobRepository())
            viewModel.load()
            viewModel.openAddressEditor(JobLocationField.FROM)
            viewModel.selectAddressSuggestion(
                suggestion("from", 48.2, 14.3)
            )
            advanceUntilIdle()

            val staleCandidate = viewModel.uiState.value
                .candidates
                .single()
            viewModel.requestDriverCreation(staleCandidate)
            assertEquals(
                staleCandidate,
                viewModel.uiState.value.pendingCandidate
            )

            viewModel.updateDueDate("2026-08-30T12:00:00Z")

            assertTrue(viewModel.uiState.value.candidates.isEmpty())
            assertTrue(viewModel.uiState.value.isLoadingCandidates)
            assertEquals(null, viewModel.uiState.value.pendingCandidate)

            viewModel.requestDriverCreation(staleCandidate)
            assertEquals(null, viewModel.uiState.value.pendingCandidate)
        }

    private fun createViewModel(
        repository: FakeJobRepository
    ) = NewJobViewModel(
        jobRepository = repository,
        geoServiceRepository = FakeGeoServiceRepository(),
        roleRepository = FakeRoleRepository(),
        now = { Instant.parse("2026-08-28T14:00:00Z") }
    )

    private fun suggestion(
        name: String,
        latitude: Double,
        longitude: Double
    ) = AddressSuggestion(
        id = name,
        displayName = name,
        latitude = latitude,
        longitude = longitude
    )

    private class FakeJobRepository : JobRepository {
        override val jobChanges: Flow<Unit> = emptyFlow()
        var candidateRequest: NewJobRequest? = null
        var creationRequest: NewJobRequest? = null

        override suspend fun getJobs(): JobsResult = JobsResult.InvalidResponse
        override suspend fun getUnassignedJobs(): UnassignedJobsResult =
            UnassignedJobsResult.InvalidResponse
        override suspend fun deleteUnassignedJob(jobId: String) =
            JobActionResult.InvalidResponse
        override suspend fun getJobCandidates(jobId: String) =
            JobCandidatesResult.InvalidResponse

        override suspend fun getJobCandidates(
            from: JobCoordinates,
            to: JobCoordinates?,
            dueDate: String
        ): JobCandidatesResult {
            candidateRequest = NewJobRequest(
                from = from,
                to = to,
                dueDate = dueDate,
                note = null,
                assignedDriverId = null
            )
            return JobCandidatesResult.Success(
                listOf(JobCandidate("driver-1", "Hermann", 1, null))
            )
        }

        override suspend fun createJob(
            request: NewJobRequest
        ): JobCreationResult {
            creationRequest = request
            return JobCreationResult.Success(
                Job(
                    id = "new-job",
                    assignedDriverId = request.assignedDriverId,
                    vehicleId = null,
                    from = request.from,
                    to = request.to,
                    fromAddress = null,
                    toAddress = null,
                    dueDate = request.dueDate,
                    note = request.note,
                    startedAt = null,
                    completedAt = null,
                    createdAt = null,
                    updatedAt = null
                )
            )
        }

        override suspend fun assignJob(jobId: String, driverId: String) =
            JobActionResult.InvalidResponse
        override suspend fun startJob(jobId: String) =
            JobActionResult.InvalidResponse
        override suspend fun cancelJob(jobId: String) =
            JobActionResult.InvalidResponse
        override suspend fun completeJob(jobId: String) =
            JobActionResult.InvalidResponse
        override suspend fun updateJobLocation(
            jobId: String,
            field: JobLocationField,
            latitude: Double,
            longitude: Double
        ) = JobActionResult.InvalidResponse
        override suspend fun updateJobDetails(
            jobId: String,
            destination: JobCoordinates?,
            dueDate: String
        ) = JobActionResult.InvalidResponse
    }

    private class FakeGeoServiceRepository : GeoServiceRepository {
        override suspend fun resolveAddress(address: String) =
            ResolveAddressResult.Success(emptyList())

        override suspend fun requestRoute(
            origin: RoutePoint,
            destination: RoutePoint,
            language: String,
            headingDegrees: Int?
        ): RouteResult = RouteResult.Success(
            Route(
                points = listOf(origin, destination),
                maneuvers = emptyList(),
                summary = RouteSummary(
                    timeSeconds = 60.0,
                    lengthKilometers = 1.0
                ),
                units = "kilometers",
                language = "en"
            )
        )
    }

    private class FakeRoleRepository : RoleRepository {
        override suspend fun getAvailability() =
            RoleAvailabilityResult.Success(
                RoleAvailability(
                    dispatcherSpotsFree = 1,
                    dispatcherAvailable = true
                )
            )

        override suspend fun selectRole(role: ShiftRole) =
            SelectRoleResult.NetworkError
    }
}
