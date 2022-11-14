package com.philips.btserver.userdataservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.welie.blessed.BluetoothPeripheralManager
import java.util.*

class UserDataService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(USER_DATA_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

    /*
     * The Database Change Increment characteristic is used to represent a count of the changes made
     * to a set of related characteristic(s) as defined by the containing service. It can be used to
     * determine the need to synchronize this set between a Server and a Client. Value is a uint32
     */
    internal val dbChangeIncrementCharacteristic = BluetoothGattCharacteristic(
        USER_DATABASE_CHANGE_INCREMENT,
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
        0
    )

    internal val indexCharacteristic = BluetoothGattCharacteristic(
        USER_INDEX_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        0
    )

    internal val controlPointCharacteristic = BluetoothGattCharacteristic(
        UDS_CONTROL_POINT_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
        0
    )

    internal var currentUserIndex = 0xFF

    private val controlPointHandler = UserDataControlPointHandler(this)

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        super.onCentralDisconnected(central)
    }

    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        when(characteristic.uuid) {
            USER_INDEX_CHARACTERISTIC_UUID -> ObservationEmitter.singleShotEmit()
        }
    }


    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        return when(characteristic.uuid) {
            UDS_CONTROL_POINT_CHARACTERISTIC_UUID -> controlPointHandler.writeGattStatusFor(value)
            else -> GattStatus.WRITE_NOT_PERMITTED
        }
    }

    override fun onCharacteristicWriteCompleted(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray) {
        when(characteristic.uuid) {
            UDS_CONTROL_POINT_CHARACTERISTIC_UUID -> controlPointHandler.handleReceivedBytes(value)
        }
    }

    fun setUserIndex(index: Int) {
        currentUserIndex = index
    }

    companion object {
        val USER_DATA_SERVICE_UUID = UUID.fromString("0000181C-0000-1000-8000-00805f9b34fb")
        val USER_DATABASE_CHANGE_INCREMENT = UUID.fromString("00002a99-0000-1000-8000-00805f9b34fb")
        val USER_INDEX_CHARACTERISTIC_UUID = UUID.fromString("00002a9a-0000-1000-8000-00805f9b34fb")
        val UDS_CONTROL_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002a9f-0000-1000-8000-00805f9b34fb")

        val UDS_AGE_CHARACTERISTIC_UUID = UUID.fromString("00002a80-0000-1000-8000-00805f9b34fb")
        val UDS_FIRST_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002a8a-0000-1000-8000-00805f9b34fb")
        val UDS_LAST_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002a90-0000-1000-8000-00805f9b34fb")

        private const val USER_DATABASE_CHANGE_DESCRIPTION = "User database change increment"

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