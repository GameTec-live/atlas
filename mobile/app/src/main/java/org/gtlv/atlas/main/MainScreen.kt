package org.gtlv.atlas.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gtlv.core.shift.ShiftRole

@Composable
internal fun MainScreen(
    userName: String,
    role: ShiftRole,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome, $userName",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Current role: ${role.displayName()}",
            style = MaterialTheme.typography.bodyLarge
        )

        Button(
            onClick = onLogout,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(text = "Log out")
        }
    }
}

private fun ShiftRole.displayName(): String {
    return when (this) {
        ShiftRole.DRIVER -> "Driver"
        ShiftRole.DISPATCHER -> "Dispatcher"
    }
}