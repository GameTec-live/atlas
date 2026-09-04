package org.gtlv.car_common.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.text.NumberFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.gtlv.car_common.R
import org.gtlv.core.telemetry.TelemetryDiagnosticStatus
import org.gtlv.core.telemetry.TelemetryDiagnosticValue
import org.gtlv.core.telemetry.TelemetryDiagnostics
import org.gtlv.core.telemetry.TelemetryDiagnosticsProvider

/** Temporary screen used to diagnose vehicle-property support on real cars. */
class TelemetryDiagnosticsScreen(
    carContext: CarContext,
    private val provider: TelemetryDiagnosticsProvider,
) : Screen(carContext), DefaultLifecycleObserver {
    private val screenScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )
    private var diagnosticsJob: Job? = null
    private var diagnostics = provider.telemetryDiagnostics.value

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        diagnosticsJob?.cancel()
        diagnosticsJob = screenScope.launch {
            provider.telemetryDiagnostics.collectLatest { updated ->
                diagnostics = updated
                invalidate()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        diagnosticsJob?.cancel()
        diagnosticsJob = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        diagnosticsJob?.cancel()
        screenScope.cancel()
        lifecycle.removeObserver(this)
    }

    override fun onGetTemplate(): Template {
        val missingPermissions =
            carContext.missingVehicleTelemetryPermissions()
        val permissionStatus = if (missingPermissions.isEmpty()) {
            carContext.getString(
                R.string.telemetry_diagnostics_permissions_granted
            )
        } else {
            carContext.getString(
                R.string.telemetry_diagnostics_permissions_missing,
                missingPermissions.joinToString { permission ->
                    permission.substringAfterLast('.')
                }
            )
        }

        val message = carContext.getString(
            R.string.telemetry_diagnostics_message,
            diagnostics.carAppApiLevel?.toString()
                ?: carContext.getString(
                    R.string.telemetry_diagnostics_not_connected
                ),
            permissionStatus,
            diagnostics.hardware.formatStatus(),
            diagnostics.energyListener.formatStatus(),
            diagnostics.batteryPercent.formatPercent(),
            diagnostics.fuelPercent.formatPercent(),
            diagnostics.mileageListener.formatStatus(),
            diagnostics.odometerKilometers.formatKilometers(),
        )

        return MessageTemplate.Builder(message)
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.BACK)
                    .setTitle(
                        carContext.getString(
                            R.string.telemetry_diagnostics_title
                        )
                    )
                    .build()
            )
            .build()
    }

    private fun TelemetryDiagnosticValue.formatPercent(): String {
        return value?.takeIf {
            status == TelemetryDiagnosticStatus.SUCCESS
        }?.let { percent ->
            carContext.getString(
                R.string.telemetry_diagnostics_percent_value,
                numberFormatter().format(percent)
            )
        } ?: formatStatus()
    }

    private fun TelemetryDiagnosticValue.formatKilometers(): String {
        return value?.takeIf {
            status == TelemetryDiagnosticStatus.SUCCESS
        }?.let { kilometers ->
            carContext.getString(
                R.string.telemetry_diagnostics_kilometer_value,
                numberFormatter().format(kilometers)
            )
        } ?: formatStatus()
    }

    private fun TelemetryDiagnosticValue.formatStatus(): String {
        val statusText = carContext.getString(
            when (status) {
                TelemetryDiagnosticStatus.DISCONNECTED ->
                    R.string.telemetry_diagnostics_not_connected
                TelemetryDiagnosticStatus.WAITING ->
                    R.string.telemetry_diagnostics_waiting
                TelemetryDiagnosticStatus.SUCCESS ->
                    R.string.telemetry_diagnostics_success
                TelemetryDiagnosticStatus.UNSUPPORTED ->
                    R.string.telemetry_diagnostics_unsupported
                TelemetryDiagnosticStatus.UNIMPLEMENTED ->
                    R.string.telemetry_diagnostics_unimplemented
                TelemetryDiagnosticStatus.UNAVAILABLE ->
                    R.string.telemetry_diagnostics_unavailable
                TelemetryDiagnosticStatus.UNKNOWN ->
                    R.string.telemetry_diagnostics_unknown
                TelemetryDiagnosticStatus.FAILED ->
                    R.string.telemetry_diagnostics_failed
            }
        )

        return detail?.let { detail ->
            carContext.getString(
                R.string.telemetry_diagnostics_status_detail,
                statusText,
                detail
            )
        } ?: statusText
    }

    private fun numberFormatter(): NumberFormat {
        return NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
    }
}
