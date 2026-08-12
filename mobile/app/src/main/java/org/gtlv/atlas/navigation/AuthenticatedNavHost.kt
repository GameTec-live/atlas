package org.gtlv.atlas.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.gtlv.atlas.main.MainScreen
import org.gtlv.core.location.LocationState
import org.gtlv.core.shift.ShiftRole

@Composable
internal fun AuthenticatedNavHost(
    userName: String,
    role: ShiftRole,
    locationState: LocationState,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    serverAddress: String
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = MainDestination,
        modifier = modifier
    ) {
        composable<MainDestination> {
            MainScreen(
                userName = userName,
                role = role,
                serverAddress = serverAddress,
                locationState = locationState,
                onLogout = onLogout
            )
        }
    }
}