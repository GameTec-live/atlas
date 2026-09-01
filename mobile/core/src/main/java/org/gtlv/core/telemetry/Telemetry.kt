package org.gtlv.core.telemetry

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.Mileage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.gtlv.core.location.AtlasLocation
import org.gtlv.core.location.LocationProvider
import org.gtlv.core.location.LocationState

/**
 * Builds process-wide telemetry from the existing location provider and
 * enriches it with optional Android Auto vehicle data while a car is attached.
 */
class Telemetry(
    private val locationProvider: LocationProvider,
    initialState: TelemetryVehicleState = TelemetryVehicleState.FREE
) : TelemetryProvider {
    private val _telemetry = MutableStateFlow<TelemetryData?>(null)

    override val telemetry: StateFlow<TelemetryData?> =
        _telemetry.asStateFlow()

    private var vehicleState = initialState
    private var vehicleId: String? = null
    private var location: AtlasLocation? = null
    private var fuelLevel: Double? = null
    private var odometer: Double? = null

    private var scope: CoroutineScope? = null
    private var locationJob: Job? = null
    private var connectedCarContext: CarContext? = null
    private var bluetoothMacProvider:
            ConnectedCarBluetoothMacProvider? = null
    private var carInfo: CarInfo? = null
    private var fuelListenerRegistered = false
    private var mileageListenerRegistered = false
    private var started = false

    private val mileageListener =
        OnCarDataAvailableListener<Mileage> { mileage ->
            // Despite its legacy name, AndroidX returns this in kilometres.
            odometer = mileage.odometerMeters
                .successfulValue()
                ?.toDouble()
                ?.takeIf { it >= 0.0 }

            publishTelemetry()
        }

    private val fuelListener =
        OnCarDataAvailableListener<EnergyLevel> { energyLevel ->
            fuelLevel = energyLevel.fuelPercent
                .successfulValue()
                ?.toDouble()
                ?.takeIf { it in TelemetryData.FUEL_LEVEL_RANGE }

            publishTelemetry()
        }

    /** Starts observing the existing car-aware location provider. */
    @MainThread
    override fun start() {
        if (started) return
        started = true

        val telemetryScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate
        )
        scope = telemetryScope

        locationJob = telemetryScope.launch {
            locationProvider.state.collect { locationState ->
                location = (locationState as? LocationState.Available)
                    ?.location
                publishTelemetry()
            }
        }

        startCarTelemetryIfConnected()
    }

    @MainThread
    override fun stop() {
        if (!started) return

        connectedCarContext?.let(::disconnectCar)
        locationJob?.cancel()
        scope?.cancel()

        locationJob = null
        scope = null
        started = false
        location = null
        _telemetry.value = null
    }

    /** Adds optional vehicle information without changing location selection. */
    @MainThread
    fun connectCar(context: Context) {
        val carContext = context as? CarContext ?: return

        if (connectedCarContext === carContext) {
            startCarTelemetryIfConnected()
            return
        }

        connectedCarContext?.let(::disconnectCar)
        connectedCarContext = carContext
        bluetoothMacProvider =
            ConnectedCarBluetoothMacProvider(carContext) { macAddress ->
                vehicleId = macAddress?.let(
                    BluetoothVehicleId::fromMacAddress
                )
                publishTelemetry()
            }

        startCarTelemetryIfConnected()
    }

    /** Clears only values that require an attached Android Auto car. */
    @MainThread
    fun disconnectCar(context: Context) {
        if (connectedCarContext !== context) return

        vehicleId = null
        fuelLevel = null
        odometer = null
        stopCarTelemetry()
        connectedCarContext = null
        bluetoothMacProvider = null
        publishTelemetry()
    }

    @MainThread
    override fun setVehicleState(state: TelemetryVehicleState) {
        vehicleState = state
        publishTelemetry()
    }

    @MainThread
    override fun refreshVehicleId() {
        bluetoothMacProvider?.refresh()
    }

    @MainThread
    private fun startCarTelemetryIfConnected() {
        if (!started) return

        val carContext = connectedCarContext ?: return

        bluetoothMacProvider?.start()

        if (carInfo != null) return

        if (carContext.carAppApiLevel < MIN_CAR_API_LEVEL) {
            Log.w(TAG, "Vehicle telemetry requires Car App API level 3")
            return
        }

        val info = runCatching {
            carContext
                .getCarService(CarHardwareManager::class.java)
                .carInfo
        }.onFailure {
            Log.w(TAG, "Car hardware information is unavailable", it)
        }.getOrNull() ?: return

        carInfo = info

        fuelListenerRegistered = runCatching {
            info.addEnergyLevelListener(
                carContext.mainExecutor,
                fuelListener
            )
        }.onFailure {
            Log.w(TAG, "Fuel level is unavailable", it)
        }.isSuccess

        mileageListenerRegistered = runCatching {
            info.addMileageListener(
                carContext.mainExecutor,
                mileageListener
            )
        }.onFailure {
            Log.w(TAG, "Odometer is unavailable", it)
        }.isSuccess
    }

    @MainThread
    private fun stopCarTelemetry() {
        if (fuelListenerRegistered) {
            runCatching {
                carInfo?.removeEnergyLevelListener(fuelListener)
            }
        }

        if (mileageListenerRegistered) {
            runCatching {
                carInfo?.removeMileageListener(mileageListener)
            }
        }

        bluetoothMacProvider?.stop()
        carInfo = null
        fuelListenerRegistered = false
        mileageListenerRegistered = false
    }

    private fun publishTelemetry() {
        val currentLocation = location
        val currentState = vehicleState

        val updatedTelemetry = if (currentLocation == null) {
            null
        } else {
            TelemetryData(
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                state = currentState,
                vehicleId = vehicleId,
                fuelLevel = fuelLevel,
                odometer = odometer
            )
        }

        _telemetry.value = updatedTelemetry
    }

    companion object {
        const val CAR_FUEL_PERMISSION =
            "com.google.android.gms.permission.CAR_FUEL"
        const val CAR_MILEAGE_PERMISSION =
            "com.google.android.gms.permission.CAR_MILEAGE"

        val VEHICLE_PERMISSIONS = listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            CAR_FUEL_PERMISSION,
            CAR_MILEAGE_PERMISSION
        )

        private const val MIN_CAR_API_LEVEL = 3
        private const val TAG = "CarTelemetry"
    }
}

private fun <T> CarValue<T>.successfulValue(): T? {
    return value.takeIf { status == CarValue.STATUS_SUCCESS }
}
