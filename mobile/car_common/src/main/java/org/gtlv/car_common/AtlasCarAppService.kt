package org.gtlv.car_common

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import org.gtlv.car_common.screen.MainScreen
import org.gtlv.car_common.screen.WaitingScreen

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
        // Replace this reader with the upcoming core role state. Core should call
        // GET http://192.168.1.200:1030/roles/ with the phone's bearer session and return true only
        // when roles contains an entry whose driverId matches the authenticated user's id.
        // Null means loading/error and deliberately keeps role-specific features inaccessible.
        val hasRole: () -> Boolean? = { true }

        return WaitingScreen(
            carContext = carContext,
            hasRole = hasRole,
            onRoleAvailable = {
                carContext.getCarService(ScreenManager::class.java).push(
                    MainScreen(
                        carContext = carContext,
                        hasRole = hasRole,
                        onRoleLost = {
                            carContext.getCarService(ScreenManager::class.java).pop()
                        },
                    ),
                )
            },
        )
    }
}
