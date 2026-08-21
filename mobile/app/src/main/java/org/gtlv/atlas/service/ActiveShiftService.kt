package org.gtlv.atlas.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.gtlv.atlas.AtlasApplication
import org.gtlv.atlas.MainActivity
import org.gtlv.atlas.R

class ActiveShiftService : Service() {

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo
                .FOREGROUND_SERVICE_TYPE_LOCATION
        )

        atlasApplication.locationProvider.start()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    override fun onDestroy() {
        atlasApplication.locationProvider.stop()

        super.onDestroy()
    }

    private val atlasApplication: AtlasApplication
        get() = application as AtlasApplication

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(
                    R.string
                        .active_shift_channel
                ),
                NotificationManager
                    .IMPORTANCE_LOW
            ).apply {
                description = getString(
                    R.string
                        .active_shift_channel_description
                )

                setShowBadge(false)
            }

        getSystemService(
            NotificationManager::class.java
        ).createNotificationChannel(channel)
    }

    private fun createNotification() =
        NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                R.drawable.ic_job_notification
            )
            .setContentTitle(
                getString(
                    R.string
                        .active_shift_notification_title
                )
            )
            .setContentText(
                getString(
                    R.string
                        .active_shift_notification_text
                )
            )
            .setContentIntent(
                createContentIntent()
            )
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(
                NotificationCompat
                    .FOREGROUND_SERVICE_IMMEDIATE
            )
            .build()

    private fun createContentIntent(): PendingIntent {
        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        return PendingIntent.getActivity(
            this,
            CONTENT_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL_ID =
            "active_shift"

        private const val NOTIFICATION_ID =
            2001

        private const val CONTENT_INTENT_REQUEST_CODE =
            2002

        fun start(context: Context) {
            val intent =
                Intent(
                    context,
                    ActiveShiftService::class.java
                )

            ContextCompat.startForegroundService(
                context,
                intent
            )
        }

        fun stop(context: Context) {
            val intent =
                Intent(
                    context,
                    ActiveShiftService::class.java
                )

            context.stopService(intent)
        }
    }
}
