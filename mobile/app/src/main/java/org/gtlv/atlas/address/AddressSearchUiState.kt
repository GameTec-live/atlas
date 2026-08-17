package org.gtlv.atlas.address

import org.gtlv.core.geoservice.AddressSuggestion

data class AddressSearchUiState(
    val query: String = "",
    val suggestions: List<AddressSuggestion> =
        emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false
)