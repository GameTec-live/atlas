package org.gtlv.core.role

import org.gtlv.core.shift.ShiftRole

interface RoleRepository {

    suspend fun getAvailability(): RoleAvailabilityResult

    suspend fun selectRole(
        role: ShiftRole
    ): SelectRoleResult
}