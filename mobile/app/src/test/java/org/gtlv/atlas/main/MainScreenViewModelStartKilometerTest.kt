package org.gtlv.atlas.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.geoservice.ResolveAddressResult
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.geoservice.RouteResult
import org.gtlv.core.job.CollectedJobStateStore
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobCandidatesResult
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.job.JobCreationResult
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.job.JobMileageSnapshots
import org.gtlv.core.job.JobMileageStateStore
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobsResult
import org.gtlv.core.job.NewJobRequest
import org.gtlv.core.job.UnassignedJobsResult
import org.gtlv.core.location.AtlasLocation
import org.gtlv.core.location.LocationSource
import org.gtlv.core.location.LocationState
import org.gtlv.core.pricing.PriceResult
import org.gtlv.core.pricing.PricingRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelStartKilometerTest {
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
    fun `confirmation starts original job after queue reorder`() =
        runTest(dispatcher) {
            val firstJob = testJob("first")
            val secondJob = testJob("second")
            val repository = FakeJobRepository(
                JobsResult.Success(
                    queuedJobs = listOf(firstJob, secondJob),
                    currentJob = null
                )
            )
            val mileageStore = FakeJobMileageStore()
            val viewModel = createViewModel(repository, mileageStore)

            viewModel.loadJobsForUser(USER_ID)
            advanceUntilIdle()
            viewModel.startNextJob()

            repository.publishJobs(
                JobsResult.Success(
                    queuedJobs = listOf(secondJob, firstJob),
                    currentJob = null
                )
            )
            advanceUntilIdle()

            viewModel.updateStartKilometerInput("12345.6")
            viewModel.confirmStartKilometer()
            advanceUntilIdle()

            assertEquals(listOf(firstJob.id), repository.startedJobIds)
            assertEquals(firstJob.id, mileageStore.startedJobId)
        }

    @Test
    fun `refresh cancels confirmation when original job disappears`() =
        runTest(dispatcher) {
            val firstJob = testJob("first")
            val secondJob = testJob("second")
            val repository = FakeJobRepository(
                JobsResult.Success(
                    queuedJobs = listOf(firstJob, secondJob),
                    currentJob = null
                )
            )
            val viewModel = createViewModel(
                repository,
                FakeJobMileageStore()
            )

            viewModel.loadJobsForUser(USER_ID)
            advanceUntilIdle()
            viewModel.startNextJob()
            assertTrue(
                viewModel.uiState.value
                    .isStartKilometerDialogVisible
            )

            repository.publishJobs(
                JobsResult.Success(
                    queuedJobs = listOf(secondJob),
                    currentJob = null
                )
            )
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value
                    .isStartKilometerDialogVisible
            )
            assertTrue(viewModel.uiState.value.startNextJobFailed)
            assertTrue(repository.startedJobIds.isEmpty())
        }

    @Test
    fun `telemetry continuation starts original job after queue reorder`() =
        runTest(dispatcher) {
            val firstJob = testJob("first")
            val secondJob = testJob("second")
            val repository = FakeJobRepository(
                JobsResult.Success(
                    queuedJobs = listOf(firstJob, secondJob),
                    currentJob = null
                )
            )
            val shiftSessionManager = ShiftSessionManager(
                FakeShiftSessionStore()
            )
            shiftSessionManager.startShift(ShiftRole.DRIVER)
            val viewModel = createViewModel(
                repository = repository,
                mileageStore = FakeJobMileageStore(),
                shiftSessionManager = shiftSessionManager
            )

            viewModel.loadJobsForUser(USER_ID)
            advanceUntilIdle()
            viewModel.startNextJob()

            repository.publishJobs(
                JobsResult.Success(
                    queuedJobs = listOf(secondJob, firstJob),
                    currentJob = null
                )
            )
            advanceUntilIdle()

            shiftSessionManager.setStartKilometerIfAbsent(12_345.6)
            advanceUntilIdle()

            assertEquals(listOf(firstJob.id), repository.startedJobIds)
        }

    @Test
    fun `finishing job without destination saves current location first`() =
        runTest(dispatcher) {
            val currentJob = testJob("current")
            val repository = FakeJobRepository(
                JobsResult.Success(
                    queuedJobs = emptyList(),
                    currentJob = currentJob
                )
            )
            val viewModel = createViewModel(
                repository,
                FakeJobMileageStore()
            )

            viewModel.loadJobsForUser(USER_ID)
            advanceUntilIdle()
            viewModel.updateLocationState(
                availableLocation(
                    latitude = 48.2082,
                    longitude = 16.3738
                )
            )
            viewModel.personCollected()
            viewModel.closeAddressEditor()
            viewModel.requestFinishCurrentJob()
            viewModel.confirmFinishCurrentJob()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    LocationUpdate(
                        jobId = currentJob.id,
                        field = JobLocationField.TO,
                        latitude = 48.2082,
                        longitude = 16.3738
                    )
                ),
                repository.locationUpdates
            )
            assertEquals(
                listOf(currentJob.id),
                repository.completedJobIds
            )
        }

    @Test
    fun `finishing job with destination does not replace it`() =
        runTest(dispatcher) {
            val currentJob = testJob(
                id = "current",
                destination = JobCoordinates(
                    latitude = 48.1,
                    longitude = 16.2
                )
            )
            val repository = FakeJobRepository(
                JobsResult.Success(
                    queuedJobs = emptyList(),
                    currentJob = currentJob
                )
            )
            val viewModel = createViewModel(
                repository,
                FakeJobMileageStore()
            )

            viewModel.loadJobsForUser(USER_ID)
            advanceUntilIdle()
            viewModel.updateLocationState(
                availableLocation(
                    latitude = 48.2082,
                    longitude = 16.3738
                )
            )
            viewModel.personCollected()
            viewModel.requestFinishCurrentJob()
            viewModel.confirmFinishCurrentJob()
            advanceUntilIdle()

            assertTrue(repository.locationUpdates.isEmpty())
            assertEquals(
                listOf(currentJob.id),
                repository.completedJobIds
            )
        }

    @Test
    fun `failed destination update prevents job completion`() =
        runTest(dispatcher) {
            val currentJob = testJob("current")
            val repository = FakeJobRepository(
                JobsResult.Success(
                    queuedJobs = emptyList(),
                    currentJob = currentJob
                )
            ).apply {
                locationUpdateResult =
                    JobActionResult.NetworkError
            }
            val viewModel = createViewModel(
                repository,
                FakeJobMileageStore()
            )

            viewModel.loadJobsForUser(USER_ID)
            advanceUntilIdle()
            viewModel.updateLocationState(
                availableLocation(
                    latitude = 48.2082,
                    longitude = 16.3738
                )
            )
            viewModel.personCollected()
            viewModel.closeAddressEditor()
            viewModel.requestFinishCurrentJob()
            viewModel.confirmFinishCurrentJob()
            advanceUntilIdle()

            assertEquals(1, repository.locationUpdates.size)
            assertTrue(repository.completedJobIds.isEmpty())
            assertTrue(
                viewModel.uiState.value
                    .finishCurrentJobFailed
            )
        }

    @Test
    fun `completion retry does not replace saved finish location`() =
        runTest(dispatcher) {
            val currentJob = testJob("current")
            val repository = FakeJobRepository(
                JobsResult.Success(
                    queuedJobs = emptyList(),
                    currentJob = currentJob
                )
            ).apply {
                completeJobResult =
                    JobActionResult.NetworkError
            }
            val viewModel = createViewModel(
                repository,
                FakeJobMileageStore()
            )

            viewModel.loadJobsForUser(USER_ID)
            advanceUntilIdle()
            viewModel.updateLocationState(
                availableLocation(
                    latitude = 48.2082,
                    longitude = 16.3738
                )
            )
            viewModel.personCollected()
            viewModel.closeAddressEditor()
            viewModel.requestFinishCurrentJob()
            viewModel.confirmFinishCurrentJob()
            advanceUntilIdle()

            assertEquals(
                JobCoordinates(
                    latitude = 48.2082,
                    longitude = 16.3738
                ),
                viewModel.uiState.value.currentJob?.to
            )

            viewModel.updateLocationState(
                availableLocation(
                    latitude = 48.3,
                    longitude = 16.4
                )
            )
            repository.completeJobResult =
                JobActionResult.Success
            viewModel.confirmFinishCurrentJob()
            advanceUntilIdle()

            assertEquals(1, repository.locationUpdates.size)
            assertEquals(
                LocationUpdate(
                    jobId = currentJob.id,
                    field = JobLocationField.TO,
                    latitude = 48.2082,
                    longitude = 16.3738
                ),
                repository.locationUpdates.single()
            )
            assertEquals(
                listOf(currentJob.id, currentJob.id),
                repository.completedJobIds
            )
        }

    private suspend fun createViewModel(
        repository: FakeJobRepository,
        mileageStore: FakeJobMileageStore,
        shiftSessionManager: ShiftSessionManager? = null
    ): MainScreenViewModel {
        val manager = shiftSessionManager ?: ShiftSessionManager(
            FakeShiftSessionStore()
        ).also {
            it.startShift(ShiftRole.DRIVER)
        }

        return MainScreenViewModel(
            jobRepository = repository,
            geoServiceRepository = FakeGeoServiceRepository(),
            telemetryProvider = FakeTelemetryProvider(),
            collectedJobStore = FakeCollectedJobStore(),
            jobMileageStore = mileageStore,
            pricingRepository = FakePricingRepository(),
            shiftSessionManager = manager
        )
    }

    private fun testJob(
        id: String,
        destination: JobCoordinates? = null
    ) = Job(
        id = id,
        assignedDriverId = USER_ID,
        vehicleId = null,
        from = null,
        to = destination,
        fromAddress = null,
        toAddress = null,
        dueDate = null,
        note = null,
        startedAt = null,
        completedAt = null,
        createdAt = null,
        updatedAt = null
    )

    private companion object {
        const val USER_ID = "driver"

        fun availableLocation(
            latitude: Double,
            longitude: Double
        ) = LocationState.Available(
            AtlasLocation(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = 2f,
                bearingDegrees = null,
                speedMetersPerSecond = null,
                timestampMillis = 1L,
                source = LocationSource.PHONE
            )
        )
    }
}

