package org.gtlv.atlas.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.util.concurrent.ConcurrentHashMap
import org.gtlv.atlas.MainActivity
import org.gtlv.atlas.R
import org.gtlv.core.job.AssignedJobNotification
import org.gtlv.core.job.JobNotification
import org.gtlv.core.job.UnassignedJobNotification

class JobSystemNotificationManager(
    private val context: Context
) {
    private val activeNotificationIds =
        ConcurrentHashMap.newKeySet<Int>()

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(
                R.string.job_notification_channel
            ),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(
                R.string.job_notification_channel_description
            )
        }

        context
            .getSystemService(
                NotificationManager::class.java
            )
            .createNotificationChannel(channel)
    }

    fun show(
        notification: JobNotification
    ) {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationId =
            notificationId(notification.jobId)

        val actionIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            action = when (notification) {
                is AssignedJobNotification ->
                    ACTION_CONFIRM_JOB_DECLINE

                is UnassignedJobNotification ->
                    ACTION_ASSIGN_UNASSIGNED_JOB
            }

            data = (
                "atlas://job-notification/" +
                        Uri.encode(notification.jobId) +
                        when (notification) {
                            is AssignedJobNotification ->
                                "/decline"

                            is UnassignedJobNotification ->
                                "/assign"
                        }
                ).toUri()

            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(EXTRA_JOB_ID, notification.jobId)
            putExtra(EXTRA_FROM, notification.from)
            notification.to?.let { destination ->
                putExtra(EXTRA_TO, destination)
            }

            notification.note?.let { note ->
                putExtra(EXTRA_NOTE, note)
            }
        }

        val openIntent =
            if (
                notification
                    is UnassignedJobNotification
            ) {
                Intent(actionIntent)
            } else {
                Intent(
                    context,
                    MainActivity::class.java
                ).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            }

        val openPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                pendingIntentFlags()
            )

        val actionPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                actionIntent,
                pendingIntentFlags()
            )

        val titleResource = when (notification) {
            is AssignedJobNotification ->
                R.string.job_notification_title

            is UnassignedJobNotification ->
                R.string.unassigned_job_notification_title
        }

        val actionResource = when (notification) {
            is AssignedJobNotification ->
                R.string.job_notification_decline

            is UnassignedJobNotification ->
                R.string.unassigned_job_notification_assign
        }

        val fromText = context.getString(
            R.string.job_notification_from,
            notification.from
        )

        val toText = context.getString(
            R.string.job_notification_to,
            notification.to ?: context.getString(
                R.string.unassigned_jobs_no_destination
            )
        )

        val noteText = notification.note?.let { note ->
            context.getString(
                R.string.job_notification_note,
                note
            )
        }

        val detailsText = buildList {
            add(fromText)
            add(toText)
            noteText?.let(::add)
        }.joinToString("\n")

        val systemNotification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_job_notification
                )
                .setContentTitle(
                    context.getString(
                        titleResource
                    )
                )
                .setContentText(fromText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(
                            context.getString(
                                titleResource
                            )
                        )
                        .bigText(detailsText)
                )
                .setColor(
                    ContextCompat.getColor(
                        context,
                        R.color.job_notification_accent
                    )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_MESSAGE
                )
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(
                    NOTIFICATION_TIMEOUT_MILLIS
                )
                .setWhen(
                    System.currentTimeMillis() +
                            NOTIFICATION_TIMEOUT_MILLIS
                )
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(
                    0,
                    context.getString(
                        actionResource
                    ),
                    actionPendingIntent
                )
                .build()

        NotificationManagerCompat
            .from(context)
            .notify(
                notificationId,
                systemNotification
            )

        activeNotificationIds += notificationId
    }

    fun cancel(
        jobId: String
    ) {
        val notificationId =
            notificationId(jobId)

        NotificationManagerCompat
            .from(context)
            .cancel(notificationId)

        activeNotificationIds -= notificationId
    }

    fun cancelAllJobNotifications() {
        val manager =
            NotificationManagerCompat.from(context)

        activeNotificationIds.forEach {
            manager.cancel(it)
        }

        activeNotificationIds.clear()
    }

    private fun notificationId(
        jobId: String
    ): Int {
        return jobId.hashCode() and Int.MAX_VALUE
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
    }

    companion object {
        const val ACTION_CONFIRM_JOB_DECLINE =
            "org.gtlv.atlas.CONFIRM_JOB_DECLINE"

        const val ACTION_ASSIGN_UNASSIGNED_JOB =
            "org.gtlv.atlas.ASSIGN_UNASSIGNED_JOB"

        private const val EXTRA_JOB_ID =
            "assigned_job_id"

        private const val EXTRA_FROM =
            "assigned_job_from"

        private const val EXTRA_TO =
            "assigned_job_to"

        private const val EXTRA_NOTE =
            "assigned_job_note"

        private const val CHANNEL_ID =
            "assigned_jobs"

        private const val NOTIFICATION_TIMEOUT_MILLIS =
            10_000L

        fun notificationFromIntent(
            intent: Intent?
        ): AssignedJobNotification? {
            if (
                intent?.action !=
                ACTION_CONFIRM_JOB_DECLINE
            ) {
                return null
            }

            val jobId = intent.getStringExtra(
                EXTRA_JOB_ID
            )?.trim().orEmpty()

            val from = intent.getStringExtra(
                EXTRA_FROM
            )?.trim().orEmpty()

            val to = intent.getStringExtra(
                EXTRA_TO
            )?.trim().orEmpty()

            val note = intent.getStringExtra(
                EXTRA_NOTE
            )?.trim()?.takeIf(String::isNotEmpty)

            if (
                jobId.isBlank() ||
                from.isBlank()
            ) {
                return null
            }

            return AssignedJobNotification(
                jobId = jobId,
                from = from,
                to = to.takeIf(String::isNotEmpty),
                note = note
            )
        }

        fun assignmentJobIdFromIntent(
            intent: Intent?
        ): String? {
            if (
                intent?.action !=
                ACTION_ASSIGN_UNASSIGNED_JOB
            ) {
                return null
            }

            return intent.getStringExtra(
                EXTRA_JOB_ID
            )?.trim()?.takeIf(String::isNotEmpty)
        }
    }
}
