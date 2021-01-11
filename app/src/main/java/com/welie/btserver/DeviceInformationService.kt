package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Build
import java.util.*

internal class DeviceInformationService(peripheralManager: PeripheralManager) : BaseService(peripheralManager) {
    override var service = BluetoothGattService(DIS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    var manufacturer = BluetoothGattCharacteristic(MANUFACTURER_NAME_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
    var modelNumber = BluetoothGattCharacteristic(MODEL_NUMBER_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)

    companion object {
        private val DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        private val MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
        private val MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")
    }

    init {
        service.addCharacteristic(manufacturer)
        service.addCharacteristic(modelNumber)
        manufacturer.setValue(Build.MANUFACTURER)
        modelNumber.setValue(Build.MODEL)
    }
}