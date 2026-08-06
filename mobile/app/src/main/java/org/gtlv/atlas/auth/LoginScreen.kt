package org.gtlv.atlas.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gtlv.atlas.auth.composable.CompactLoginLayout
import org.gtlv.atlas.auth.composable.ServerAddressDialog
import org.gtlv.atlas.auth.composable.WideLoginLayout

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordVisibilityChanged: () -> Unit,
    onLogin: () -> Unit,
    onEditServer: () -> Unit,
    onServerAddressChanged: (String) -> Unit,
    onSaveServerAddress: () -> Unit,
    onDismissServerDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowSize = currentWindowDpSize()

    /*
     * 600 dp is the beginning of the medium-width range.
     * This includes most landscape phones, foldables and tablets.
     */
    val useWideLayout = windowSize.width >= 600.dp

    Surface(
        modifier = modifier.fillMaxSize()
    ) {
        if (useWideLayout) {
            WideLoginLayout(
                state = state,
                onUsernameChanged = onUsernameChanged,
                onPasswordChanged = onPasswordChanged,
                onPasswordVisibilityChanged =
                    onPasswordVisibilityChanged,
                onLogin = onLogin,
                onEditServer = onEditServer
            )
        } else {
            CompactLoginLayout(
                state = state,
                onUsernameChanged = onUsernameChanged,
                onPasswordChanged = onPasswordChanged,
                onPasswordVisibilityChanged =
                    onPasswordVisibilityChanged,
                onLogin = onLogin,
                onEditServer = onEditServer
            )
        }
    }

    if (state.showServerDialog) {
        ServerAddressDialog(
            state = state,
            onServerAddressChanged = onServerAddressChanged,
            onSaveServerAddress = onSaveServerAddress,
            onDismissServerDialog = onDismissServerDialog
        )
    }
}

