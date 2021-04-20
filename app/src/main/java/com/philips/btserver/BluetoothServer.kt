/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver

import android.bluetooth.*
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import com.philips.btserver.gatt.CurrentTimeService
import com.philips.btserver.gatt.DeviceInformationService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.BluetoothPeripheralManagerCallback
import com.welie.blessed.GattStatus
import com.philips.btserver.generichealthservice.GenericHealthSensorService
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.util.*

interface BluetoothServerConnectionListener {
    fun onCentralConnected(central: BluetoothCentral)
    fun onCentralDisconnected(central: BluetoothCentral)
}

internal class BluetoothServer(context: Context) {

    var bluetoothAdapter: BluetoothAdapter
    var bluetoothManager: BluetoothManager
    private val peripheralManager: BluetoothPeripheralManager

    // Listeners for central connect/disconnects
    private val connectionListeners = mutableListOf<BluetoothServerConnectionListener>()

    private val peripheralManagerCallback: BluetoothPeripheralManagerCallback = object : BluetoothPeripheralManagerCallback() {
        override fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
            serviceImplementations[characteristic.service]?.onCharacteristicRead(central, characteristic)
        }

        override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
            return serviceImplementations[characteristic.service]?.onCharacteristicWrite(central, characteristic, value)
                    ?: GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor) {
            serviceImplementations[descriptor.characteristic.service]?.onDescriptorRead(central, descriptor)
        }

        override fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
            return serviceImplementations[descriptor.characteristic.service]?.onDescriptorWrite(central, descriptor, value)
                    ?: GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
            serviceImplementations[characteristic.service]?.onNotifyingEnabled(central, characteristic)
        }

        override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
            serviceImplementations[characteristic.service]?.onNotifyingDisabled(central, characteristic)
        }

        override fun onCentralConnected(central: BluetoothCentral) {
            (connectionListeners + serviceImplementations.values).forEach { it.onCentralConnected(central) }
        }

        override fun onCentralDisconnected(central: BluetoothCentral) {
            (connectionListeners + connectionListeners).forEach { it.onCentralDisconnected(central) }
        }
    }

    fun addConnectionListener(connectionListner: BluetoothServerConnectionListener) {
        connectionListeners.add(connectionListner)
    }

    fun removeConnectionListener(connectionListner: BluetoothServerConnectionListener) {
        connectionListeners.remove(connectionListner)
    }

    fun numberOfCentralsConnected(): Int {
        return peripheralManager.getConnectedCentrals().size
    }

    fun startAdvertising(serviceUUID: UUID) {
        val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
        val advertiseData = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(ParcelUuid(serviceUUID))
                .build()
        val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()
        peripheralManager.startAdvertising(advertiseSettings, scanResponse, advertiseData)
    }

    private fun setupServices() {
        for (service in serviceImplementations.keys) {
            peripheralManager.add(service)
        }
    }

    fun getServiceWithUUID(serviceUUID: UUID): BaseService? {
        return serviceImplementations.entries.find { it.key.uuid == serviceUUID }?.value
    }

    private val serviceImplementations = HashMap<BluetoothGattService, BaseService>()

    companion object {
        private var instance: BluetoothServer? = null

        fun getInstance(): BluetoothServer? {
            return instance
        }

        @Synchronized
        fun getInstance(context: Context): BluetoothServer? {
            if (instance == null) {
                instance = BluetoothServer(context.applicationContext)
            }
            return instance
        }
    }

    init {

        // Plant a tree
        Timber.plant(DebugTree())
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            Timber.e("not supporting advertising")
        }
        bluetoothAdapter.name = "GHS-Simulator"
        peripheralManager = BluetoothPeripheralManager(context, bluetoothManager, peripheralManagerCallback)
        val dis = DeviceInformationService(peripheralManager)
        val cts = CurrentTimeService(peripheralManager)
        val ghs = GenericHealthSensorService(peripheralManager)
        serviceImplementations[dis.service] = dis
        serviceImplementations[cts.service] = cts
        serviceImplementations[ghs.service] = ghs
        setupServices()
        startAdvertising(ghs.service.uuid)
    }
}
