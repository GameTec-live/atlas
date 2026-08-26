package org.gtlv.atlas.address

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.text.BasicTextField
import org.gtlv.atlas.R
import org.gtlv.core.geoservice.AddressSuggestion

@Composable
internal fun AddressSearchField(
    state: AddressSearchUiState,
    label: String,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected:
        (AddressSuggestion) -> Unit,
    onClose: () -> Unit,
    presentation: AddressSearchPresentation =
        AddressSearchPresentation.CARD,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember {
        FocusRequester()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (presentation == AddressSearchPresentation.INLINE) {
        InlineAddressSearchField(
            state = state,
            label = label,
            onQueryChanged = onQueryChanged,
            onSuggestionSelected = onSuggestionSelected,
            onClose = onClose,
            focusRequester = focusRequester,
            modifier = modifier
        )
        return
    }

    Surface(
        modifier = modifier.widthIn(
            max = 600.dp
        ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor =
            MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp
    ) {
        Column {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(8.dp),
                enabled = !state.isSaving,
                singleLine = true,
                label = {
                    Text(text = label)
                },
                placeholder = {
                    Text(
                        text = stringResource(
                            R.string.address_search_hint
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector =
                            Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        if (
                            state.isLoading ||
                            state.isSaving
                        ) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }

                        IconButton(
                            onClick = onClose,
                            enabled = !state.isSaving
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.Close,
                                contentDescription =
                                    stringResource(
                                        R.string
                                            .address_search_close
                                    )
                            )
                        }
                    }
                }
            )

            when {
                state.hasError -> {
                    SearchMessage(
                        text = stringResource(
                            R.string
                                .address_search_load_error
                        ),
                        isError = true
                    )
                }

                state.saveFailed -> {
                    SearchMessage(
                        text = stringResource(
                            R.string
                                .address_search_save_error
                        ),
                        isError = true
                    )
                }

                state.hasSearched &&
                        state.query.isNotBlank() &&
                        !state.isLoading &&
                        state.suggestions.isEmpty() -> {
                    SearchMessage(
                        text = stringResource(
                            R.string
                                .address_search_no_results
                        ),
                        isError = false
                    )
                }

                state.suggestions.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                max = 280.dp
                            )
                    ) {
                        itemsIndexed(
                            items = state.suggestions,
                            key = { index, suggestion ->
                                "${suggestion.id}:$index"
                            }
                        ) { _, suggestion ->
                            SuggestionRow(
                                suggestion = suggestion,
                                enabled =
                                    !state.isSaving,
                                onClick = {
                                    onSuggestionSelected(
                                        suggestion
                                    )
                                }
                            )

                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: AddressSuggestion,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = suggestion.displayName,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SearchMessage(
    text: String,
    isError: Boolean
) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            bottom = 12.dp
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

internal enum class AddressSearchPresentation {
    CARD,
    INLINE
}

@Composable
private fun InlineAddressSearchField(
    state: AddressSearchUiState,
    label: String,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (AddressSuggestion) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val showSuggestions =
        state.isLoading ||
                state.hasError ||
                state.saveFailed ||
                state.suggestions.isNotEmpty() ||
                (
                    state.hasSearched &&
                        state.query.isNotBlank()
                    )

    Box(modifier = modifier) {
        BasicTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            enabled = !state.isSaving,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
                .merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme
                            .onSurface
                    )
                ),
            cursorBrush = SolidColor(
                MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .focusRequester(focusRequester)
                .padding(
                    start = 4.dp,
                    end = 28.dp,
                    top = 14.dp,
                    bottom = 12.dp
                ),
            decorationBox = { innerTextField ->
                Box {
                    if (state.query.isBlank()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography
                                .bodyMedium,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }

                    innerTextField()
                }
            }
        )

        if (state.isLoading || state.isSaving) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(18.dp),
                strokeWidth = 2.dp
            )
        }

        DropdownMenu(
            expanded = showSuggestions,
            onDismissRequest = onClose,
            modifier = Modifier
                .widthIn(
                    min = 280.dp,
                    max = 480.dp
                )
                .heightIn(max = 220.dp),
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            when {
                state.isLoading -> {
                    DropdownMessage(
                        text = stringResource(
                            R.string.address_search_loading
                        ),
                        isError = false
                    )
                }

                state.hasError -> {
                    DropdownMessage(
                        text = stringResource(
                            R.string.address_search_load_error
                        ),
                        isError = true
                    )
                }

                state.saveFailed -> {
                    DropdownMessage(
                        text = stringResource(
                            R.string.address_search_save_error
                        ),
                        isError = true
                    )
                }

                state.suggestions.isEmpty() -> {
                    DropdownMessage(
                        text = stringResource(
                            R.string.address_search_no_results
                        ),
                        isError = false
                    )
                }

                else -> {
                    state.suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = suggestion.displayName,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                onSuggestionSelected(suggestion)
                            },
                            enabled = !state.isSaving
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownMessage(
    text: String,
    isError: Boolean
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        onClick = {},
        enabled = false
    )
}
