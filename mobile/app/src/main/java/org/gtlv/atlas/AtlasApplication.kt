package org.gtlv.atlas

import android.app.Application
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.repository.AuthRepositoryImpl
import org.gtlv.core.role.RoleRepositoryImpl
import org.gtlv.core.session.SecureSessionStore
import org.gtlv.core.session.SessionManager
import org.gtlv.core.settings.DataStoreServerSettingsRepository
import org.gtlv.core.shift.DataStoreShiftSessionStore
import org.gtlv.core.shift.ShiftSessionManager
import org.gtlv.core.location.PhoneLocationProvider
import org.gtlv.core.location.LocationProvider

class AtlasApplication : Application() {

    val locationProvider: LocationProvider by lazy {
        PhoneLocationProvider(
            context = applicationContext
        )
    }
    val networkClient by lazy {
        NetworkClient()
    }

    val serverSettingsRepository by lazy {
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

    val shiftSessionManager by lazy {
        ShiftSessionManager(
            store = shiftSessionStore
        )
    }

    val roleRepository by lazy {
        RoleRepositoryImpl(
            networkClient = networkClient,
            serverSettingsRepository = serverSettingsRepository,
            accessTokenProvider = authRepository
        )
    }

    val sessionManager by lazy {
        SessionManager(
            authRepository = authRepository,
            roleRepository = roleRepository,
            shiftSessionManager = shiftSessionManager
        )
    }
}