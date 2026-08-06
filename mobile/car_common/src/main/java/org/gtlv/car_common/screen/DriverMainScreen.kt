package org.gtlv.car_common.screen

import androidx.car.app.CarContext
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import org.gtlv.core.shift.ShiftRole

class DriverMainScreen(
    carContext: CarContext,
    getRole: () -> ShiftRole?,
    onRoleLost: () -> Unit,
) : RoleAwareScreen(carContext, ShiftRole.DRIVER, getRole, onRoleLost) {
    override fun onGetTemplate(): Template = MessageTemplate.Builder("You are a driver").build()
}
