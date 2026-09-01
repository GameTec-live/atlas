package org.gtlv.atlas.main.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Currency
import org.gtlv.atlas.R
import org.gtlv.atlas.main.FinishJobConfirmation

@Composable
internal fun FinishJobConfirmationDialog(
    confirmation: FinishJobConfirmation,
    isFinishing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val distanceFormatter = remember {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
    }
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance().apply {
            currency = Currency.getInstance("EUR")
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    val quote = confirmation.quote

    AlertDialog(
        onDismissRequest = {
            if (!isFinishing) onDismiss()
        },
        title = {
            Text(
                text = stringResource(
                    R.string.job_finish_confirmation_title
                )
            )
        },
        text = quote?.let {
            {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.job_finish_total,
                            distanceFormatter.format(
                                quote.totalDistanceKilometers
                            ),
                            currencyFormatter.format(
                                quote.totalPrice
                            )
                        )
                    )
                    Text(
                        text = stringResource(
                            R.string.job_finish_passenger,
                            distanceFormatter.format(
                                quote.passengerDistanceKilometers
                            ),
                            currencyFormatter.format(
                                quote.passengerPrice
                            )
                        )
                    )
                    Text(
                        text = stringResource(
                            R.string.job_finish_rate,
                            currencyFormatter.format(
                                quote.pricePerKilometer
                            )
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isFinishing
            ) {
                if (isFinishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.job_finish_yes
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isFinishing
            ) {
                Text(
                    text = stringResource(
                        R.string.job_finish_no
                    )
                )
            }
        }
    )
}
