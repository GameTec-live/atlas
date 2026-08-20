package org.gtlv.atlas.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import org.gtlv.atlas.MainActivity
import org.gtlv.atlas.R
import org.gtlv.core.job.AssignedJobNotification

class JobSystemNotificationManager(
    private val context: Context
) {
    private val activeNotificationIds =
        ConcurrentHashMap.newKeySet<Int>()

    fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }

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
        notification: AssignedJobNotification
    ) {
        if (!canPostNotifications()) {
            return
        }

        val notificationId =
            notificationId(notification.jobId)

        val openIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val openPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                pendingIntentFlags()
            )

        val declineIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            action = ACTION_CONFIRM_JOB_DECLINE

            data = Uri.parse(
                "atlas://job-notification/" +
                        Uri.encode(notification.jobId) +
                        "/decline"
            )

            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(
                EXTRA_JOB_ID,
                notification.jobId
            )

            putExtra(
                EXTRA_FROM,
                notification.from
            )

            putExtra(
                EXTRA_TO,
                notification.to
            )
        }

        val declinePendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                declineIntent,
                pendingIntentFlags()
            )

        val fromText = context.getString(
            R.string.job_notification_from,
            notification.from
        )

        val toText = context.getString(
            R.string.job_notification_to,
            notification.to
        )

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
                        R.string.job_notification_title
                    )
                )
                .setContentText(fromText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "$fromText\n$toText"
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
                .addAction(
                    0,
                    context.getString(
                        R.string.job_notification_decline
                    ),
                    declinePendingIntent
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

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
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

        private const val EXTRA_JOB_ID =
            "assigned_job_id"

        private const val EXTRA_FROM =
            "assigned_job_from"

        private const val EXTRA_TO =
            "assigned_job_to"

        private const val CHANNEL_ID =
            "assigned_jobs"

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

            if (
                jobId.isBlank() ||
                from.isBlank() ||
                to.isBlank()
            ) {
                return null
            }

            return AssignedJobNotification(
                jobId = jobId,
                from = from,
                to = to
            )
        }
    }
}