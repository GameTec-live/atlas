package org.gtlv.car_common.screen

import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.gtlv.car_common.R
import org.gtlv.core.telemetry.Telemetry

/** Requests all vehicle telemetry permissions from one parked-only action. */
class TelemetryPermissionScreen(
    carContext: CarContext,
    private val onPermissionsGranted: () -> Unit,
    private val onDestroyed: () -> Unit
) : Screen(carContext), DefaultLifecycleObserver {
    private var requestInFlight = false
    private var permissionDenied = false

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        val message = when {
            requestInFlight -> R.string.telemetry_permission_waiting
            permissionDenied -> R.string.telemetry_permission_denied
            else -> R.string.telemetry_permission_explanation
        }

        val builder = MessageTemplate.Builder(carContext.getString(message))
            .setHeader(
                Header.Builder()
                    .setTitle(
                        carContext.getString(
                            R.string.telemetry_permission_title
                        )
                    )
                    .build()
            )

        if (!requestInFlight) {
            builder.addAction(
                Action.Builder()
                    .setTitle(
                        carContext.getString(
                            R.string.telemetry_permission_action
                        )
                    )
                    .setOnClickListener(
                        // maybe remove ParkedOnlyOnClickListener
                        ParkedOnlyOnClickListener.create {
                            requestTelemetryPermissions()
                        }
                    )
                    .build()
            )
        }

        return builder.build()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        lifecycle.removeObserver(this)
        onDestroyed()
    }

    private fun requestTelemetryPermissions() {
        val missingPermissions =
            carContext.missingVehicleTelemetryPermissions()

        if (missingPermissions.isEmpty()) {
            finishWithPermissions()
            return
        }

        requestInFlight = true
        permissionDenied = false
        invalidate()

        carContext.requestPermissions(
            missingPermissions,
            carContext.mainExecutor
        ) { _, _ ->
            requestInFlight = false

            if (
                carContext
                    .missingVehicleTelemetryPermissions()
                    .isEmpty()
            ) {
                finishWithPermissions()
            } else {
                permissionDenied = true
                invalidate()
            }
        }
    }

    private fun finishWithPermissions() {
        onPermissionsGranted()
        carContext
            .getCarService(ScreenManager::class.java)
            .pop()
    }
}

internal fun CarContext.missingVehicleTelemetryPermissions(): List<String> {
    return Telemetry.VEHICLE_PERMISSIONS.filter { permission ->
        ContextCompat.checkSelfPermission(
            this,
            permission
        ) != PackageManager.PERMISSION_GRANTED
    }
}
