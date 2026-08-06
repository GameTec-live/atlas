package org.gtlv.atlas.role

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.role.composable.RoleSelectionContent
import androidx.compose.foundation.layout.Arrangement

@Composable
fun RoleSelectionScreen(
    state: RoleSelectionUiState,
    onDispatcherSelected: () -> Unit,
    onDriverSelected: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(
                        horizontal = 24.dp,
                        vertical = 24.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RoleSelectionContent(
                    state = state,
                    onDispatcherSelected = onDispatcherSelected,
                    onDriverSelected = onDriverSelected,
                    onRetry = onRetry,
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}