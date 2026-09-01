package org.gtlv.car_common.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import java.text.NumberFormat
import java.util.Currency
import org.gtlv.car_common.R
import org.gtlv.core.job.JobFareQuote

class FinishJobConfirmationScreen(
    carContext: CarContext,
    private val quote: JobFareQuote?,
    private val onConfirm: () -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return quote?.toPaneTemplate()
            ?: fallbackTemplate()
    }

    private fun fallbackTemplate(): Template {
        return MessageTemplate.Builder(
            carContext.getString(
                R.string.driver_finish_confirmation_title
            )
        )
            .addAction(noAction())
            .addAction(yesAction())
            .build()
    }

    private fun JobFareQuote.toPaneTemplate(): Template {
        val distanceFormatter = distanceFormatter()
        val currencyFormatter = currencyFormatter()
        val pane = Pane.Builder()
            .addRow(
                quoteRow(
                    label = carContext.getString(
                        R.string.driver_finish_total_label
                    ),
                    value = carContext.getString(
                        R.string.driver_finish_distance_and_price,
                        distanceFormatter.format(
                            totalDistanceKilometers
                        ),
                        currencyFormatter.format(totalPrice)
                    )
                )
            )
            .addRow(
                quoteRow(
                    label = carContext.getString(
                        R.string.driver_finish_passenger_label
                    ),
                    value = carContext.getString(
                        R.string.driver_finish_distance_and_price,
                        distanceFormatter.format(
                            passengerDistanceKilometers
                        ),
                        currencyFormatter.format(passengerPrice)
                    )
                )
            )
            .addRow(
                quoteRow(
                    label = carContext.getString(
                        R.string.driver_finish_rate_label
                    ),
                    value = carContext.getString(
                        R.string.driver_finish_rate_value,
                        currencyFormatter.format(pricePerKilometer)
                    )
                )
            )
            .addAction(noAction())
            .addAction(yesAction())
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(finishHeader())
            .build()
    }

    private fun quoteRow(
        label: String,
        value: String
    ): Row {
        return Row.Builder()
            .setTitle(label)
            .addText(value)
            .build()
    }

    private fun finishHeader(): Header {
        return Header.Builder()
            .setTitle(
                carContext.getString(
                    R.string.driver_finish_confirmation_title
                )
            )
            .build()
    }

    private fun noAction(): Action {
        return Action.Builder()
            .setTitle(
                carContext.getString(
                    R.string.driver_finish_no
                )
            )
            .setOnClickListener {
                carScreenManager.pop()
            }
            .build()
    }

    private fun yesAction(): Action {
        return Action.Builder()
            .setTitle(
                carContext.getString(
                    R.string.driver_finish_yes
                )
            )
            .setOnClickListener {
                carScreenManager.pop()
                onConfirm()
            }
            .build()
    }

    private fun distanceFormatter(): NumberFormat {
        return NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
    }

    private fun currencyFormatter(): NumberFormat {
        return NumberFormat.getCurrencyInstance().apply {
            currency = Currency.getInstance("EUR")
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    private val carScreenManager: ScreenManager
        get() = carContext.getCarService(
            ScreenManager::class.java
        )
}
