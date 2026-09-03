package org.gtlv.atlas.offboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.StateFlow
import org.gtlv.core.fleet.ConnectedVehicleState
import org.gtlv.core.logbook.LogbookRepository
import org.gtlv.core.session.SessionManager
import org.gtlv.core.shift.ShiftSessionManager
import org.gtlv.core.telemetry.TelemetryProvider

class OffboardingViewModelFactory(
    private val shiftSessionManager: ShiftSessionManager,
    private val telemetryProvider: TelemetryProvider,
    private val connectedVehicleState: StateFlow<ConnectedVehicleState>,
    private val logbookRepository: LogbookRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(OffboardingViewModel::class.java))
        return OffboardingViewModel(
            shiftSessionManager = shiftSessionManager,
            telemetryProvider = telemetryProvider,
            connectedVehicleState = connectedVehicleState,
            logbookRepository = logbookRepository,
            logout = sessionManager::logout
        ) as T
    }
}
