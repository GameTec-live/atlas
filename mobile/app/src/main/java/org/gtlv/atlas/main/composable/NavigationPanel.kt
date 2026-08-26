package org.gtlv.atlas.main.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.roundToInt
import org.gtlv.atlas.R
import org.gtlv.atlas.main.NavigationError
import org.gtlv.atlas.main.NavigationPhase
import org.gtlv.atlas.main.NavigationStatus
import org.gtlv.atlas.main.NavigationUiState
import org.gtlv.core.geoservice.RouteManeuver

@Composable
internal fun NavigationPanel(
    state: NavigationUiState,
    modifier: Modifier = Modifier
) {
    if (
        state.phase == NavigationPhase.None ||
        state.status == NavigationStatus.Idle
    ) {
        return
    }

    var expanded by rememberSaveable(
        state.jobId,
        state.phase
    ) {
        mutableStateOf(false)
    }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    when (state.phase) {
                        NavigationPhase.ToPickup ->
                            R.string.navigation_to_pickup
                        NavigationPhase.ToDestination ->
                            R.string.navigation_to_destination
                        NavigationPhase.None ->
                            R.string.navigation_route
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            val route = state.route
            if (route == null) {
                NavigationStatusContent(state)
                return@Column
            }

            val currentIndex = state.progress?.let { progress ->
                progress.currentManeuverIndex
                    ?: progress.nextManeuverIndex
            } ?: route.maneuvers.indices.firstOrNull()
            val nextIndex = state.progress
                ?.nextManeuverIndex
            val displayedIndex = nextIndex ?: currentIndex
            val displayedManeuver = displayedIndex?.let {
                route.maneuvers.getOrNull(it)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.rotate(
                        maneuverRotation(displayedManeuver?.type)
                    ),
                    tint = MaterialTheme.colorScheme.primary
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayedManeuver?.instruction
                            ?: stringResource(
                                R.string.navigation_no_maneuvers
                            ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.progress
                        ?.remainingDistanceToManeuverKilometers
                        ?.let { distance ->
                            Text(
                                text = stringResource(
                                    R.string.navigation_in_distance,
                                    formatDistance(distance)
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                }
            }

            val remainingTime = state.progress
                ?.remainingRouteTimeSeconds
                ?: route.summary.timeSeconds
                ?: 0.0
            val remainingDistance = state.progress
                ?.remainingRouteDistanceKilometers
                ?: route.summary.lengthKilometers
                ?: 0.0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = route.maneuvers.isNotEmpty()
                    ) {
                        expanded = !expanded
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatDuration(remainingTime),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        R.string.navigation_summary_separator
                    )
                )
                Text(
                    text = formatDistance(remainingDistance),
                    style = MaterialTheme.typography.titleMedium
                )

                if (route.maneuvers.isNotEmpty()) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (expanded) {
                                R.string.navigation_collapse_steps
                            } else {
                                R.string.navigation_expand_steps
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (expanded && route.maneuvers.isNotEmpty()) {
                HorizontalDivider()
                val firstRemainingIndex =
                    nextIndex ?: currentIndex ?: 0
                val remainingManeuvers = route.maneuvers
                    .drop(firstRemainingIndex)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(remainingManeuvers) { index, maneuver ->
                        ManeuverRow(
                            maneuver = maneuver,
                            remainingSegmentDistanceKilometers =
                                if (
                                    index == 0 &&
                                    nextIndex == null
                                ) {
                                    state.progress
                                        ?.remainingDistanceInCurrentManeuverKilometers
                                } else {
                                    null
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationStatusContent(state: NavigationUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.status == NavigationStatus.Loading) {
            CircularProgressIndicator()
        }
        Text(
            text = stringResource(
                when (state.status) {
                    NavigationStatus.Loading ->
                        R.string.navigation_loading
                    NavigationStatus.WaitingForLocation ->
                        R.string.navigation_waiting_location
                    NavigationStatus.PickupUnavailable ->
                        R.string.navigation_pickup_unavailable
                    NavigationStatus.WaitingForDestination ->
                        R.string.navigation_waiting_destination
                    NavigationStatus.Error -> when (state.error) {
                        NavigationError.Unauthorized ->
                            R.string.navigation_error_unauthorized
                        NavigationError.Network ->
                            R.string.navigation_error_network
                        NavigationError.Router ->
                            R.string.navigation_error_router
                        NavigationError.Server ->
                            R.string.navigation_error_server
                        NavigationError.InvalidResponse,
                        null -> R.string.navigation_error_invalid_response
                    }
                    NavigationStatus.Idle,
                    NavigationStatus.Ready ->
                        R.string.navigation_route
                }
            )
        )
    }
}

@Composable
private fun ManeuverRow(
    maneuver: RouteManeuver,
    remainingSegmentDistanceKilometers: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = null,
            modifier = Modifier.rotate(
                maneuverRotation(maneuver.type)
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = maneuver.instruction,
                style = MaterialTheme.typography.bodyLarge
            )
            val distance = remainingSegmentDistanceKilometers
                ?: maneuver.lengthKilometers
            distance?.let {
                Text(
                    text = stringResource(
                        R.string.navigation_continue_for,
                        formatDistance(it)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun formatDistance(kilometers: Double): String {
    return if (kilometers < 1.0) {
        stringResource(
            R.string.navigation_distance_meters,
            (kilometers * 1000.0).roundToInt().coerceAtLeast(0)
        )
    } else {
        val formatter = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }
        stringResource(
            R.string.navigation_distance_kilometers,
            formatter.format(kilometers)
        )
    }
}

@Composable
private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60.0)
        .roundToInt()
        .coerceAtLeast(0)
    return if (minutes < 60) {
        stringResource(
            R.string.navigation_duration_minutes,
            minutes
        )
    } else {
        stringResource(
            R.string.navigation_duration_hours_minutes,
            minutes / 60,
            minutes % 60
        )
    }
}

private fun maneuverRotation(type: Int?): Float = when (type) {
    9, 10, 11, 18, 20, 23 -> 90f
    14, 15, 16, 19, 21, 24 -> -90f
    12, 13 -> 180f
    else -> 0f
}
