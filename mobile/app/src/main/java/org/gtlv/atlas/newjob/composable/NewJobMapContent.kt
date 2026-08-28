package org.gtlv.atlas.newjob.composable

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.map.AtlasMap
import org.gtlv.atlas.map.MapConfiguration
import org.gtlv.atlas.newjob.NewJobUiState
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.job.JobLocationField
import org.gtlv.core.location.LocationState
import org.gtlv.core.telemetry.LiveMapUser

@Composable
internal fun NewJobMapContent(
    state: NewJobUiState,
    serverAddress: String,
    locationState: LocationState,
    liveMapUsers: Collection<LiveMapUser>,
    onEditAddress: (JobLocationField) -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onAddressSuggestionSelected: (AddressSuggestion) -> Unit,
    onCloseAddressEditor: () -> Unit,
    onDueDateChanged: (String) -> Unit,
    onUnassignedDueDateSelected: (String) -> Unit,
    onDueDatePickerClosed: () -> Unit,
    onNoteChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
    var fieldsHeightPixels by remember { mutableIntStateOf(0) }
    val fieldsHeight = with(density) {
        fieldsHeightPixels.toDp()
    }

    Box(modifier = modifier) {
        AtlasMap(
            locationState = locationState,
            liveMapUsers = liveMapUsers,
            routePoints = state.route?.points.orEmpty(),
            showRouteEndpoints = true,
            recenterRequestId = 0,
            isFollowingLocation = false,
            onUserCameraMove = onCloseAddressEditor,
            onMapClick = onCloseAddressEditor,
            styleUrl = MapConfiguration.createStyleUrl(serverAddress),
            cameraFocusPoints = state.cameraFocusPoints,
            cameraFocusRequestId = state.cameraFocusRequestId,
            cameraFocusPadding = 48.dp,
            cameraFocusTopPadding = fieldsHeight + 24.dp,
            cameraFocusBottomPadding = if (isLandscape) {
                48.dp
            } else {
                208.dp
            },
            modifier = Modifier.fillMaxSize()
        )

        NewJobFields(
            state = state,
            onEditAddress = onEditAddress,
            onAddressQueryChanged = onAddressQueryChanged,
            onAddressSuggestionSelected = onAddressSuggestionSelected,
            onCloseAddressEditor = onCloseAddressEditor,
            onDueDateChanged = onDueDateChanged,
            onUnassignedDueDateSelected =
                onUnassignedDueDateSelected,
            onDueDatePickerClosed = onDueDatePickerClosed,
            onNoteChanged = onNoteChanged,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    fieldsHeightPixels = size.height
                }
        )
    }
}
