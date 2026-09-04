package org.gtlv.atlas

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.gtlv.core.location.CarAwareLocationProvider
import org.gtlv.core.location.CarLocationProviderRegistry
import org.gtlv.core.location.LocationProvider
import org.gtlv.core.location.LocationProviderProvider
import org.gtlv.core.location.PhoneLocationProvider
import org.gtlv.core.shift.ShiftSessionProvider
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.repository.AuthRepositoryImpl
import org.gtlv.core.role.RoleRepositoryImpl
import org.gtlv.core.session.SecureSessionStore
import org.gtlv.core.session.SessionManager
import org.gtlv.core.settings.DataStoreServerSettingsRepository
import org.gtlv.core.settings.ServerSettingsProvider
import org.gtlv.core.shift.DataStoreShiftSessionStore
import org.gtlv.core.shift.ShiftSessionManager
import org.gtlv.core.shift.ShiftSessionState
import org.gtlv.core.telemetry.TelemetryProvider
import org.gtlv.core.telemetry.TelemetryProviderRegistry
import org.gtlv.core.telemetry.Telemetry
import org.gtlv.core.job.JobRepositoryImpl
import org.gtlv.core.job.JobRepositoryProvider
import org.gtlv.core.job.JobNotification
import org.gtlv.core.job.JobNotificationSyncProvider
import org.gtlv.core.geoservice.GeoServiceRepositoryImpl
import org.gtlv.core.geoservice.GeoServiceRepositoryProvider
import org.gtlv.core.fleet.ConnectedVehicleManager
import org.gtlv.core.fleet.ConnectedVehicleStore
import org.gtlv.core.fleet.FleetRepository
import org.gtlv.core.fleet.FleetRepositoryImpl
import org.gtlv.core.fleet.FleetRepositoryProvider
import org.gtlv.core.logbook.LogbookRepository
import org.gtlv.core.logbook.LogbookRepositoryImpl
import org.gtlv.core.logbook.LogbookRepositoryProvider
import org.gtlv.core.telemetry.TelemetryWebSocketSender
import org.gtlv.core.telemetry.LiveMapUsersProvider
import org.gtlv.core.job.CollectedJobStore
import org.gtlv.core.job.CollectedJobStoreProvider
import org.gtlv.core.job.JobMileageStore
import org.gtlv.core.job.JobMileageStoreProvider
import org.gtlv.core.pricing.PricingRepository
import org.gtlv.core.pricing.PricingRepositoryImpl
import org.gtlv.core.pricing.PricingRepositoryProvider
import org.gtlv.core.session.SessionManagerProvider
import org.gtlv.atlas.notification.AppVisibilityTracker
import org.gtlv.atlas.notification.JobNotificationWebSocket
import org.gtlv.atlas.notification.JobNotificationSync
import org.gtlv.atlas.notification.JobSystemNotificationManager

