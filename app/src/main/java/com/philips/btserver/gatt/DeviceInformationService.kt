/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.gatt

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Build
import com.welie.blessed.BluetoothPeripheralManager
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import java.util.*

internal class DeviceInformationService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(DIS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    private val manufacturerChar = BluetoothGattCharacteristic(MANUFACTURER_NAME_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
    private val modelNumberChar = BluetoothGattCharacteristic(MODEL_NUMBER_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)

    companion object {
        val DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")

        // If the BluetoothService has a running DeviceInformationService then return it
        fun getInstance(): DeviceInformationService? {
            val bleServer = BluetoothServer.getInstance()
            val dis = bleServer?.getServiceWithUUID(DIS_SERVICE_UUID)
            return dis?.let { it as DeviceInformationService }
        }
    }

    init {
        service.addCharacteristic(manufacturerChar)
        service.addCharacteristic(modelNumberChar)
        setDefaultValues()
    }

    private fun setDefaultValues() {
        setManufacturer(Build.MANUFACTURER)
        setModelNumber(Build.MODEL)
    }

    fun getManufacturer(): String {
        return manufacturerChar.getStringValue(0)
    }

    fun setManufacturer(manufacturerName: String) {
        manufacturerChar.setValue(manufacturerName)
    }

    fun getModelNumber(): String {
        return modelNumberChar.getStringValue(0)
    }

    fun setModelNumber(modelNum: String) {
        modelNumberChar.setValue(modelNum)
    }
}