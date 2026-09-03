package org.gtlv.core.session

sealed interface SessionState {

    data object Checking : SessionState

    data object SignedOut : SessionState

    data class SignedIn(
        val userId: String,
        val userName: String,
        val isAdmin: Boolean = false
    ) : SessionState

    data class RoleCheckFailed(
        val userId: String,
        val userName: String,
        val isAdmin: Boolean = false
    ) : SessionState
}
