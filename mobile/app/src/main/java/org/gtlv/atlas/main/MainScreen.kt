package org.gtlv.atlas.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.map.AtlasMap
import org.gtlv.core.shift.ShiftRole

@Composable
internal fun MainScreen(
    userName: String,
    role: ShiftRole,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // The map uses the complete window, including behind system bars.
        AtlasMap(
            modifier = Modifier.fillMaxSize()
        )

        // Only interactive controls stay inside the safe area.
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
                text = role.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(text = "Log out")
            }
        }
    }
}

private fun ShiftRole.displayName(): String {
    return when (this) {
        ShiftRole.DRIVER -> "Driver"
        ShiftRole.DISPATCHER -> "Dispatcher"
    }
}