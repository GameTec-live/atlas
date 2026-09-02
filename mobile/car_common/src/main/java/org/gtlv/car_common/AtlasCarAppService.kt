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
import org.gtlv.car_common.screen.MainScreen
import org.gtlv.car_common.screen.TelemetryPermissionScreen
import org.gtlv.car_common.screen.WaitingScreen
import org.gtlv.car_common.screen.missingVehicleTelemetryPermissions
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.shift.ShiftSessionState
import org.gtlv.core.shift.ShiftSessionProvider
import org.gtlv.core.settings.ServerSettingsProvider
import org.gtlv.core.location.CarLocationProvider
import org.gtlv.core.location.CarLocationProviderRegistry
import org.gtlv.core.location.LocationProviderProvider
import org.gtlv.core.job.JobRepositoryProvider
import org.gtlv.core.job.JobNotificationSyncProvider
import org.gtlv.core.job.CollectedJobStoreProvider
import org.gtlv.core.job.JobMileageStoreProvider
import org.gtlv.core.geoservice.GeoServiceRepositoryProvider
import org.gtlv.core.session.SessionManagerProvider
import org.gtlv.core.session.SessionState
import org.gtlv.core.telemetry.TelemetryProvider
import org.gtlv.core.telemetry.TelemetryProviderRegistry
import org.gtlv.core.telemetry.LiveMapUsersProvider
import org.gtlv.core.pricing.PricingRepositoryProvider

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
    private var telemetryProvider: TelemetryProvider? = null
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
        val getStartKilometer: () -> Double? = {
            (shiftSessionManager?.state?.value as? ShiftSessionState.Active)
                ?.session
                ?.startKilometer
        }
        val serverSettingsRepository =
            (carContext.applicationContext as? ServerSettingsProvider)
                ?.serverSettingsRepository
        val jobRepository =
            (carContext.applicationContext as? JobRepositoryProvider)
                ?.jobRepository
        val locationProvider =
            (carContext.applicationContext as? LocationProviderProvider)
                ?.locationProvider
        val collectedJobStore =
            (carContext.applicationContext as? CollectedJobStoreProvider)
                ?.collectedJobStore
        val jobMileageStore =
            (carContext.applicationContext as? JobMileageStoreProvider)
                ?.jobMileageStore
        val pricingRepository =
            (carContext.applicationContext as? PricingRepositoryProvider)
                ?.pricingRepository
        val geoServiceRepository =
            (carContext.applicationContext as? GeoServiceRepositoryProvider)
                ?.geoServiceRepository
        val sessionManager =
            (carContext.applicationContext as? SessionManagerProvider)
                ?.sessionManager
        val getUserId: () -> String? = {
            (sessionManager?.state?.value as? SessionState.SignedIn)
                ?.userId
        }
        val liveMapUsers =
            (carContext.applicationContext as? LiveMapUsersProvider)
                ?.liveMapUsers
        val jobNotificationSync =
            carContext.applicationContext as? JobNotificationSyncProvider

        return WaitingScreen(
            carContext = carContext,
            getRole = getRole,
            serverSettingsRepository = serverSettingsRepository,
            onRoleAvailable = { role ->
                val onRoleLost: () -> Unit = {
                    carContext.getCarService(ScreenManager::class.java).pop()
                }
                val roleScreen = MainScreen(
                    carContext = carContext,
                    role = role,
                    getRole = getRole,
                    onRoleLost = onRoleLost,
                    jobRepository = jobRepository,
                    locationProvider = locationProvider,
                    serverSettingsRepository = serverSettingsRepository,
                    collectedJobStore = collectedJobStore,
                    jobMileageStore = jobMileageStore,
                    pricingRepository = pricingRepository,
                    geoServiceRepository = geoServiceRepository,
                    getUserId = getUserId,
                    getStartKilometer = getStartKilometer,
                    telemetryProvider = telemetryProvider,
                    liveMapUsers = liveMapUsers,
                    jobNotifications = jobNotificationSync?.jobNotifications,
                    resolveJobNotification =
                        jobNotificationSync?.let { sync ->
                            sync::resolveJobNotification
                        },
                )

                carContext.getCarService(ScreenManager::class.java).push(roleScreen)
            },
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        carLocationProvider?.start()
        telemetryProviderRegistry?.connectCarTelemetry(carContext)
        showTelemetryPermissionScreenIfNeeded()
    }

    override fun onStop(owner: LifecycleOwner) {
        telemetryProviderRegistry?.disconnectCarTelemetry(carContext)
        carLocationProvider?.stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        val currentLocationProvider = carLocationProvider
        telemetryProviderRegistry?.disconnectCarTelemetry(carContext)

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

        telemetryProviderRegistry = registry
        telemetryProvider = registry.telemetryProvider
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
                telemetryProviderRegistry
                    ?.disconnectCarTelemetry(carContext)
                telemetryProviderRegistry
                    ?.connectCarTelemetry(carContext)
                provider.refreshVehicleId()
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