private data class LocationUpdate(
    val jobId: String,
    val field: JobLocationField,
    val latitude: Double,
    val longitude: Double
)

private class FakeJobRepository(
    private var jobs: JobsResult.Success
) : JobRepository {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val jobChanges: Flow<Unit> = changes
    val startedJobIds = mutableListOf<String>()
    val completedJobIds = mutableListOf<String>()
    val locationUpdates = mutableListOf<LocationUpdate>()
    var locationUpdateResult: JobActionResult =
        JobActionResult.Success
    var completeJobResult: JobActionResult =
        JobActionResult.Success

    fun publishJobs(result: JobsResult.Success) {
        jobs = result
        changes.tryEmit(Unit)
    }

    override suspend fun getJobs(): JobsResult = jobs

    override suspend fun startJob(jobId: String): JobActionResult {
        startedJobIds += jobId
        val startedJob = jobs.queuedJobs.first { it.id == jobId }
        jobs = JobsResult.Success(
            queuedJobs = jobs.queuedJobs.filterNot { it.id == jobId },
            currentJob = startedJob
        )
        return JobActionResult.Success
    }

    override suspend fun getUnassignedJobs() =
        UnassignedJobsResult.InvalidResponse

    override suspend fun deleteUnassignedJob(jobId: String) =
        JobActionResult.InvalidResponse

    override suspend fun getJobCandidates(jobId: String) =
        JobCandidatesResult.InvalidResponse

    override suspend fun getJobCandidates(
        from: JobCoordinates,
        to: JobCoordinates?,
        dueDate: String
    ) = JobCandidatesResult.InvalidResponse

    override suspend fun createJob(request: NewJobRequest) =
        JobCreationResult.InvalidResponse

    override suspend fun assignJob(jobId: String, driverId: String) =
        JobActionResult.InvalidResponse

    override suspend fun cancelJob(jobId: String) =
        JobActionResult.InvalidResponse

    override suspend fun completeJob(jobId: String): JobActionResult {
        completedJobIds += jobId
        if (completeJobResult == JobActionResult.Success) {
            jobs = jobs.copy(currentJob = null)
        }
        return completeJobResult
    }

    override suspend fun updateJobLocation(
        jobId: String,
        field: JobLocationField,
        latitude: Double,
        longitude: Double
    ): JobActionResult {
        locationUpdates += LocationUpdate(
            jobId = jobId,
            field = field,
            latitude = latitude,
            longitude = longitude
        )
        if (locationUpdateResult == JobActionResult.Success) {
            jobs = jobs.copy(
                currentJob = jobs.currentJob?.let { currentJob ->
                    if (currentJob.id == jobId) {
                        currentJob.copy(
                            to = JobCoordinates(
                                latitude = latitude,
                                longitude = longitude
                            )
                        )
                    } else {
                        currentJob
                    }
                }
            )
        }
        return locationUpdateResult
    }

    override suspend fun updateJobDetails(
        jobId: String,
        destination: JobCoordinates?,
        dueDate: String
    ) = JobActionResult.InvalidResponse
}

