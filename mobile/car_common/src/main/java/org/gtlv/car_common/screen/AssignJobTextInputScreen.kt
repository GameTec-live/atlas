package org.gtlv.car_common.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

/** Host-managed keyboard used by the editable rows on the assign-job screen. */
internal class AssignJobTextInputScreen(
    carContext: CarContext,
    private val title: String,
    initialValue: String,
    private val onValueChanged: (String) -> Unit,
) : Screen(carContext) {
    private var value = initialValue

    override fun onGetTemplate(): Template = SearchTemplate.Builder(
        object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                updateValue(searchText)
            }

            override fun onSearchSubmitted(searchText: String) {
                updateValue(searchText)
                carContext
                    .getCarService(ScreenManager::class.java)
                    .pop()
            }
        },
    )
        .setInitialSearchText(value)
        .setSearchHint(title)
        .setShowKeyboardByDefault(true)
        .setHeaderAction(Action.BACK)
        .build()

    private fun updateValue(updatedValue: String) {
        if (value == updatedValue) return
        value = updatedValue
        onValueChanged(updatedValue)
        invalidate()
    }
}
