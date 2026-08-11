package org.gtlv.core.location

sealed interface LocationState {

    data object Stopped : LocationState

    data object WaitingForLocation : LocationState

    data object PermissionDenied : LocationState

    data object Unavailable : LocationState

    data class Available(
        val location: AtlasLocation
    ) : LocationState
}