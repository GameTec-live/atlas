package org.gtlv.atlas.assign.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.assign.AssignJobUiState
import org.gtlv.atlas.map.AtlasMap
import org.gtlv.atlas.map.MapConfiguration
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.location.LocationState
import org.gtlv.core.telemetry.LiveMapUser

@Composable
internal fun AssignJobMapContent(
    state: AssignJobUiState,
    serverAddress: String,
    locationState: LocationState,
    liveMapUsers: Collection<LiveMapUser>,
    onEditAddress: (JobLocationField) -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected: (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    onDueDateChanged: (String) -> Unit,
    onSaveChanges: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AtlasMap(
            locationState = locationState,
            liveMapUsers = liveMapUsers,
            routePoints = state.route
                ?.points
                .orEmpty(),
            showRouteEndpoints = true,
            recenterRequestId = 0,
            isFollowingLocation = false,
            onUserCameraMove = {
                if (state.isAddressEditorOpen) {
                    onCloseAddressEditor()
                }
            },
            onMapClick = {
                if (state.isAddressEditorOpen) {
                    onCloseAddressEditor()
                }
            },
            styleUrl = MapConfiguration.createStyleUrl(
                serverAddress
            ),
            cameraFocusPoints = state.cameraFocusPoints,
            cameraFocusRequestId =
                state.cameraFocusRequestId,
            cameraFocusPadding = 112.dp,
            modifier = Modifier.fillMaxSize()
        )

        state.job?.let { job ->
            AddressFields(
                state = state,
                job = job,
                enabled =
                    !state.isAssigning &&
                            !state.isSavingChanges &&
                            !state.addressSearch.isSaving,
                onEditAddress = onEditAddress,
                onAddressQueryChanged = onAddressQueryChanged,
                onAddressSuggestionSelected =
                    onAddressSuggestionSelected,
                onCloseAddressEditor = onCloseAddressEditor,
                onDueDateChanged = onDueDateChanged,
                onSaveChanges = onSaveChanges,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
            )
        }
    }
}
