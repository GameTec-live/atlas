package org.gtlv.atlas.notification

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

class AppVisibilityTracker :
    Application.ActivityLifecycleCallbacks {

    private val startedActivities =
        AtomicInteger(0)

    @Volatile
    var isForeground: Boolean = false
        private set

    override fun onActivityStarted(
        activity: Activity
    ) {
        startedActivities.incrementAndGet()
        isForeground = true
    }

    override fun onActivityStopped(
        activity: Activity
    ) {
        val remaining =
            startedActivities
                .decrementAndGet()
                .coerceAtLeast(0)

        if (
            remaining == 0 &&
            !activity.isChangingConfigurations
        ) {
            isForeground = false
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
    ) = Unit

    override fun onActivityResumed(
        activity: Activity
    ) = Unit

    override fun onActivityPaused(
        activity: Activity
    ) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle
    ) = Unit

    override fun onActivityDestroyed(
        activity: Activity
    ) = Unit
}