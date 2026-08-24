package org.gtlv.atlas.main

import org.gtlv.core.geoservice.Route
import org.gtlv.core.geoservice.RouteProgress

enum class NavigationPhase {
    None,
    ToPickup,
    ToDestination
}

enum class NavigationStatus {
    Idle,
    Loading,
    Ready,
    WaitingForLocation,
    PickupUnavailable,
    WaitingForDestination,
    Error
}

enum class NavigationError {
    Unauthorized,
    Network,
    Router,
    Server,
    InvalidResponse
}

data class NavigationUiState(
    val jobId: String? = null,
    val phase: NavigationPhase = NavigationPhase.None,
    val status: NavigationStatus = NavigationStatus.Idle,
    val route: Route? = null,
    val progress: RouteProgress? = null,
    val error: NavigationError? = null
)
