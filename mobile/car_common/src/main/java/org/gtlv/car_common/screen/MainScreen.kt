package org.gtlv.car_common.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.annotations.RequiresCarApi
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gtlv.car_common.R
import org.gtlv.core.job.CollectedJobStore
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobsResult
import org.gtlv.core.location.LocationProvider
import org.gtlv.core.location.LocationState
import org.gtlv.core.settings.ServerSettingsRepository
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.telemetry.TelemetryProvider
import org.gtlv.core.telemetry.TelemetryVehicleState
import org.gtlv.core.telemetry.LiveMapUser
import kotlin.time.Duration.Companion.milliseconds
import org.gtlv.core.job.Job as AtlasJob
import androidx.core.graphics.createBitmap

class MainScreen(
    carContext: CarContext,
    private val role: ShiftRole,
    getRole: () -> ShiftRole?,
    onRoleLost: () -> Unit,
    private val jobRepository: JobRepository?,
    private val locationProvider: LocationProvider?,
    private val serverSettingsRepository: ServerSettingsRepository?,
    private val collectedJobStore: CollectedJobStore?,
    private val getUserId: () -> String?,
    private val telemetryProvider: TelemetryProvider?,
    private val liveMapUsers: StateFlow<Map<String, LiveMapUser>>?,
) : RoleAwareScreen(carContext, role, getRole, onRoleLost) {
    private val screenScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )
    private val jobRequestMutex = Mutex()
    private val mapRenderer = MapLibreSurfaceRenderer(
        carContext = carContext,
        showDispatcherDriverList = role == ShiftRole.DISPATCHER,
    )

    private var pollingJob: Job? = null
    private var locationJob: Job? = null
    private var styleJob: Job? = null
    private var collectedStateJob: Job? = null
    private var jobLifecycleJob: Job? = null
    private var liveMapUsersJob: Job? = null
    private var observedCollectedUserId: String? = null
    private var currentJob: AtlasJob? = null
    private var queuedJobs: List<AtlasJob> = emptyList()
    private var isLoading = true
    private var hasLoadError = false
    private var isStartingNextJob = false
    private var isCancellingCurrentJob = false
    private var isFinishingCurrentJob = false
    private var isPersonCollected = false

    init {
        carContext
            .getCarService(AppManager::class.java)
            .setSurfaceCallback(mapRenderer)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        observeMapConfiguration()
        observeLocation()
        observeCollectedJobState()
        observeJobLifecycle()
        observeLiveMapUsers()
        startJobPolling()
    }

    override fun onStop(owner: LifecycleOwner) {
        pollingJob?.cancel()
        pollingJob = null
        locationJob?.cancel()
        locationJob = null
        styleJob?.cancel()
        styleJob = null
        collectedStateJob?.cancel()
        collectedStateJob = null
        jobLifecycleJob?.cancel()
        jobLifecycleJob = null
        liveMapUsersJob?.cancel()
        liveMapUsersJob = null
        observedCollectedUserId = null
        super.onStop(owner)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        carContext
            .getCarService(AppManager::class.java)
            .setSurfaceCallback(null)
        mapRenderer.release()
        screenScope.cancel()
        super.onDestroy(owner)
    }

    override fun onGetTemplate(): Template {
        val isJobActionInProgress =
            isStartingNextJob ||
                isCancellingCurrentJob ||
                isFinishingCurrentJob
        val jobActionBuilder = Action.Builder()
        when {
            currentJob == null -> {
                jobActionBuilder.setTitle(
                    carContext.getString(R.string.driver_next_job),
                )
                if (queuedJobs.isNotEmpty()) {
                    jobActionBuilder.setOnClickListener(::startNextJob)
                }
            }
            isPersonCollected -> jobActionBuilder
                .setTitle(carContext.getString(R.string.driver_job_finished))
                .setOnClickListener(::finishCurrentJob)
            else -> jobActionBuilder
                .setTitle(carContext.getString(R.string.driver_person_collected))
                .setOnClickListener(::personCollected)
        }

        if (carContext.carAppApiLevel >= 4) {
            val flags = if (carContext.carAppApiLevel >= 5) {
                Action.FLAG_PRIMARY or Action.FLAG_IS_PERSISTENT
            } else {
                Action.FLAG_PRIMARY
            }
            jobActionBuilder
                .setFlags(flags)
                .setBackgroundColor(CarColor.BLUE)
            if (
                carContext.carAppApiLevel >= 5 &&
                (
                    (currentJob == null && queuedJobs.isEmpty()) ||
                        isJobActionInProgress
                    )
            ) {
                jobActionBuilder.setEnabled(false)
            }
        }

        val jobAction = jobActionBuilder.build()

        val actionStripBuilder = ActionStrip.Builder()
        if (currentJob != null) {
            val cancelActionBuilder = Action.Builder()
                .setIcon(carIcon(R.drawable.ic_close))
                .setOnClickListener(::cancelCurrentJob)

            if (carContext.carAppApiLevel >= 5) {
                cancelActionBuilder
                    .setFlags(Action.FLAG_IS_PERSISTENT)
                    .setEnabled(!isJobActionInProgress)
            }

            actionStripBuilder.addAction(
                cancelActionBuilder.build()
            )
        }

        if (role == ShiftRole.DISPATCHER) {
            val newJobActionBuilder = Action.Builder()
                .setIcon(carIcon(R.drawable.ic_add))
                .setOnClickListener(::onNewJobClick)

            if (carContext.carAppApiLevel >= 5) {
                newJobActionBuilder.setFlags(Action.FLAG_IS_PERSISTENT)
            }

            actionStripBuilder.addAction(newJobActionBuilder.build())
        }

        actionStripBuilder.addAction(jobAction)

        val builder = NavigationTemplate.Builder()
            .setActionStrip(actionStripBuilder.build())

        if (carContext.carAppApiLevel >= 2) {
            addInteractiveMapControls(builder)
        }

        return builder.build()
    }

    @RequiresCarApi(2)
    private fun addInteractiveMapControls(
        builder: NavigationTemplate.Builder,
    ) {
        builder
            .setMapActionStrip(buildMapActionStrip())
            .setPanModeListener(::onPanModeChanged)
    }

    @RequiresCarApi(2)
    private fun buildMapActionStrip(): ActionStrip {
        val panAction = Action.Builder(Action.PAN)
            .setIcon(carIcon(R.drawable.ic_pan))
            .build()
        val zoomInActionBuilder = Action.Builder()
            .setIcon(carIcon(R.drawable.ic_zoom_in))
            .setOnClickListener(mapRenderer::zoomIn)
        val zoomOutActionBuilder = Action.Builder()
            .setIcon(carIcon(R.drawable.ic_zoom_out))
            .setOnClickListener(mapRenderer::zoomOut)
        val recenterActionBuilder = Action.Builder()
            .setIcon(carIcon(R.drawable.ic_recenter))
            .setOnClickListener(mapRenderer::recenter)

        if (carContext.carAppApiLevel >= 5) {
            zoomInActionBuilder.setFlags(Action.FLAG_IS_PERSISTENT)
            zoomOutActionBuilder.setFlags(Action.FLAG_IS_PERSISTENT)
            recenterActionBuilder.setFlags(Action.FLAG_IS_PERSISTENT)
        }

        return ActionStrip.Builder()
            .addAction(panAction)
            .addAction(zoomInActionBuilder.build())
            .addAction(zoomOutActionBuilder.build())
            .addAction(recenterActionBuilder.build())
            .build()
    }

    private fun onPanModeChanged(isInPanMode: Boolean) {
        if (isInPanMode) {
            mapRenderer.stopFollowingLocation()
        }
    }

    private fun carIcon(resourceId: Int): CarIcon {
        val size = (CAR_ICON_SIZE_DP * carContext.resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val drawable = requireNotNull(
            AppCompatResources.getDrawable(carContext, resourceId),
        )
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))

        return CarIcon.Builder(
            IconCompat.createWithBitmap(bitmap),
        ).build()
    }

    private fun observeMapConfiguration() {
        if (styleJob != null) return
        val repository = serverSettingsRepository ?: return

        styleJob = screenScope.launch {
            repository.serverAddress.collectLatest { serverAddress ->
                if (serverAddress.isNotBlank()) {
                    mapRenderer.setStyleUrl(
                        "${serverAddress.trimEnd('/')}/map/style/liberty",
                    )
                }
            }
        }
    }

    private fun observeLocation() {
        if (locationJob != null) return
        val provider = locationProvider ?: return

        locationJob = screenScope.launch {
            provider.state.collectLatest { state ->
                val location = (state as? LocationState.Available)?.location
                    ?: return@collectLatest
                mapRenderer.updateLocation(location)
            }
        }
    }

    private fun startJobPolling() {
        if (pollingJob != null) return

        pollingJob = screenScope.launch {
            while (isActive) {
                refreshJobs()
                delay(JOB_REFRESH_INTERVAL_MILLIS.milliseconds)
            }
        }
    }

    private fun observeJobLifecycle() {
        if (jobLifecycleJob != null) return
        val repository = jobRepository ?: return

        jobLifecycleJob = screenScope.launch {
            repository.jobChanges.collectLatest {
                refreshJobs()
            }
        }
    }

    private suspend fun refreshJobs() {
        val repository = jobRepository
        if (repository == null) {
            isLoading = false
            hasLoadError = true
            updateJobOverlay()
            invalidateSafely()
            return
        }

        jobRequestMutex.withLock {
            when (val result = repository.getJobs()) {
                is JobsResult.Success -> {
                    currentJob = result.currentJob
                    queuedJobs = result.queuedJobs
                    mapRenderer.updateQueuedJobCount(queuedJobs.size)
                    updateCollectedState()
                    hasLoadError = false
                }

                else -> hasLoadError = true
            }
            isLoading = false
            updateJobOverlay()
            invalidateSafely()
        }
    }

    private fun startNextJob() {
        when {
            isStartingNextJob ||
                isCancellingCurrentJob ||
                isFinishingCurrentJob -> return
            currentJob != null -> showToast(R.string.driver_job_already_active)
            queuedJobs.isEmpty() -> showToast(R.string.driver_no_next_job)
            jobRepository == null -> showToast(R.string.driver_start_job_error)
            else -> {
                val nextJob = queuedJobs.first()
                isStartingNextJob = true
                showToast(R.string.driver_starting_job)

                screenScope.launch {
                    val result = jobRequestMutex.withLock {
                        jobRepository.startJob(nextJob.id)
                    }
                    isStartingNextJob = false

                    if (result == JobActionResult.Success) {
                        refreshJobs()
                    } else {
                        showToast(R.string.driver_start_job_error)
                        invalidateSafely()
                    }
                }
            }
        }
    }

    private fun personCollected() {
        val job = currentJob ?: return
        if (
            isPersonCollected ||
            isStartingNextJob ||
            isCancellingCurrentJob ||
            isFinishingCurrentJob
        ) return

        val userId = getUserId()
        val store = collectedJobStore
        if (userId == null || store == null) {
            showToast(R.string.driver_person_collected_error)
            return
        }

        store.setCollectedJobId(userId, job.id)
        isPersonCollected = true
        telemetryProvider?.setVehicleState(TelemetryVehicleState.OCCUPIED)
        invalidateSafely()
    }

    private fun cancelCurrentJob() {
        endCurrentJob(complete = false)
    }

    private fun finishCurrentJob() {
        endCurrentJob(complete = true)
    }

    private fun endCurrentJob(
        complete: Boolean
    ) {
        val job = currentJob ?: return
        val repository = jobRepository
        if (
            isStartingNextJob ||
            isCancellingCurrentJob ||
            isFinishingCurrentJob ||
            (complete && !isPersonCollected)
        ) return

        if (repository == null) {
            showToast(
                if (complete) {
                    R.string.driver_finish_job_error
                } else {
                    R.string.driver_cancel_job_error
                }
            )
            return
        }

        if (complete) {
            isFinishingCurrentJob = true
        } else {
            isCancellingCurrentJob = true
        }
        invalidateSafely()

        screenScope.launch {
            val result = jobRequestMutex.withLock {
                if (complete) {
                    repository.completeJob(job.id)
                } else {
                    repository.cancelJob(job.id)
                }
            }

            isFinishingCurrentJob = false
            isCancellingCurrentJob = false

            if (result == JobActionResult.Success) {
                getUserId()?.let { userId ->
                    collectedJobStore
                        ?.clearCollectedJobId(userId)
                }
                currentJob = null
                isPersonCollected = false
                telemetryProvider?.setVehicleState(
                    TelemetryVehicleState.FREE
                )
                updateJobOverlay()
                invalidateSafely()
                refreshJobs()
            } else {
                showToast(
                    if (complete) {
                        R.string.driver_finish_job_error
                    } else {
                        R.string.driver_cancel_job_error
                    }
                )
                invalidateSafely()
            }
        }
    }

    private fun observeCollectedJobState() {
        val userId = getUserId()
        val store = collectedJobStore
        if (
            userId == null ||
            store == null ||
            (observedCollectedUserId == userId && collectedStateJob != null)
        ) {
            return
        }

        collectedStateJob?.cancel()
        observedCollectedUserId = userId
        collectedStateJob = screenScope.launch {
            store.observeCollectedJobId(userId).collectLatest { storedJobId ->
                val personCollected =
                    currentJob?.id == storedJobId && storedJobId != null
                if (isPersonCollected == personCollected) {
                    return@collectLatest
                }

                isPersonCollected = personCollected
                updateVehicleTelemetry()
                invalidateSafely()
            }
        }
    }

    private fun updateCollectedState() {
        observeCollectedJobState()
        val userId = getUserId()
        val storedJobId = userId?.let { collectedJobStore?.getCollectedJobId(it) }
        isPersonCollected = currentJob != null && currentJob?.id == storedJobId

        if (storedJobId != null && !isPersonCollected) {
            collectedJobStore?.clearCollectedJobId(userId)
        }

        updateVehicleTelemetry()
    }

    private fun updateVehicleTelemetry() {
        telemetryProvider?.setVehicleState(
            when {
                currentJob == null -> TelemetryVehicleState.FREE
                isPersonCollected -> TelemetryVehicleState.OCCUPIED
                else -> TelemetryVehicleState.ON_THE_WAY
            },
        )
    }

    private fun currentJobText(): String = when {
        isLoading -> carContext.getString(R.string.driver_loading_job)
        hasLoadError && currentJob == null ->
            carContext.getString(R.string.driver_job_load_error)
        currentJob == null -> carContext.getString(R.string.driver_no_job)
        else -> carContext.getString(
            R.string.driver_job_route,
            currentJob?.fromDisplayAddress(),
            currentJob?.toDisplayAddress(),
        )
    }

    private fun updateJobOverlay() {
        mapRenderer.updateJobSummary(currentJobText())
    }

    private fun AtlasJob.fromDisplayAddress(): String = fromAddress
        ?: from?.let { "${it.latitude}, ${it.longitude}" }
        ?: carContext.getString(R.string.driver_unknown_address)

    private fun AtlasJob.toDisplayAddress(): String = toAddress
        ?: to?.let { "${it.latitude}, ${it.longitude}" }
        ?: carContext.getString(R.string.driver_unknown_address)

    private fun showToast(messageResource: Int) {
        CarToast.makeText(
            carContext,
            carContext.getString(messageResource),
            CarToast.LENGTH_SHORT,
        ).show()
    }

    private fun observeLiveMapUsers() {
        if (
            role != ShiftRole.DISPATCHER ||
            liveMapUsersJob != null
        ) {
            return
        }
        mapRenderer.updateLiveUsers(TEMPORARY_SCROLL_TEST_USERS) // remove after UI tested successfully
        val users = liveMapUsers ?: return

        liveMapUsersJob = screenScope.launch {
            users.collectLatest { liveUsersById ->
                val currentUserId = getUserId()
                val visibleUsers = liveUsersById.values
                    .asSequence()
                    .filter { user -> user.userId != currentUserId }
                    .plus(TEMPORARY_SCROLL_TEST_USERS.asSequence()) // remove after UI tested successfully
                    .toList()

                mapRenderer.updateLiveUsers(visibleUsers)
            }
        }
    }

    private fun onNewJobClick() {
        showToast(R.string.dispatcher_new_job_unavailable)
    }

    private fun invalidateSafely() {
        runCatching { invalidate() }
    }

    private companion object {
        // Temporary data for validating sidebar scrolling. Remove after UI approval.
        val TEMPORARY_SCROLL_TEST_USERS = listOf(
            temporaryUser("01", "Alex", TelemetryVehicleState.FREE),
            temporaryUser("02", "Anna", TelemetryVehicleState.ON_THE_WAY),
            temporaryUser("03", "Ben", TelemetryVehicleState.OCCUPIED),
            temporaryUser("04", "Clara", TelemetryVehicleState.AWAY),
            temporaryUser("05", "Daniel", TelemetryVehicleState.FREE),
            temporaryUser("06", "Eva", TelemetryVehicleState.ON_THE_WAY),
            temporaryUser("07", "Felix", TelemetryVehicleState.OCCUPIED),
            temporaryUser("08", "Greta", TelemetryVehicleState.AWAY),
            temporaryUser("09", "Jonas", TelemetryVehicleState.FREE),
            temporaryUser("10", "Laura", TelemetryVehicleState.ON_THE_WAY),
            temporaryUser("11", "Lukas", TelemetryVehicleState.OCCUPIED),
            temporaryUser("12", "Mia", TelemetryVehicleState.AWAY),
            temporaryUser("13", "Noah", TelemetryVehicleState.FREE),
        )

        fun temporaryUser(
            id: String,
            name: String,
            state: TelemetryVehicleState,
        ) = LiveMapUser(
            userId = "scroll-test-$id",
            userName = name,
            latitude = 0.0,
            longitude = 0.0,
            state = state,
        )

        const val JOB_REFRESH_INTERVAL_MILLIS = 15_000L
        const val CAR_ICON_SIZE_DP = 48
    }
}
