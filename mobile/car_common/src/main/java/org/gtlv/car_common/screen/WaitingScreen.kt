package org.gtlv.car_common.screen

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.gtlv.car_common.R
import org.gtlv.core.settings.ServerSettingsRepository
import org.gtlv.core.shift.ShiftRole

/**
 * Fail-closed entry screen shown until the connected phone user has a role.
 *
 * Login and role selection are deliberately performed by the phone application. This screen only
 * observes whether core has loaded a role for the current user and advances once one is available.
 */
class WaitingScreen(
    carContext: CarContext,
    private val getRole: () -> ShiftRole?,
    private val onRoleAvailable: (ShiftRole) -> Unit,
    private val serverSettingsRepository: ServerSettingsRepository?,
    private val pollingIntervalMillis: Long = DEFAULT_POLLING_INTERVAL_MILLIS,
) : Screen(carContext), DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private var isObserving = false
    private var hasNavigated = false

    private val rolePoll = object : Runnable {
        override fun run() {
            if (!isObserving) return

            val role = runCatching(getRole).getOrNull()

            if (role != null) {
                navigateToRoleContent(role)
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
        .setHeader(
            Header.Builder()
                .setTitle(carContext.getString(R.string.main_screen_title))
                .build(),
        )
        .build()

    private fun navigateToRoleContent(role: ShiftRole) {
        if (hasNavigated) return
        hasNavigated = true
        stopObserving()
        onRoleAvailable(role)
    }

    private fun stopObserving() {
        isObserving = false
        handler.removeCallbacks(rolePoll)
    }

    companion object {
        const val DEFAULT_POLLING_INTERVAL_MILLIS = 1_000L
    }
}
