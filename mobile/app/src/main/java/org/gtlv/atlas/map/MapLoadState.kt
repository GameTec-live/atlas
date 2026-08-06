package org.gtlv.atlas.map

internal sealed interface MapLoadState {

    data object Loading : MapLoadState

    data object Loaded : MapLoadState

    data class Error(
        val message: String
    ) : MapLoadState
}