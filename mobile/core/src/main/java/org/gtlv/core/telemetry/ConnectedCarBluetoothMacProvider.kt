package org.gtlv.core.telemetry

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Finds the single Bluetooth device most likely to be the connected car. */
internal class ConnectedCarBluetoothMacProvider(
    context: Context,
    private val onMacAddressChanged: (String?) -> Unit
) {
    private val applicationContext = context.applicationContext
    private val bluetoothAdapter = applicationContext
        .getSystemService(BluetoothManager::class.java)
        ?.adapter

    private val profileProxies = mutableMapOf<Int, BluetoothProfile>()
    private val addressesByProfile = mutableMapOf<Int, Set<String>>()
    private val pendingProfiles = mutableSetOf<Int>()
    private var receiverRegistered = false
    private var started = false

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(
            profile: Int,
            proxy: BluetoothProfile
        ) {
            if (!started) {
                closeProfileProxy(profile, proxy)
                return
            }

            profileProxies[profile] = proxy
            pendingProfiles.remove(profile)
            refreshProfile(profile, proxy)
            publishIfReady()
        }

        override fun onServiceDisconnected(profile: Int) {
            profileProxies.remove(profile)
            addressesByProfile[profile] = emptySet()
            pendingProfiles.remove(profile)
            publishIfReady()
        }
    }

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasBluetoothPermission()) {
            onMacAddressChanged(null)
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onMacAddressChanged(null)
            return
        }

        started = true
        registerConnectionReceiver()

        SUPPORTED_PROFILES.forEach { profile ->
            pendingProfiles.add(profile)

            val requested = runCatching {
                adapter.getProfileProxy(
                    applicationContext,
                    serviceListener,
                    profile
                )
            }.getOrDefault(false)

            if (!requested) {
                pendingProfiles.remove(profile)
                addressesByProfile[profile] = emptySet()
            }
        }

        publishIfReady()
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        if (!started) {
            start()
            return
        }

        if (!hasBluetoothPermission()) {
            onMacAddressChanged(null)
            return
        }

        profileProxies.forEach(::refreshProfile)
        publishIfReady()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        started = false

        if (receiverRegistered) {
            runCatching {
                applicationContext.unregisterReceiver(
                    connectionReceiver
                )
            }
            receiverRegistered = false
        }

        /*
         * Closing a profile proxy can synchronously call
         * onServiceDisconnected(), which modifies profileProxies.
         * Therefore, copy and clear the map before closing anything.
         */
        val proxiesToClose = profileProxies.toList()

        profileProxies.clear()
        addressesByProfile.clear()
        pendingProfiles.clear()

        proxiesToClose.forEach { (profile, proxy) ->
            closeProfileProxy(profile, proxy)
        }

        onMacAddressChanged(null)
    }

    @SuppressLint("MissingPermission")
    private fun refreshProfile(
        profile: Int,
        proxy: BluetoothProfile
    ) {
        addressesByProfile[profile] = runCatching {
            proxy.connectedDevices
                .map(BluetoothDevice::getAddress)
                .filter(BluetoothAdapter::checkBluetoothAddress)
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun publishIfReady() {
        if (pendingProfiles.isNotEmpty()) return

        onMacAddressChanged(
            selectConnectedCarMacAddress(addressesByProfile)
        )
    }

    @SuppressLint("MissingPermission")
    private fun closeProfileProxy(
        profile: Int,
        proxy: BluetoothProfile
    ) {
        runCatching {
            bluetoothAdapter?.closeProfileProxy(profile, proxy)
        }
    }

    private fun registerConnectionReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }

        ContextCompat.registerReceiver(
            applicationContext,
            connectionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun hasBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        val SUPPORTED_PROFILES = listOf(
            BluetoothProfile.HEADSET,
            BluetoothProfile.A2DP
        )
    }
}

/**
 * A device connected through both hands-free and audio profiles is preferred.
 * Ambiguous results deliberately return null instead of identifying the wrong
 * vehicle.
 */
internal fun selectConnectedCarMacAddress(
    addressesByProfile: Map<Int, Set<String>>
): String? {
    val headsetAddresses =
        addressesByProfile[BluetoothProfile.HEADSET].orEmpty()
    val audioAddresses =
        addressesByProfile[BluetoothProfile.A2DP].orEmpty()

    val sharedAddress = headsetAddresses
        .intersect(audioAddresses)
        .singleOrNull()

    if (sharedAddress != null) return sharedAddress

    return (headsetAddresses + audioAddresses).singleOrNull()
}
