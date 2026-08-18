package org.gtlv.car_common

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.gtlv.car_common.screen.DispatcherMainScreen
import org.gtlv.car_common.screen.DriverMainScreen
import org.gtlv.car_common.screen.TelemetryPermissionScreen
import org.gtlv.car_common.screen.WaitingScreen
import org.gtlv.car_common.screen.missingVehicleTelemetryPermissions
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.shift.ShiftSessionState
import org.gtlv.core.shift.ShiftSessionProvider
import org.gtlv.core.settings.ServerSettingsProvider
import org.gtlv.core.location.CarLocationProvider
import org.gtlv.core.location.CarLocationProviderRegistry
import org.gtlv.core.telemetry.Telemetry
import org.gtlv.core.telemetry.TelemetryProviderRegistry

class AtlasCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return AtlasSession()
    }
}

class AtlasSession : Session(), DefaultLifecycleObserver {
    private var carLocationProvider: CarLocationProvider? = null
    private var carLocationProviderRegistry: CarLocationProviderRegistry? = null
    private var telemetryProvider: Telemetry? = null
    private var telemetryProviderRegistry: TelemetryProviderRegistry? = null
    private var telemetryPermissionScreen: TelemetryPermissionScreen? = null

    override fun onCreateScreen(intent: Intent): Screen {
        connectCarLocationProvider()
        connectTelemetryProvider()

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

    override fun onStart(owner: LifecycleOwner) {
        carLocationProvider?.start()
        telemetryProvider?.start()
        showTelemetryPermissionScreenIfNeeded()
    }

    override fun onStop(owner: LifecycleOwner) {
        telemetryProvider?.stop()
        carLocationProvider?.stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        val currentLocationProvider = carLocationProvider
        val currentTelemetryProvider = telemetryProvider

        if (currentTelemetryProvider != null) {
            currentTelemetryProvider.stop()
            telemetryProviderRegistry
                ?.unregisterTelemetryProvider(currentTelemetryProvider)
        }

        if (currentLocationProvider != null) {
            currentLocationProvider.stop()
            carLocationProviderRegistry
                ?.unregisterCarLocationProvider(currentLocationProvider)
        }

        carLocationProvider = null
        carLocationProviderRegistry = null
        telemetryProvider = null
        telemetryProviderRegistry = null
        telemetryPermissionScreen = null
        lifecycle.removeObserver(this)
    }

    private fun connectCarLocationProvider() {
        if (carLocationProvider != null) return

        val registry = carContext.applicationContext
            as? CarLocationProviderRegistry
            ?: return

        val provider = CarLocationProvider(carContext)

        carLocationProviderRegistry = registry
        carLocationProvider = provider
        registry.registerCarLocationProvider(provider)
        lifecycle.addObserver(this)
    }

    private fun connectTelemetryProvider() {
        if (telemetryProvider != null) return

        val registry = carContext.applicationContext
            as? TelemetryProviderRegistry
            ?: return

        val provider = Telemetry(
            carContext = carContext,
            locationProvider = registry.telemetryLocationProvider
        )

        telemetryProviderRegistry = registry
        telemetryProvider = provider
        registry.registerTelemetryProvider(provider)
    }

    private fun showTelemetryPermissionScreenIfNeeded() {
        val provider = telemetryProvider ?: return

        if (
            carContext
                .missingVehicleTelemetryPermissions()
                .isEmpty()
        ) {
            provider.refreshVehicleId()
            return
        }

        if (telemetryPermissionScreen != null) return

        val permissionScreen = TelemetryPermissionScreen(
            carContext = carContext,
            onPermissionsGranted = {
                provider.stop()
                provider.start()
            },
            onDestroyed = {
                telemetryPermissionScreen = null
            }
        )

        telemetryPermissionScreen = permissionScreen
        carContext
            .getCarService(ScreenManager::class.java)
            .push(permissionScreen)
    }
}
