package com.welie.btserver

import android.bluetooth.*
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Handler
import android.os.ParcelUuid
import com.welie.btserver.generichealthservice.GenericHealthSensorService
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.util.*

internal class BluetoothServer(private val context: Context) {
    private val handler = Handler()
    var bluetoothAdapter: BluetoothAdapter
    var bluetoothManager: BluetoothManager
    private val peripheralManager: PeripheralManager
    private val peripheralManagerCallback: PeripheralManagerCallback = object : PeripheralManagerCallback {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {}
        override fun onCharacteristicRead(central: Central, characteristic: BluetoothGattCharacteristic) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onCharacteristicRead(central, characteristic)
        }

        override fun onCharacteristicWrite(central: Central, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
            val serviceImplementation = serviceImplementations[characteristic.service]
            return if (serviceImplementation != null) {
                serviceImplementation.onCharacteristicWrite(central, characteristic, value)
            } else GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onDescriptorRead(central: Central, descriptor: BluetoothGattDescriptor) {
            val characteristic = Objects.requireNonNull(descriptor.characteristic, "Descriptor has no Characteristic")
            val service = Objects.requireNonNull(characteristic.service, "Characteristic has no Service")
            val serviceImplementation = serviceImplementations[service]
            serviceImplementation?.onDescriptorRead(central, descriptor)
        }

        override fun onDescriptorWrite(central: Central, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
            val characteristic = Objects.requireNonNull(descriptor.characteristic, "Descriptor has no Characteristic")
            val service = Objects.requireNonNull(characteristic.service, "Characteristic has no Service")
            val serviceImplementation = serviceImplementations[service]
            return if (serviceImplementation != null) {
                serviceImplementation.onDescriptorWrite(central, descriptor, value)
            } else GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onNotifyingEnabled(central: Central, characteristic: BluetoothGattCharacteristic) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onNotifyingEnabled(central, characteristic)
        }

        override fun onNotifyingDisabled(central: Central, characteristic: BluetoothGattCharacteristic) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onNotifyingDisabled(central, characteristic)
        }

        override fun onCentralConnected(central: Central) {
            for (serviceImplementation in serviceImplementations.values) {
                serviceImplementation.onCentralConnected(central)
            }
        }

        override fun onCentralDisconnected(central: Central) {
            for (serviceImplementation in serviceImplementations.values) {
                serviceImplementation.onCentralDisconnected(central)
            }
        }
    }

    fun startAdvertising(serviceUUID: UUID?) {
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

    fun getServiceWithUUID(serviceUUID: UUID): Service? {
        return serviceImplementations.entries.find { it.key.uuid == serviceUUID }?.value
    }

    private val serviceImplementations = HashMap<BluetoothGattService, Service>()

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
        peripheralManager = PeripheralManager(context, bluetoothManager, peripheralManagerCallback)
//        val dis = DeviceInformationService(peripheralManager)
        val cts = CurrentTimeService(peripheralManager)
//        val hrs = HeartRateService(peripheralManager)
        val ghs = GenericHealthSensorService(peripheralManager)
//        serviceImplementations[dis.service] = dis
//        serviceImplementations[cts.service] = cts
//        serviceImplementations[hrs.service] = hrs
        serviceImplementations[ghs.service] = ghs
        setupServices()
        startAdvertising(ghs.service.uuid)
    }
}