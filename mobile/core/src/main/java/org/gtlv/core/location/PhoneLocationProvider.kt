package org.gtlv.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneLocationProvider(
    context: Context
) : LocationProvider {

    private val applicationContext = context.applicationContext

    private val fusedLocationClient:
            FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(
            applicationContext
        )

    private val _state =
        MutableStateFlow<LocationState>(LocationState.Stopped)

    override val state: StateFlow<LocationState> =
        _state.asStateFlow()

    private var started = false

    private val locationCallback = object : LocationCallback() {

        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return

            _state.value = LocationState.Available(
                location = location.toAtlasLocation()
            )
        }

        override fun onLocationAvailability(
            availability: LocationAvailability
        ) {
            if (
                !availability.isLocationAvailable &&
                _state.value !is LocationState.Available
            ) {
                _state.value = LocationState.Unavailable
            }
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun start() {
        if (started) return

        val fineLocationGranted = hasPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val coarseLocationGranted = hasPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (!fineLocationGranted && !coarseLocationGranted) {
            _state.value = LocationState.PermissionDenied
            return
        }

        val priority = if (fineLocationGranted) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(
            priority,
            LOCATION_INTERVAL_MILLIS
        )
            .setMinUpdateIntervalMillis(
                MIN_LOCATION_INTERVAL_MILLIS
            )
            .build()

        _state.value = LocationState.WaitingForLocation
        started = true

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener {
                started = false
                _state.value = LocationState.Unavailable
            }
        } catch (_: SecurityException) {
            started = false
            _state.value = LocationState.PermissionDenied
        }
    }

    override fun stop() {
        if (started) {
            fusedLocationClient.removeLocationUpdates(
                locationCallback
            )
        }

        started = false
        _state.value = LocationState.Stopped
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val LOCATION_INTERVAL_MILLIS = 5_000L
        const val MIN_LOCATION_INTERVAL_MILLIS = 2_000L
    }
}