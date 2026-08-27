package org.gtlv.atlas.assign.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.assign.AssignJobUiState
import org.gtlv.core.job.JobCandidate

@Composable
internal fun CandidatePanel(
    state: AssignJobUiState,
    onRetry: () -> Unit,
    onCandidateClick: (JobCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val otherDrivers = remember(
        state.candidates,
        state.allDrivers
    ) {
        val recommendedIds = state.candidates
            .mapTo(mutableSetOf()) { candidate ->
                candidate.driverId
            }

        state.allDrivers.filterNot { driver ->
            driver.driverId in recommendedIds
        }
    }

    Column(
        modifier = modifier.padding(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(
                R.string.assign_job_candidates
            ),
            style = MaterialTheme.typography.titleMedium
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                when {
                    state.isLoadingCandidates &&
                            state.candidates.isEmpty() -> {
                        item(key = "recommended-loading") {
                            LoadingDriversIndicator()
                        }
                    }

                    state.candidatesFailed -> {
                        item(key = "recommended-error") {
                            DriversError(
                                message = stringResource(
                                    R.string
                                        .assign_job_candidates_error
                                ),
                                onRetry = onRetry
                            )
                        }
                    }

                    state.candidates.isEmpty() -> {
                        item(key = "recommended-empty") {
                            Text(
                                text = stringResource(
                                    R.string
                                        .assign_job_no_candidates
                                ),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    else -> {
                        items(
                            items = state.candidates,
                            key = { candidate ->
                                "recommended:${candidate.driverId}"
                            }
                        ) { candidate ->
                            DriverButton(
                                candidate = candidate,
                                recommended = true,
                                enabled = !state.isAssigning,
                                onClick = {
                                    onCandidateClick(candidate)
                                }
                            )
                        }
                    }
                }

                item(key = "drivers-divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                item(key = "drivers-heading") {
                    Text(
                        text = stringResource(
                            R.string.assign_job_all_drivers
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                when {
                    state.isLoadingDrivers &&
                            state.allDrivers.isEmpty() -> {
                        item(key = "drivers-loading") {
                            LoadingDriversIndicator()
                        }
                    }

                    state.driversFailed -> {
                        item(key = "drivers-error") {
                            DriversError(
                                message = stringResource(
                                    R.string
                                        .assign_job_drivers_error
                                ),
                                onRetry = onRetry
                            )
                        }
                    }

                    otherDrivers.isEmpty() -> {
                        item(key = "drivers-empty") {
                            Text(
                                text = stringResource(
                                    R.string
                                        .assign_job_no_other_drivers
                                ),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    else -> {
                        items(
                            items = otherDrivers,
                            key = { driver ->
                                "driver:${driver.driverId}"
                            }
                        ) { driver ->
                            DriverButton(
                                candidate = driver,
                                recommended = false,
                                enabled = !state.isAssigning,
                                onClick = {
                                    onCandidateClick(driver)
                                }
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun DriverButton(
    candidate: JobCandidate,
    recommended: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (recommended) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            DriverButtonContent(
                candidate = candidate,
                showRank = true
            )
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            DriverButtonContent(
                candidate = candidate,
                showRank = false
            )
        }
    }
}

@Composable
private fun DriverButtonContent(
    candidate: JobCandidate,
    showRank: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = candidate.driverName,
            style = MaterialTheme.typography.titleMedium
        )

        if (showRank) {
            Text(
                text = stringResource(
                    R.string.assign_job_recommended_rank,
                    candidate.rank
                ),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun LoadingDriversIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DriversError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = message)

        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(
                    R.string.unassigned_jobs_retry
                )
            )
        }
    }
}
