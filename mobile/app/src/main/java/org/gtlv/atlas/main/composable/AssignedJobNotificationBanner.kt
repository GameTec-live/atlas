package org.gtlv.atlas.main.composable

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.notification.JOB_NOTIFICATION_DURATION_MILLIS
import org.gtlv.core.job.AssignedJobNotification

@Composable
internal fun AssignedJobNotificationBanner(
    notification: AssignedJobNotification,
    expiresAtElapsedRealtime: Long,
    isDeclining: Boolean,
    onDecline: () -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun remainingProgress(): Float {
        val remainingMillis =
            (
                    expiresAtElapsedRealtime -
                            SystemClock.elapsedRealtime()
                    ).coerceAtLeast(0L)

        return (
                remainingMillis.toFloat() /
                        JOB_NOTIFICATION_DURATION_MILLIS
                            .toFloat()
                ).coerceIn(0f, 1f)
    }

    val progress = remember(
        notification.jobId,
        expiresAtElapsedRealtime
    ) {
        Animatable(
            remainingProgress()
        )
    }

    LaunchedEffect(
        notification.jobId,
        expiresAtElapsedRealtime,
        isDeclining
    ) {
        if (isDeclining) {
            return@LaunchedEffect
        }

        val remainingMillis =
            (
                    expiresAtElapsedRealtime -
                            SystemClock.elapsedRealtime()
                    ).coerceAtLeast(0L)

        progress.snapTo(
            remainingProgress()
        )

        if (remainingMillis > 0L) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis =
                        remainingMillis
                            .coerceAtMost(
                                Int.MAX_VALUE
                                    .toLong()
                            )
                            .toInt(),
                    easing = LinearEasing
                )
            )
        }

        onExpired()
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color =
            MaterialTheme.colorScheme.surface,
        contentColor =
            MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(
                    start = 12.dp,
                    top = 10.dp,
                    end = 8.dp,
                    bottom = 6.dp
                ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(
                            R.string
                                .job_notification_title
                        ),
                        style =
                            MaterialTheme.typography
                                .titleSmall
                    )

                    Text(
                        text = stringResource(
                            R.string
                                .job_notification_from,
                            notification.from
                        ),
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )

                    Text(
                        text = stringResource(
                            R.string
                                .job_notification_to,
                            notification.to
                        ),
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                TextButton(
                    onClick = onDecline,
                    enabled = !isDeclining
                ) {
                    if (isDeclining) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string
                                    .job_notification_decline
                            )
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = {
                    progress.value
                },
                modifier =
                    Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun AssignedJobDeclineDialog(
    notification: AssignedJobNotification,
    isDeclining: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDeclining) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(
                    R.string
                        .job_notification_decline_dialog_title
                )
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string
                        .job_notification_decline_dialog_message,
                    notification.from,
                    notification.to
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeclining
            ) {
                if (isDeclining) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string
                                .job_notification_confirm_decline
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeclining
            ) {
                Text(
                    text = stringResource(
                        R.string.button_cancel
                    )
                )
            }
        }
    )
}