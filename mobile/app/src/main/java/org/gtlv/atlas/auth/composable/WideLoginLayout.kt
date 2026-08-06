package org.gtlv.atlas.auth.composable

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.auth.LoginUiState

@Composable
internal fun WideLoginLayout(
    state: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordVisibilityChanged: () -> Unit,
    onLogin: () -> Unit,
    onEditServer: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(
                    horizontal = 32.dp,
                    vertical = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Branding(
                serverAddress = state.serverAddress
            )

            Spacer(modifier = Modifier.height(32.dp))

            LoginForm(
                state = state,
                onUsernameChanged = onUsernameChanged,
                onPasswordChanged = onPasswordChanged,
                onPasswordVisibilityChanged =
                    onPasswordVisibilityChanged,
                onLogin = onLogin,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            ServerAddressButton(
                serverAddress = state.serverAddress,
                onClick = onEditServer
            )
        }
    }
}