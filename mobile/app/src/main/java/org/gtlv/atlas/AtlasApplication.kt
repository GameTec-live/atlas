package org.gtlv.atlas

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.gtlv.core.job.JobRepositoryImpl
import org.gtlv.core.geoservice.GeoServiceRepositoryImpl

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

    override val telemetryLocationProvider: LocationProvider
        get() = locationProvider

    private val _telemetryProviderState =
        MutableStateFlow<TelemetryProvider?>(null)

    val telemetryProviderState: StateFlow<TelemetryProvider?> =
        _telemetryProviderState.asStateFlow()

    val telemetryProvider: TelemetryProvider?
        get() = _telemetryProviderState.value

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

    override fun registerTelemetryProvider(
        provider: TelemetryProvider
    ) {
        _telemetryProviderState.value = provider
    }

    override fun unregisterTelemetryProvider(
        provider: TelemetryProvider
    ) {
        if (_telemetryProviderState.value === provider) {
            _telemetryProviderState.value = null
        }
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
}
