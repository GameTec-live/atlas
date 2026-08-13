package org.gtlv.atlas

import android.app.Application
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
import org.gtlv.core.job.JobRepositoryImpl

class AtlasApplication : Application(), ShiftSessionProvider,
    ServerSettingsProvider, CarLocationProviderRegistry {

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
}
