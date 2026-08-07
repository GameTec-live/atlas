package org.gtlv.car_common

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import org.gtlv.car_common.screen.DispatcherMainScreen
import org.gtlv.car_common.screen.DriverMainScreen
import org.gtlv.car_common.screen.WaitingScreen
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.shift.ShiftSessionState
import org.gtlv.core.shift.ShiftSessionProvider
import org.gtlv.core.settings.ServerSettingsProvider

class AtlasCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return AtlasSession()
    }
}

class AtlasSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val shiftSessionManager =
            (carContext.applicationContext as? ShiftSessionProvider)
                ?.shiftSessionManager
        val getRole: () -> ShiftRole? = {
            (shiftSessionManager?.state?.value as? ShiftSessionState.Active)
                ?.session
                ?.role
        }
        val serverSettingsRepository =
            (carContext.applicationContext as? ServerSettingsProvider)
                ?.serverSettingsRepository

        return WaitingScreen(
            carContext = carContext,
            getRole = getRole,
            serverSettingsRepository = serverSettingsRepository,
            onRoleAvailable = { role ->
                val onRoleLost: () -> Unit = {
                    carContext.getCarService(ScreenManager::class.java).pop()
                }
                val roleScreen = when (role) {
                    ShiftRole.DRIVER -> DriverMainScreen(carContext, getRole, onRoleLost)
                    ShiftRole.DISPATCHER -> DispatcherMainScreen(carContext, getRole, onRoleLost)
                }

                carContext.getCarService(ScreenManager::class.java).push(roleScreen)
            },
        )
    }
}
