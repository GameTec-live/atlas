package org.gtlv.atlas.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.map.AtlasMap
import org.gtlv.core.location.LocationState
import org.gtlv.core.shift.ShiftRole

@Composable
internal fun MainScreen(
    userName: String,
    role: ShiftRole,
    locationState: LocationState,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var recenterRequestId by remember {
        mutableIntStateOf(0)
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AtlasMap(
            locationState = locationState,
            recenterRequestId = recenterRequestId,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            MainScreenOverlay(
                userName = userName,
                role = role,
                onLogout = onLogout,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )

            if (locationState is LocationState.Available) {
                FloatingActionButton(
                    onClick = {
                        recenterRequestId += 1
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = stringResource(
                            R.string.map_recenter_location
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreenOverlay(
    userName: String,
    role: ShiftRole,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = 0.92f
        ),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = userName,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = stringResource(role.displayNameResource()),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.logout)
                )
            }
        }
    }
}

private fun ShiftRole.displayNameResource(): Int {
    return when (this) {
        ShiftRole.DRIVER ->
            R.string.role_driver

        ShiftRole.DISPATCHER ->
            R.string.role_dispatcher
    }
}