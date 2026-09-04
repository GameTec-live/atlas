package org.gtlv.atlas.main.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import org.gtlv.atlas.R
import org.gtlv.core.telemetry.TelemetryDiagnosticStatus
import org.gtlv.core.telemetry.TelemetryDiagnosticValue
import org.gtlv.core.telemetry.TelemetryDiagnostics

@Composable
internal fun OdometerDebugPanel(
    odometerKilometers: Double?,
    diagnostics: TelemetryDiagnostics?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.debug_odometer_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            DebugValue(
                label = stringResource(R.string.debug_car_api),
                value = diagnostics?.carAppApiLevel?.toString()
                    ?: stringResource(R.string.debug_status_disconnected)
            )
            DebugValue(
                label = stringResource(R.string.debug_hardware_api),
                value = diagnostics?.hardware?.statusText()
                    ?: stringResource(R.string.debug_status_disconnected)
            )
            DebugValue(
                label = stringResource(R.string.debug_energy_listener),
                value = diagnostics?.energyListener?.statusText()
                    ?: stringResource(R.string.debug_status_waiting)
            )
            DebugValue(
                label = stringResource(R.string.debug_battery),
                value = diagnostics?.batteryPercent?.percentText()
                    ?: stringResource(R.string.debug_status_waiting)
            )
            DebugValue(
                label = stringResource(R.string.debug_fuel),
                value = diagnostics?.fuelPercent?.percentText()
                    ?: stringResource(R.string.debug_status_waiting)
            )
            DebugValue(
                label = stringResource(R.string.debug_mileage_listener),
                value = diagnostics?.mileageListener?.statusText()
                    ?: stringResource(R.string.debug_status_waiting)
            )
            DebugValue(
                label = stringResource(R.string.debug_odometer),
                value = diagnostics?.odometerKilometers
                    ?.kilometerText()
                    ?: odometerKilometers?.let { value ->
                        stringResource(
                            R.string.debug_odometer_value,
                            numberFormatter(3).format(value)
                        )
                    }
                    ?: stringResource(R.string.debug_status_waiting)
            )
        }
    }
}

@Composable
private fun DebugValue(
    label: String,
    value: String,
) {
    Text(
        text = stringResource(R.string.debug_value, label, value),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun TelemetryDiagnosticValue.percentText(): String {
    return value?.takeIf {
        status == TelemetryDiagnosticStatus.SUCCESS
    }?.let { percent ->
        stringResource(
            R.string.debug_percent_value,
            numberFormatter().format(percent)
        )
    } ?: statusText()
}

@Composable
private fun TelemetryDiagnosticValue.kilometerText(): String {
    return value?.takeIf {
        status == TelemetryDiagnosticStatus.SUCCESS
    }?.let { kilometers ->
        stringResource(
            R.string.debug_odometer_value,
            numberFormatter(3).format(kilometers)
        )
    } ?: statusText()
}

@Composable
private fun TelemetryDiagnosticValue.statusText(): String {
    val statusText = stringResource(
        when (status) {
            TelemetryDiagnosticStatus.DISCONNECTED ->
                R.string.debug_status_disconnected
            TelemetryDiagnosticStatus.WAITING ->
                R.string.debug_status_waiting
            TelemetryDiagnosticStatus.SUCCESS ->
                R.string.debug_status_success
            TelemetryDiagnosticStatus.UNSUPPORTED ->
                R.string.debug_status_unsupported
            TelemetryDiagnosticStatus.UNIMPLEMENTED ->
                R.string.debug_status_unimplemented
            TelemetryDiagnosticStatus.UNAVAILABLE ->
                R.string.debug_status_unavailable
            TelemetryDiagnosticStatus.UNKNOWN ->
                R.string.debug_status_unknown
            TelemetryDiagnosticStatus.FAILED ->
                R.string.debug_status_failed
        }
    )

    return detail?.let { detail ->
        stringResource(
            R.string.debug_status_detail,
            statusText,
            detail
        )
    } ?: statusText
}

private fun numberFormatter(maximumFractionDigits: Int = 1): NumberFormat {
    return NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 0
        this.maximumFractionDigits = maximumFractionDigits
    }
}
