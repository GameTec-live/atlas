package org.gtlv.core.session

sealed interface SessionState {

    data object Checking : SessionState

    data object SignedOut : SessionState

    data class SignedIn(
        val userName: String
    ) : SessionState
}