private class FakeCollectedJobStore : CollectedJobStateStore {
    private val collectedJobId = MutableStateFlow<String?>(null)

    override fun getCollectedJobId(userId: String): String? =
        collectedJobId.value

    override fun setCollectedJobId(userId: String, jobId: String) {
        collectedJobId.value = jobId
    }

    override fun clearCollectedJobId(userId: String) {
        collectedJobId.value = null
    }

    override fun observeCollectedJobId(userId: String): StateFlow<String?> =
        collectedJobId
}

private class FakeJobMileageStore : JobMileageStateStore {
    var startedJobId: String? = null

    override fun getSnapshots(userId: String): JobMileageSnapshots? = null

    override fun recordJobStarted(
        userId: String,
        jobId: String,
        odometerKilometers: Double?
    ) {
        startedJobId = jobId
    }

    override fun recordPersonCollected(
        userId: String,
        jobId: String,
        odometerKilometers: Double?
    ) = Unit

    override fun clear(userId: String) = Unit

    override fun clearIfJobMatches(userId: String, jobId: String) = Unit
}

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

private class FakeTelemetryProvider : TelemetryProvider {
    override val telemetry = MutableStateFlow<TelemetryData?>(null)
    override val odometerKilometers = MutableStateFlow<Double?>(null)

    override fun start() = Unit
    override fun stop() = Unit
    override fun setVehicleState(state: TelemetryVehicleState) = Unit
    override fun refreshVehicleId() = Unit
}

private class FakeGeoServiceRepository : GeoServiceRepository {
    override suspend fun resolveAddress(address: String) =
        ResolveAddressResult.InvalidResponse

    override suspend fun requestRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        language: String,
        headingDegrees: Int?
    ) = RouteResult.InvalidResponse
}

private class FakePricingRepository : PricingRepository {
    override suspend fun getPricePerKilometer() = PriceResult.Unavailable
}
