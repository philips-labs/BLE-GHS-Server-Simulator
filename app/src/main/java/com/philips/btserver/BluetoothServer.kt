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
import com.philips.btserver.generichealthservice.GenericHealthSensorService
import com.philips.btserver.generichealthservice.SimpleTimeService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.BluetoothPeripheralManagerCallback
import com.welie.blessed.GattStatus
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.util.*

interface BluetoothServerConnectionListener {
    fun onCentralConnected(central: BluetoothCentral)
    fun onCentralDisconnected(central: BluetoothCentral)
}

interface BluetoothServerAdvertisingListener {
    fun onStartAdvertising()
    fun onStopAdvertising()
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

        override fun onCharacteristicWriteCompleted(
            bluetoothCentral: BluetoothCentral,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray) {
            serviceImplementations[characteristic.service]?.onCharacteristicWriteCompleted(bluetoothCentral, characteristic, value)
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

        override fun onNotificationSent(
            bluetoothCentral: BluetoothCentral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            serviceImplementations[characteristic.service]?.onNotificationSent(bluetoothCentral,
                value,
                characteristic,
                status)
        }

        override fun onCentralConnected(central: BluetoothCentral) {
            (connectionListeners + serviceImplementations.values).forEach { it.onCentralConnected(central) }
        }

        override fun onCentralDisconnected(central: BluetoothCentral) {
            (connectionListeners + serviceImplementations.values).forEach { it.onCentralDisconnected(central) }
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

    // TODO Add the GHS advert data with (5.6.1)
    // Bytes from Brian when doing this TODO
    /*
        02 01 02 // flags
        0F 09 49 4E 32 30 31 33 2D 47 48 53 2D 53 49 4D //name
        02 0A F5 //tx power
        03 03 44 7F // service uuid
        06 16 44 7F 06 10 00 //service data -ECG - no pairing
     */
    fun startAdvertising() {
        val serviceUUID = GenericHealthSensorService.GHS_SERVICE_UUID
        val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
        val advertiseData = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(ParcelUuid(serviceUUID))
                .addServiceData(ParcelUuid(serviceUUID), getGHSAdvertBytes())
                .build()
        val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()
        peripheralManager.startAdvertising(advertiseSettings, scanResponse, advertiseData)
    }

    fun stopAdvertising() {
        peripheralManager.stopAdvertising()
    }

    // TODO This is fixed for a PulseOx (0x1004) with no security (0x00)...
    private fun getGHSAdvertBytes(): ByteArray {
        return byteArrayOf(0x04, 0x10, 0x00)
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
//        bluetoothAdapter.name = "${Build.MODEL}-GHS-SIM"
        bluetoothAdapter.name = "GHS-SIM"
        peripheralManager = BluetoothPeripheralManager(context, bluetoothManager, peripheralManagerCallback)
        peripheralManager.removeAllServices()

        val dis = DeviceInformationService(peripheralManager)
        val cts = CurrentTimeService(peripheralManager)
        val ghs = GenericHealthSensorService(peripheralManager)
        val time = SimpleTimeService(peripheralManager)
        serviceImplementations[dis.service] = dis
        serviceImplementations[cts.service] = cts
        serviceImplementations[ghs.service] = ghs
        serviceImplementations[time.service] = time
        setupServices()
        startAdvertising()
    }
}
