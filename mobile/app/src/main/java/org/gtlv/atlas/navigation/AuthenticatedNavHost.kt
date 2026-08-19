package org.gtlv.atlas.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.gtlv.atlas.main.MainScreen
import org.gtlv.atlas.main.MainScreenUiState
import org.gtlv.core.location.LocationState
import org.gtlv.core.shift.ShiftRole
import org.gtlv.core.geoservice.AddressSuggestion

@Composable
internal fun AuthenticatedNavHost(
    userName: String,
    role: ShiftRole,
    locationState: LocationState,
    onLogout: () -> Unit,
    serverAddress: String,
    mainScreenState: MainScreenUiState,
    onToggleJobList: () -> Unit,
    onRetryJobs: () -> Unit,
    onStartNextJob: () -> Unit,
    onCancelCurrentJob: () -> Unit,
    onPersonCollected: () -> Unit,
    onEditDestination: () -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected:
        (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    modifier: Modifier = Modifier
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
                jobState = mainScreenState,
                onToggleJobList = onToggleJobList,
                onRetryJobs = onRetryJobs,
                onStartNextJob = onStartNextJob,
                onCancelCurrentJob = onCancelCurrentJob,
                onPersonCollected = onPersonCollected,
                onEditDestination = onEditDestination,
                onAddressQueryChanged = onAddressQueryChanged,
                onAddressSuggestionSelected = onAddressSuggestionSelected,
                onCloseAddressEditor = onCloseAddressEditor,
                onLogout = onLogout
            )
        }
    }
}