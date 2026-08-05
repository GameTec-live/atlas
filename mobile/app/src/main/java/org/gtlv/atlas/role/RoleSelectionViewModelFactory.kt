package org.gtltlv.atlas.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.atlas.role.RoleSelectionViewModel
import org.gtlv.core.role.RoleRepository
import org.gtlv.core.session.SessionManager
import org.gtlv.core.shift.ShiftSessionManager

class RoleSelectionViewModelFactory(
    private val roleRepository: RoleRepository,
    private val shiftSessionManager: ShiftSessionManager,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                RoleSelectionViewModel::class.java
            )
        ) {
            return requireNotNull(
                modelClass.cast(
                    RoleSelectionViewModel(
                        roleRepository = roleRepository,
                        shiftSessionManager = shiftSessionManager,
                        sessionManager = sessionManager
                    )
                )
            )
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}