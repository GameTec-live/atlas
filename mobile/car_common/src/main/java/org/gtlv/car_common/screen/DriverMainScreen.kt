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
import org.gtlv.core.job.Job as AtlasJob

class DriverMainScreen(
    carContext: CarContext,
    getRole: () -> ShiftRole?,
    onRoleLost: () -> Unit,
    private val jobRepository: JobRepository?,
    private val locationProvider: LocationProvider?,
    private val serverSettingsRepository: ServerSettingsRepository?,
    private val collectedJobStore: CollectedJobStore?,
    private val getUserId: () -> String?,
    private val telemetryProvider: TelemetryProvider?,
) : RoleAwareScreen(carContext, ShiftRole.DRIVER, getRole, onRoleLost) {
    private val screenScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )
    private val jobRequestMutex = Mutex()
    private val mapRenderer = MapLibreSurfaceRenderer(carContext)

    private var pollingJob: Job? = null
    private var locationJob: Job? = null
    private var styleJob: Job? = null
    private var collectedStateJob: Job? = null
    private var observedCollectedUserId: String? = null
    private var currentJob: AtlasJob? = null
    private var queuedJobs: List<AtlasJob> = emptyList()
    private var isLoading = true
    private var hasLoadError = false
    private var isStartingNextJob = false
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
                        (currentJob != null && isPersonCollected)
                    )
            ) {
                jobActionBuilder.setEnabled(false)
            }
        }

        val jobAction = jobActionBuilder.build()

        val builder = NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(jobAction)
                    .build(),
            )

        if (carContext.carAppApiLevel >= 2) {
            addInteractiveMapControls(builder)
        }

        return builder.build()
    }

    @RequiresCarApi(2)
    private fun addInteractiveMapControls(
        builder: NavigationTemplate.Builder,
    ) {
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

        builder
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(panAction)
                    .addAction(zoomInActionBuilder.build())
                    .addAction(zoomOutActionBuilder.build())
                    .addAction(recenterActionBuilder.build())
                    .build(),
            )
            .setPanModeListener { isInPanMode ->
                if (isInPanMode) {
                    mapRenderer.stopFollowingLocation()
                }
            }
    }

    private fun carIcon(resourceId: Int): CarIcon {
        val size = (CAR_ICON_SIZE_DP * carContext.resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(
            size,
            size,
            Bitmap.Config.ARGB_8888,
        )
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
                delay(JOB_REFRESH_INTERVAL_MILLIS)
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
            isStartingNextJob -> return
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
        if (isPersonCollected) return

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

    private fun invalidateSafely() {
        runCatching { invalidate() }
    }

    private companion object {
        const val JOB_REFRESH_INTERVAL_MILLIS = 15_000L
        const val CAR_ICON_SIZE_DP = 48
    }
}
