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
import com.philips.btserver.userdataservice.UserDataManager
import com.philips.btserver.userdataservice.UserDataService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import java.util.*

internal class DeviceInformationService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(DIS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    private val manufacturerChar = BluetoothGattCharacteristic(MANUFACTURER_NAME_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
    private val modelNumberChar = BluetoothGattCharacteristic(MODEL_NUMBER_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
    private val uniqueDeviceIdentifierChar = BluetoothGattCharacteristic(UDI_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)

    var manufacturer: String = Build.MANUFACTURER
    var modelNumber: String = Build.MODEL
    var uuid: String = UUID.randomUUID().toString()

    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ): ReadResponse {
        return when(characteristic.uuid) {
            MANUFACTURER_NAME_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, manufacturer.toByteArray())
            MODEL_NUMBER_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, modelNumber.toByteArray())
            UDI_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, uuid.toByteArray())
            else -> ReadResponse(GattStatus.REQUEST_NOT_SUPPORTED, byteArrayOf())
        }
    }

    companion object {
        val DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")
        val UDI_CHARACTERISTIC_UUID = UUID.fromString("00007F3A-0000-1000-8000-00805f9b34fb")

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
        service.addCharacteristic(uniqueDeviceIdentifierChar)
    }

}