package org.gtlv.atlas.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.R
import org.gtlv.atlas.main.composable.JobPanel
import org.gtlv.atlas.main.composable.ProfileButton
import org.gtlv.atlas.main.composable.ProfileSidebar
import org.gtlv.atlas.map.AtlasMap
import org.gtlv.atlas.map.MapConfiguration
import org.gtlv.core.location.LocationState
import org.gtlv.core.shift.ShiftRole

@Composable
internal fun MainScreen(
    userName: String,
    role: ShiftRole,
    locationState: LocationState,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    jobState: MainScreenUiState,
    onToggleJobList: () -> Unit,
    onRetryJobs: () -> Unit,
    serverAddress: String
) {
    val styleUrl = MapConfiguration.createStyleUrl(
        serverAddress = serverAddress
    )

    var recenterRequestId by remember {
        mutableIntStateOf(0)
    }

    var isProfileOpen by rememberSaveable(userName) {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AtlasMap(
            locationState = locationState,
            recenterRequestId = recenterRequestId,
            styleUrl = styleUrl,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            JobPanel(
                state = jobState,
                onToggleExpanded = onToggleJobList,
                onRetry = onRetryJobs,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 8.dp,
                        bottom = 8.dp
                    )
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

            if (!isProfileOpen) {
                ProfileButton(
                    userName = userName,
                    onClick = {
                        isProfileOpen = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }

            if (isProfileOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = null,
                            onClick = {
                                isProfileOpen = false
                            }
                        )
                )
            }

            AnimatedVisibility(
                visible = isProfileOpen,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = slideInHorizontally(
                    initialOffsetX = { width -> width }
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { width -> width }
                ) + fadeOut()
            ) {
                ProfileSidebar(
                    userName = userName,
                    role = role,
                    onClose = {
                        isProfileOpen = false
                    },
                    onLogout = {
                        isProfileOpen = false
                        onLogout()
                    }
                )
            }
        }
    }
}





fun String.initial(): String {
    return trim()
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "?"
}

fun ShiftRole.displayNameResource(): Int {
    return when (this) {
        ShiftRole.DRIVER ->
            R.string.role_driver

        ShiftRole.DISPATCHER ->
            R.string.role_dispatcher
    }
}