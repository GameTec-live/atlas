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
) : TelemetryProvider, TelemetryDiagnosticsProvider {
    private val _telemetry = MutableStateFlow<TelemetryData?>(null)

    override val telemetry: StateFlow<TelemetryData?> =
        _telemetry.asStateFlow()

    private val _odometerKilometers = MutableStateFlow<Double?>(null)

    override val odometerKilometers: StateFlow<Double?> =
        _odometerKilometers.asStateFlow()

    private val _vehicleFingerprint = MutableStateFlow<String?>(null)

    override val vehicleFingerprint: StateFlow<String?> =
        _vehicleFingerprint.asStateFlow()

    private val _telemetryDiagnostics =
        MutableStateFlow(TelemetryDiagnostics())

    override val telemetryDiagnostics: StateFlow<TelemetryDiagnostics> =
        _telemetryDiagnostics.asStateFlow()

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
            val odometerValue = mileage.odometerMeters

            updateDiagnostics {
                copy(
                    odometerKilometers =
                        odometerValue.toTelemetryDiagnosticValue()
                )
            }

            // Despite its legacy name, AndroidX returns this in kilometres.
            odometer = odometerValue
                .successfulValue()
                ?.toDouble()
                ?.takeIf { it.isFinite() && it >= 0.0 }

            if (odometer == null) {
                Log.w(
                    TAG,
                    "Odometer update unavailable: " +
                        "status=${odometerValue.status}, " +
                        "value=${odometerValue.value}"
                )
            }

            _odometerKilometers.value = odometer

            publishTelemetry()
        }

    private val fuelListener =
        OnCarDataAvailableListener<EnergyLevel> { energyLevel ->
            val batteryPercent = energyLevel.batteryPercent
            val fuelPercent = energyLevel.fuelPercent

            updateDiagnostics {
                copy(
                    batteryPercent =
                        batteryPercent.toTelemetryDiagnosticValue(),
                    fuelPercent =
                        fuelPercent.toTelemetryDiagnosticValue(),
                )
            }

            // The server field is named fuelLevel, but for an EV the usable
            // energy percentage is supplied as batteryPercent instead.
            fuelLevel = (
                fuelPercent.successfulValue()
                    ?: batteryPercent.successfulValue()
                )
                ?.toDouble()
                ?.takeIf { it in TelemetryData.FUEL_LEVEL_RANGE }

            Log.i(
                TAG,
                "Energy update: batteryStatus=${batteryPercent.status}, " +
                    "fuelStatus=${fuelPercent.status}"
            )

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
        val carContext = context as? CarContext
        if (carContext == null) {
            Log.w(TAG, "Ignoring telemetry connection without a CarContext")
            return
        }

        Log.i(
            TAG,
            "Connecting car telemetry at Car App API " +
                carContext.carAppApiLevel
        )

        if (connectedCarContext === carContext) {
            startCarTelemetryIfConnected()
            return
        }

        connectedCarContext?.let(::disconnectCar)
        connectedCarContext = carContext
        _telemetryDiagnostics.value = TelemetryDiagnostics(
            carAppApiLevel = carContext.carAppApiLevel,
            hardware = TelemetryDiagnosticValue(
                TelemetryDiagnosticStatus.WAITING
            ),
        )
        bluetoothMacProvider =
            ConnectedCarBluetoothMacProvider(carContext) { macAddress ->
                val fingerprint = macAddress?.let(
                    BluetoothVehicleId::fromMacAddress
                )

                if (_vehicleFingerprint.value != fingerprint) {
                    _vehicleFingerprint.value = fingerprint
                    vehicleId = null
                }
                publishTelemetry()
            }

        startCarTelemetryIfConnected()
    }

    /** Clears only values that require an attached Android Auto car. */
    @MainThread
    fun disconnectCar(context: Context) {
        if (connectedCarContext !== context) return

        Log.i(TAG, "Disconnecting car telemetry")

        vehicleId = null
        _vehicleFingerprint.value = null
        fuelLevel = null
        odometer = null
        _odometerKilometers.value = null
        stopCarTelemetry()
        connectedCarContext = null
        bluetoothMacProvider = null
        _telemetryDiagnostics.value = TelemetryDiagnostics()
        publishTelemetry()
    }

    @MainThread
    override fun setVehicleState(state: TelemetryVehicleState) {
        vehicleState = state
        publishTelemetry()
    }

    @MainThread
    override fun setResolvedVehicleId(vehicleId: String?) {
        this.vehicleId = vehicleId
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

        val info = carInfo ?: run {
            if (carContext.carAppApiLevel < MIN_CAR_API_LEVEL) {
                Log.w(TAG, "Vehicle telemetry requires Car App API level 3")
                updateDiagnostics {
                    copy(
                        hardware = TelemetryDiagnosticValue(
                            status = TelemetryDiagnosticStatus.UNSUPPORTED,
                            detail = "Requires Car App API level 3 or newer",
                        )
                    )
                }
                return
            }

            val hardwareResult = runCatching {
                carContext
                    .getCarService(CarHardwareManager::class.java)
                    .carInfo
            }.onFailure {
                Log.w(TAG, "Car hardware information is unavailable", it)
            }

            hardwareResult.exceptionOrNull()?.let { error ->
                updateDiagnostics {
                    copy(
                        hardware = error.toFailedDiagnostic()
                    )
                }
            }

            hardwareResult.getOrNull()?.also {
                carInfo = it
                updateDiagnostics {
                    copy(
                        hardware = TelemetryDiagnosticValue(
                            TelemetryDiagnosticStatus.SUCCESS
                        )
                    )
                }
            } ?: return
        }

        if (!fuelListenerRegistered) {
            val listenerResult = runCatching {
                info.addEnergyLevelListener(
                    carContext.mainExecutor,
                    fuelListener
                )
            }.onFailure {
                Log.w(TAG, "Fuel level is unavailable", it)
            }
            fuelListenerRegistered = listenerResult.isSuccess

            updateDiagnostics {
                copy(
                    energyListener = listenerResult.toListenerDiagnostic()
                )
            }

            Log.i(
                TAG,
                "Fuel listener registered=$fuelListenerRegistered"
            )
        }

        if (!mileageListenerRegistered) {
            val listenerResult = runCatching {
                info.addMileageListener(
                    carContext.mainExecutor,
                    mileageListener
                )
            }.onFailure {
                Log.w(TAG, "Odometer is unavailable", it)
            }
            mileageListenerRegistered = listenerResult.isSuccess

            updateDiagnostics {
                copy(
                    mileageListener = listenerResult.toListenerDiagnostic()
                )
            }

            Log.i(
                TAG,
                "Odometer listener registered=$mileageListenerRegistered"
            )
        }
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

    private inline fun updateDiagnostics(
        transform: TelemetryDiagnostics.() -> TelemetryDiagnostics
    ) {
        _telemetryDiagnostics.value =
            _telemetryDiagnostics.value.transform()
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

private fun CarValue<Float>.toTelemetryDiagnosticValue():
        TelemetryDiagnosticValue {
    val diagnosticStatus = when (status) {
        CarValue.STATUS_SUCCESS -> TelemetryDiagnosticStatus.SUCCESS
        CarValue.STATUS_UNIMPLEMENTED ->
            TelemetryDiagnosticStatus.UNIMPLEMENTED
        CarValue.STATUS_UNAVAILABLE -> TelemetryDiagnosticStatus.UNAVAILABLE
        else -> TelemetryDiagnosticStatus.UNKNOWN
    }

    return TelemetryDiagnosticValue(
        status = diagnosticStatus,
        value = successfulValue()?.toDouble(),
    )
}

private fun Result<Unit>.toListenerDiagnostic(): TelemetryDiagnosticValue {
    return exceptionOrNull()?.toFailedDiagnostic()
        ?: TelemetryDiagnosticValue(TelemetryDiagnosticStatus.SUCCESS)
}

private fun Throwable.toFailedDiagnostic(): TelemetryDiagnosticValue {
    val description = buildString {
        append(this@toFailedDiagnostic::class.java.simpleName)
        message?.takeIf { it.isNotBlank() }?.let { message ->
            append(": ")
            append(message)
        }
    }

    return TelemetryDiagnosticValue(
        status = TelemetryDiagnosticStatus.FAILED,
        detail = description,
    )
}
