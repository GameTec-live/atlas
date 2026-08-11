package org.gtlv.core.location

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.annotation.MainThread
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.CarSensors
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads location reported by the vehicle connected through Android Auto. */
class CarLocationProvider(
    private val carContext: CarContext,
    private val updateRate: Int = CarSensors.UPDATE_RATE_NORMAL
) : LocationProvider {
    private val _state =
        MutableStateFlow<LocationState>(LocationState.Stopped)

    override val state: StateFlow<LocationState> =
        _state.asStateFlow()

    private var carSensors: CarSensors? = null
    private var started = false

    private val locationListener =
        OnCarDataAvailableListener<CarHardwareLocation> { hardwareLocation ->
            val carValue = hardwareLocation.location
            val location = carValue.value

            _state.value = if (
                carValue.status == CarValue.STATUS_SUCCESS &&
                location != null
            ) {
                LocationState.Available(
                    location = location.toAtlasCarLocation()
                )
            } else {
                LocationState.Unavailable
            }
        }

    @MainThread
    override fun start() {
        if (started) return

        if (!hasFineLocationPermission()) {
            _state.value = LocationState.PermissionDenied
            return
        }

        _state.value = LocationState.WaitingForLocation

        try {
            val sensors = carContext
                .getCarService(CarHardwareManager::class.java)
                .carSensors

            sensors.addCarHardwareLocationListener(
                updateRate,
                carContext.mainExecutor,
                locationListener
            )

            carSensors = sensors
            started = true
        } catch (_: SecurityException) {
            _state.value = LocationState.PermissionDenied
        } catch (_: RuntimeException) {
            _state.value = LocationState.Unavailable
        }
    }

    @MainThread
    override fun stop() {
        if (started) {
            runCatching {
                carSensors?.removeCarHardwareLocationListener(
                    locationListener
                )
            }
        }

        carSensors = null
        started = false
        _state.value = LocationState.Stopped
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            carContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun Location.toAtlasCarLocation(): AtlasLocation {
    return AtlasLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy.takeIf { hasAccuracy() },
        bearingDegrees = bearing.takeIf { hasBearing() },
        speedMetersPerSecond = speed.takeIf { hasSpeed() },
        timestampMillis = time,
        source = LocationSource.CAR
    )
}
