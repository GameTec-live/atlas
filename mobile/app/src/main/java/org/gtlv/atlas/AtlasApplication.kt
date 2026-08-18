package org.gtlv.atlas

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.gtlv.core.location.CarAwareLocationProvider
import org.gtlv.core.location.CarLocationProviderRegistry
import org.gtlv.core.location.LocationProvider
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
import org.gtlv.core.telemetry.TelemetryProvider
import org.gtlv.core.telemetry.TelemetryProviderRegistry
import org.gtlv.core.telemetry.Telemetry
import org.gtlv.core.job.JobRepositoryImpl
import org.gtlv.core.geoservice.GeoServiceRepositoryImpl
import org.gtlv.core.telemetry.TelemetryWebSocketSender

class AtlasApplication : Application(), ShiftSessionProvider,
    ServerSettingsProvider, CarLocationProviderRegistry,
    TelemetryProviderRegistry {

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

    val locationProvider: LocationProvider
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

    val sessionManager by lazy {
        SessionManager(
            authRepository = authRepository,
            roleRepository = roleRepository,
            shiftSessionManager = shiftSessionManager
        )
    }

    val jobRepository by lazy {
        JobRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository =
                serverSettingsRepository
        )
    }

    val geoServiceRepository by lazy {
        GeoServiceRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository =
                serverSettingsRepository
        )
    }

    private val telemetryWebSocketSender by lazy {
        TelemetryWebSocketSender(
            networkClient = networkClient,
            serverSettingsRepository = serverSettingsRepository,
            sessionManager = sessionManager,
            shiftSessionManager = shiftSessionManager,
            telemetryProvider = telemetryProvider,
            scope = applicationScope
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationTelemetry.start()
        telemetryWebSocketSender.start()
    }
}
