package org.gtlv.car_common.screen

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.gtlv.car_common.R
import org.gtlv.core.shift.ShiftRole

/** Shared role monitoring for the concrete driver and dispatcher screens. */
@Suppress("DEPRECATION") // Legacy title API keeps the template compatible with pre-Car-API-7 hosts.
abstract class RoleAwareScreen(
    carContext: CarContext,
    private val expectedRole: ShiftRole,
    private val getRole: () -> ShiftRole?,
    private val onRoleLost: () -> Unit,
    private val pollingIntervalMillis: Long = WaitingScreen.DEFAULT_POLLING_INTERVAL_MILLIS,
) : Screen(carContext), DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var isObserving = false

    private val rolePoll = object : Runnable {
        override fun run() {
            if (!isObserving) return

            val role = runCatching(getRole).getOrNull()

            if (role != expectedRole) {
                stopObserving()
                onRoleLost()
            } else {
                handler.postDelayed(this, pollingIntervalMillis)
            }
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        isObserving = true
        handler.removeCallbacks(rolePoll)
        handler.post(rolePoll)
    }

    override fun onStop(owner: LifecycleOwner) {
        stopObserving()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        stopObserving()
        lifecycle.removeObserver(this)
    }

    override fun onGetTemplate(): Template = MessageTemplate.Builder(
        carContext.getString(R.string.main_screen_message),
    )
        .setTitle(carContext.getString(R.string.main_screen_title))
        .build()

    private fun stopObserving() {
        isObserving = false
        handler.removeCallbacks(rolePoll)
    }
}
