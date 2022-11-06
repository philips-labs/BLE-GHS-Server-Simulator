package com.philips.btserver.userdataservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.welie.blessed.BluetoothPeripheralManager
import java.util.*

class UserDataService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(USER_DATA_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

    internal val indexCharacteristic = BluetoothGattCharacteristic(
        USER_INDEX_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE,
        0
    )

    internal val controlPointCharacteristic = BluetoothGattCharacteristic(
        UDS_CONTROL_POINT_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    )

    companion object {
        val USER_DATA_SERVICE_UUID = UUID.fromString("0000181C-0000-1000-8000-00805f9b34fb")
        val USER_INDEX_CHARACTERISTIC_UUID =
            UUID.fromString("00002a9a-0000-1000-8000-00805f9b34fb")
        val UDS_CONTROL_POINT_CHARACTERISTIC_UUID =
            UUID.fromString("00002a9f-0000-1000-8000-00805f9b34fb")

        private const val USER_INDEX_DESCRIPTION = "User index characteristic"
        private const val UDS_CONTROL_POINT_DESCRIPTION = "Control Point characteristic"

        /**
         * If the [BluetoothServer] singleton has an instance of a UserDataService return it (otherwise null)
         */
        fun getInstance(): UserDataService? {
            val bleServer = BluetoothServer.getInstance()
            val uds = bleServer?.getServiceWithUUID(USER_DATA_SERVICE_UUID)
            return uds?.let { it as UserDataService }
        }
    }

    init {
        initCharacteristic(indexCharacteristic, USER_INDEX_DESCRIPTION)
        initCharacteristic(controlPointCharacteristic, UDS_CONTROL_POINT_DESCRIPTION)
    }

}