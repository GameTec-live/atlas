package org.gtlv.atlas.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {

    data class Resource(
        @StringRes val resourceId: Int,
        val arguments: List<Any> = emptyList()
    ) : UiText

    data class Dynamic(
        val value: String
    ) : UiText
}

@Composable
fun UiText.asString(): String {
    return when (this) {
        is UiText.Resource -> stringResource(
            id = resourceId,
            *arguments.toTypedArray()
        )

        is UiText.Dynamic -> value
    }
}