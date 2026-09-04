package org.gtlv.atlas.offboarding.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.gtlv.atlas.R
import org.gtlv.atlas.offboarding.OffboardingUiState
import org.gtlv.core.fleet.Vehicle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OffboardingContent(
    state: OffboardingUiState,
    onRevenueChanged: (String) -> Unit,
    onConfirmationChanged: (Boolean) -> Unit,
    onVehicleSelected: (Vehicle) -> Unit,
    onRetryVehicles: () -> Unit,
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
    val unavailable = stringResource(R.string.offboarding_unavailable)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.offboarding_title)) },
                navigationIcon = {
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
                }
            )
        },
        bottomBar = {
            OffboardingBottomBar(
                state = state,
                onConfirmationChanged = onConfirmationChanged,
                onSubmit = onSubmit
            )
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 4.dp,
                    end = 20.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SectionLabel(R.string.offboarding_mileage_section)
                    Spacer(Modifier.height(8.dp))
                    MileageCard(
                        startKilometer = session.startKilometer
                            ?.let(numberFormatter::format)
                            ?: unavailable,
                        endKilometer = numberFormatter.format(endKilometer),
                        drivenKilometers = driven
                            ?.let(numberFormatter::format)
                            ?: unavailable
                    )
                }

                item {
                    SectionLabel(R.string.offboarding_vehicle_section)
                    Spacer(Modifier.height(8.dp))
                    VehicleCard(
                        vehicle = state.vehicle,
                        availableVehicles = state.availableVehicles,
                        isLoading = state.isLoadingVehicles,
                        loadFailed = state.vehicleLoadFailed,
                        enabled = !state.isSubmitting,
                        onVehicleSelected = onVehicleSelected,
                        onRetry = onRetryVehicles
                    )
                }

                item {
                    SectionLabel(R.string.offboarding_time_section)
                    Spacer(Modifier.height(8.dp))
                    TimeCard(
                        startTime = formatTime(session.startTimeUtc),
                        endTime = formatTime(endTime),
                        duration = formatDuration(session.startTimeUtc, endTime)
                    )
                }

                item {
                    SectionLabel(R.string.offboarding_cash_report)
                    Spacer(Modifier.height(8.dp))
                    RevenueCard(
                        value = state.revenueInput,
                        isInvalid = state.isRevenueInvalid,
                        enabled = !state.isSubmitting,
                        onValueChanged = onRevenueChanged
                    )
                }
            }
        }
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
