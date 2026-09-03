package org.gtlv.atlas.offboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.gtlv.atlas.R

@Composable
internal fun OffboardingScreen(
    state: OffboardingUiState,
    onRevenueChanged: (String) -> Unit,
    onConfirmationChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(
        enabled = !state.isSubmitting,
        onBack = onBack
    )
    val session = state.session ?: return
    val endKilometer = session.endKilometer ?: return
    val endTime = session.endTimeUtc ?: return
    val numberFormatter = remember {
        NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }
    }
    val driven = session.startKilometer?.let { start ->
        (endKilometer - start).coerceAtLeast(0.0)
    }
    val vehicleName = state.vehicle?.displayName
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.offboarding_unavailable)
    val licensePlate = state.vehicle?.licensePlate
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.offboarding_unavailable)

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            IconButton(
                onClick = onBack,
                enabled = !state.isSubmitting
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(
                        R.string.offboarding_back
                    )
                )
            }

            Text(
                text = stringResource(R.string.offboarding_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryRow(
                        stringResource(R.string.offboarding_km_start),
                        session.startKilometer?.let(numberFormatter::format)
                            ?: stringResource(R.string.offboarding_unavailable)
                    )
                    SummaryRow(
                        stringResource(R.string.offboarding_km_end),
                        numberFormatter.format(endKilometer)
                    )
                    SummaryRow(
                        stringResource(R.string.offboarding_km_driven),
                        driven?.let(numberFormatter::format)
                            ?: stringResource(R.string.offboarding_unavailable)
                    )

                    Spacer(Modifier.height(8.dp))
                    SummaryRow(
                        stringResource(R.string.offboarding_vehicle),
                        vehicleName
                    )
                    SummaryRow(
                        stringResource(R.string.offboarding_license_plate),
                        licensePlate
                    )

                    Spacer(Modifier.height(8.dp))
                    SummaryRow(
                        stringResource(R.string.offboarding_start_time),
                        formatTime(session.startTimeUtc)
                    )
                    SummaryRow(
                        stringResource(R.string.offboarding_end_time),
                        formatTime(endTime)
                    )

                    Spacer(Modifier.height(8.dp))
                    SummaryRow(
                        stringResource(R.string.offboarding_total_time),
                        formatDuration(session.startTimeUtc, endTime)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.offboarding_cash_report),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.revenueInput,
                        onValueChange = onRevenueChanged,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.offboarding_revenue))
                        },
                        singleLine = true,
                        isError = state.isRevenueInvalid,
                        supportingText = if (state.isRevenueInvalid) {
                            {
                                Text(stringResource(R.string.offboarding_revenue_invalid))
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isSubmitting) {
                        onConfirmationChanged(!state.isConfirmed)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.isConfirmed,
                    onCheckedChange = onConfirmationChanged,
                    enabled = !state.isSubmitting
                )
                Text(stringResource(R.string.offboarding_confirmation))
            }

            state.error?.let { error ->
                Text(
                    text = stringResource(error.messageResource()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Button(
                onClick = onSubmit,
                enabled = state.isConfirmed && !state.isSubmitting,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 20.dp)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.offboarding_log_off))
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatTime(instant: Instant): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(instant)

internal fun formatDuration(start: Instant, end: Instant): String {
    val minutes = Duration.between(start, end).toMinutes().coerceAtLeast(0)
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (remainingMinutes == 0L) {
        "${hours}h"
    } else {
        "${hours}h ${remainingMinutes}m"
    }
}

private fun OffboardingError.messageResource(): Int = when (this) {
    OffboardingError.VEHICLE_UNAVAILABLE ->
        R.string.offboarding_vehicle_unavailable
    OffboardingError.START_KILOMETER_UNAVAILABLE ->
        R.string.offboarding_start_kilometer_unavailable
    OffboardingError.SUBMISSION_FAILED ->
        R.string.offboarding_submit_failed
}
