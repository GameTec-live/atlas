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

/**
 * Fail-closed entry screen shown until the connected phone user has a role.
 *
 * Login and role selection are deliberately performed by the phone application. This screen only
 * observes whether core has loaded a role for the current user and advances once one is available.
 */
@Suppress("DEPRECATION") // Legacy title API keeps the template compatible with pre-Car-API-7 hosts.
class WaitingScreen(
    carContext: CarContext,
    private val hasRole: () -> Boolean?,
    private val onRoleAvailable: () -> Unit,
    private val pollingIntervalMillis: Long = DEFAULT_POLLING_INTERVAL_MILLIS,
) : Screen(carContext), DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private var isObserving = false
    private var hasNavigated = false

    private val rolePoll = object : Runnable {
        override fun run() {
            if (!isObserving) return

            val roleAvailable = runCatching(hasRole).getOrNull()

            if (roleAvailable == true) {
                navigateToRoleContent()
            } else {
                handler.postDelayed(this, pollingIntervalMillis)
            }
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        hasNavigated = false
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
        carContext.getString(R.string.waiting_screen_explanation),
    )
        .setTitle(carContext.getString(R.string.waiting_screen_title))
        .build()

    private fun navigateToRoleContent() {
        if (hasNavigated) return
        hasNavigated = true
        stopObserving()
        onRoleAvailable()
    }

    private fun stopObserving() {
        isObserving = false
        handler.removeCallbacks(rolePoll)
    }

    companion object {
        const val DEFAULT_POLLING_INTERVAL_MILLIS = 1_000L
    }
}
