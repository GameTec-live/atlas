package org.gtlv.atlas.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.gtlv.core.location.LocationProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.gtlv.core.location.LocationState

@Composable
internal fun RequiredLocationPermissionGate(
    locationProvider: LocationProvider,
    content: @Composable (LocationState) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationState by locationProvider.state
        .collectAsStateWithLifecycle()

    var permissionGranted by rememberSaveable {
        mutableStateOf(context.hasLocationPermission())
    }

    var hasRequestedPermission by rememberSaveable {
        mutableStateOf(false)
    }

    var requestInFlight by rememberSaveable {
        mutableStateOf(false)
    }

    var permissionPrompt by rememberSaveable {
        mutableStateOf(LocationPermissionPrompt.Hidden)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            requestInFlight = false

            val preciseGranted =
                permissions.getOrDefault(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    false
                )

            val approximateGranted =
                permissions.getOrDefault(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    false
                )

            permissionGranted =
                preciseGranted || approximateGranted

            if (permissionGranted) {
                permissionPrompt =
                    LocationPermissionPrompt.Hidden

                locationProvider.start()
            } else {
                // Allows the provider to publish PermissionDenied.
                locationProvider.start()

                permissionPrompt =
                    if (activity.canRequestLocationPermissionAgain()) {
                        LocationPermissionPrompt.Retry
                    } else {
                        LocationPermissionPrompt.Settings
                    }
            }
        }

    val requestPermission = {
        if (!requestInFlight) {
            hasRequestedPermission = true
            requestInFlight = true
            permissionPrompt = LocationPermissionPrompt.Hidden

            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(locationProvider) {
        permissionGranted = context.hasLocationPermission()

        if (permissionGranted) {
            permissionPrompt = LocationPermissionPrompt.Hidden
            locationProvider.start()
        } else {
            // Publishes PermissionDenied without crashing.
            locationProvider.start()

            if (!hasRequestedPermission) {
                requestPermission()
            } else if (!requestInFlight) {
                permissionPrompt =
                    if (activity.canRequestLocationPermissionAgain()) {
                        LocationPermissionPrompt.Retry
                    } else {
                        LocationPermissionPrompt.Settings
                    }
            }
        }
    }

    DisposableEffect(
        lifecycleOwner,
        locationProvider
    ) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        permissionGranted =
                            context.hasLocationPermission()

                        if (permissionGranted) {
                            permissionPrompt =
                                LocationPermissionPrompt.Hidden

                            locationProvider.start()
                        } else {
                            locationProvider.start()

                            if (
                                hasRequestedPermission &&
                                !requestInFlight
                            ) {
                                permissionPrompt =
                                    if (
                                        activity
                                            .canRequestLocationPermissionAgain()
                                    ) {
                                        LocationPermissionPrompt.Retry
                                    } else {
                                        LocationPermissionPrompt.Settings
                                    }
                            }
                        }
                    }

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (permissionGranted) {
        content(locationState)
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.background
                )
        )
    }

    LocationPermissionDialog(
        prompt = permissionPrompt,
        onTryAgain = requestPermission,
        onOpenSettings = {
            context.openApplicationSettings()
        }
    )
}

private fun Context.hasLocationPermission(): Boolean {
    val preciseGranted =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    val approximateGranted =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    return preciseGranted || approximateGranted
}

private fun Activity?.canRequestLocationPermissionAgain(): Boolean {
    if (this == null) {
        return false
    }

    return shouldShowRequestPermissionRationale(
        Manifest.permission.ACCESS_FINE_LOCATION
    ) || shouldShowRequestPermissionRationale(
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}

private fun Context.openApplicationSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts(
            "package",
            packageName,
            null
        )
    )

    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        startActivity(intent)
    }
}

private fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