class AtlasApplication : Application(), ShiftSessionProvider,
    ServerSettingsProvider, CarLocationProviderRegistry,
    TelemetryProviderRegistry, LocationProviderProvider,
    JobRepositoryProvider, CollectedJobStoreProvider,
    JobMileageStoreProvider, PricingRepositoryProvider,
    SessionManagerProvider, LiveMapUsersProvider,
    GeoServiceRepositoryProvider, JobNotificationSyncProvider,
    FleetRepositoryProvider, LogbookRepositoryProvider {

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    private val phoneLocationProvider: LocationProvider by lazy {
        PhoneLocationProvider(
            context = applicationContext
        )
    }

    private val carAwareLocationProvider by lazy {
        CarAwareLocationProvider(
            phoneLocationProvider = phoneLocationProvider,
            scope = applicationScope
        )
    }

    override val locationProvider: LocationProvider
        get() = carAwareLocationProvider

    private val applicationTelemetry by lazy {
        Telemetry(
            locationProvider = locationProvider
        )
    }

    override val telemetryProvider: TelemetryProvider
        get() = applicationTelemetry

    override fun registerCarLocationProvider(
        provider: LocationProvider
    ) {
        carAwareLocationProvider.registerCarLocationProvider(provider)
    }

    override fun unregisterCarLocationProvider(
        provider: LocationProvider
    ) {
        carAwareLocationProvider.unregisterCarLocationProvider(provider)
    }

    override fun connectCarTelemetry(carContext: Context) {
        applicationTelemetry.connectCar(carContext)
    }

    override fun disconnectCarTelemetry(carContext: Context) {
        applicationTelemetry.disconnectCar(carContext)
    }

    val appVisibilityTracker =
        AppVisibilityTracker()

    val jobSystemNotificationManager by lazy {
        JobSystemNotificationManager(
            context = applicationContext
        )
    }

    val jobNotificationWebSocket by lazy {
        JobNotificationWebSocket(
            networkClient = networkClient,
            serverSettingsRepository =
                serverSettingsRepository,
            sessionManager = sessionManager,
            shiftSessionManager =
                shiftSessionManager,
            visibilityTracker =
                appVisibilityTracker,
            systemNotificationManager =
                jobSystemNotificationManager,
            scope = applicationScope
        )
    }

    val jobNotificationSync by lazy {
        JobNotificationSync(
            webSocket = jobNotificationWebSocket,
            scope = applicationScope
        )
    }

    override val jobNotifications
        get() = jobNotificationSync.jobNotifications

    override val resolvedJobNotifications
        get() = jobNotificationSync.resolvedJobNotifications

    override fun resolveJobNotification(notification: JobNotification) {
        jobNotificationSync.resolveJobNotification(notification)
    }

    val networkClient by lazy {
        NetworkClient()
    }

    override val serverSettingsRepository by lazy {
        DataStoreServerSettingsRepository(
            context = applicationContext
        )
    }

    val secureSessionStore by lazy {
        SecureSessionStore(
            context = applicationContext
        )
    }

    val authRepository by lazy {
        AuthRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository = serverSettingsRepository,
            secureSessionStore = secureSessionStore
        )
    }

    val shiftSessionStore by lazy {
        DataStoreShiftSessionStore(
            context = applicationContext
        )
    }

    override val shiftSessionManager by lazy {
        ShiftSessionManager(
            store = shiftSessionStore
        )
    }

    val roleRepository by lazy {
        RoleRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository = serverSettingsRepository
        )
    }

    override val fleetRepository: FleetRepository by lazy {
        FleetRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository = serverSettingsRepository
        )
    }

    override val logbookRepository: LogbookRepository by lazy {
        LogbookRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository = serverSettingsRepository
        )
    }

    private val connectedVehicleStore by lazy {
        ConnectedVehicleStore(applicationContext)
    }

    val connectedVehicleManager by lazy {
        ConnectedVehicleManager(
            telemetryProvider = telemetryProvider,
            sessionState = sessionManager.state,
            fleetRepository = fleetRepository,
            store = connectedVehicleStore
        )
    }

    override val sessionManager by lazy {
        SessionManager(
            authRepository = authRepository,
            roleRepository = roleRepository,
            shiftSessionManager = shiftSessionManager
        )
    }

    override val jobRepository by lazy {
        JobRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository =
                serverSettingsRepository
        )
    }

    override val geoServiceRepository by lazy {
        GeoServiceRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository =
                serverSettingsRepository
        )
    }

    val telemetryWebSocketSender by lazy {
        TelemetryWebSocketSender(
            networkClient = networkClient,
            serverSettingsRepository = serverSettingsRepository,
            sessionManager = sessionManager,
            shiftSessionManager = shiftSessionManager,
            telemetryProvider = telemetryProvider,
            scope = applicationScope
        )
    }

    override val liveMapUsers
        get() = telemetryWebSocketSender.liveMapUsers

    override val collectedJobStore by lazy {
        CollectedJobStore(
            context = applicationContext
        )
    }

    override val jobMileageStore by lazy {
        JobMileageStore(
            context = applicationContext
        )
    }

    override val pricingRepository: PricingRepository by lazy {
        PricingRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository =
                serverSettingsRepository
        )
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(
            appVisibilityTracker
        )

        jobSystemNotificationManager
            .createChannel()

        applicationTelemetry.start()
        connectedVehicleManager.start(applicationScope)
        observeShiftStartKilometer()
        telemetryWebSocketSender.start()
        jobNotificationSync
        jobNotificationWebSocket.start()
    }

    private fun observeShiftStartKilometer() {
        applicationScope.launch {
            combine(
                shiftSessionManager.state,
                applicationTelemetry.odometerKilometers
            ) { shiftState, odometerKilometers ->
                val session =
                    (shiftState as? ShiftSessionState.Active)
                        ?.session

                odometerKilometers.takeIf {
                    session != null &&
                        session.startKilometer == null
                }
                }
                .filterNotNull()
                .collect { odometerKilometers ->
                    try {
                        shiftSessionManager
                            .setStartKilometerIfAbsent(
                                odometerKilometers
                            )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.e(
                            TAG,
                            "Could not save the shift start odometer",
                            error
                        )
                    }
                }
        }
    }

    private companion object {
        const val TAG = "AtlasApplication"
    }
}
