package org.gtlv.atlas.assign.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.core.job.JobCandidate

@Composable
internal fun CandidatePanel(
    candidates: List<JobCandidate>,
    isLoadingCandidates: Boolean,
    candidatesFailed: Boolean,
    allDrivers: List<JobCandidate>,
    isLoadingDrivers: Boolean,
    driversFailed: Boolean,
    isActionInProgress: Boolean,
    candidateButtonsEnabled: Boolean = true,
    recommendationsEnabled: Boolean = true,
    showCreateUnassigned: Boolean = false,
    canCreateUnassigned: Boolean = false,
    isCreatingUnassigned: Boolean = false,
    onCreateUnassigned: () -> Unit = {},
    onRetry: () -> Unit,
    onCandidateClick: (JobCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val otherDrivers = remember(
        candidates,
        allDrivers
    ) {
        val recommendedIds = candidates
            .mapTo(mutableSetOf()) { candidate ->
                candidate.driverId
            }

        allDrivers.filterNot { driver ->
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
                if (showCreateUnassigned) {
                    item(key = "create-unassigned") {
                        FilledTonalButton(
                            onClick = onCreateUnassigned,
                            enabled = canCreateUnassigned &&
                                    !isActionInProgress,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isCreatingUnassigned) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(2.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(
                                        R.string.new_job_create_unassigned
                                    )
                                )
                            }
                        }
                    }
                }

                when {
                    !recommendationsEnabled -> {
                        item(key = "recommended-disabled") {
                            Text(
                                text = stringResource(
                                    R.string.new_job_pickup_required
                                ),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    isLoadingCandidates && candidates.isEmpty() -> {
                        item(key = "recommended-loading") {
                            LoadingDriversIndicator()
                        }
                    }

                    candidatesFailed -> {
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

                    candidates.isEmpty() -> {
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
                            items = candidates,
                            key = { candidate ->
                                "recommended:${candidate.driverId}"
                            }
                        ) { candidate ->
                            DriverButton(
                                candidate = candidate,
                                recommended = true,
                                enabled = candidateButtonsEnabled &&
                                        !isActionInProgress,
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
                    isLoadingDrivers && allDrivers.isEmpty() -> {
                        item(key = "drivers-loading") {
                            LoadingDriversIndicator()
                        }
                    }

                    driversFailed -> {
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
                                enabled = candidateButtonsEnabled &&
                                        !isActionInProgress,
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
