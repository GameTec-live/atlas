package org.gtlv.core.role

import org.gtlv.core.shift.ShiftRole

data class AssignedRole(
    val driverId: String,
    val role: ShiftRole,
    val name: String?
)

data class RoleAvailability(
    val dispatcherSpotsFree: Int,
    val dispatcherAvailable: Boolean,
    val assignedRoles: List<AssignedRole> = emptyList()
)