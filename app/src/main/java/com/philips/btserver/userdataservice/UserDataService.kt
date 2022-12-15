package com.philips.btserver.userdataservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattService
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.generichealthservice.GenericHealthSensorService
import com.philips.btserver.generichealthservice.isBonded
import com.philips.btserver.observations.ObservationStore
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import java.util.*

class UserDataService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(USER_DATA_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

    private val currentUserIndexes = mutableMapOf<String, Int>()
    /*
     * The Database Change Increment characteristic is used to represent a count of the changes made
     * to a set of related characteristic(s) as defined by the containing service. It can be used to
     * determine the need to synchronize this set between a Server and a Client. Value is a uint32
     */
    internal val dbChangeIncrementCharacteristic = BluetoothGattCharacteristic(
        USER_DATABASE_CHANGE_INCREMENT,
        PROPERTY_READ or PROPERTY_WRITE or PROPERTY_INDICATE,
        0
    )

    internal val indexCharacteristic = BluetoothGattCharacteristic(
        USER_INDEX_CHARACTERISTIC_UUID,
        PROPERTY_READ,
        PERMISSION_READ
    )

    internal val controlPointCharacteristic = BluetoothGattCharacteristic(
        UDS_CONTROL_POINT_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
        PERMISSION_WRITE_ENCRYPTED
    )

    private val controlPointHandler = UserDataControlPointHandler(this)

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
        //if (!central.isBonded()) central.createBond()
        setUserIndexForCentral(central, UserDataManager.UNDEFINED_USER_INDEX)
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        super.onCentralDisconnected(central)
        currentUserIndexes.remove(central.address)
    }

    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ): ReadResponse {
        return when(characteristic.uuid) {
            USER_INDEX_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, byteArrayOf(getCurrentUserIndexForCentral(central)))
            else -> super.onCharacteristicRead(central, characteristic)
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
            UDS_CONTROL_POINT_CHARACTERISTIC_UUID -> controlPointHandler.handleReceivedBytes(bluetoothCentral, value)
        }
    }


    fun getCurrentUserIndexForCentral(central: BluetoothCentral): Byte {
        return (currentUserIndexes[central.address] ?: 0xFF).toByte()
    }

    fun setUserIndexForCentral(central: BluetoothCentral, userIndex: Int) {
        currentUserIndexes.put(central.address, userIndex)
        sendTempStoredObservations(central, userIndex)
        // TODO Send any pending temp stored observations
    }

    fun sendTempStoredObservations(central: BluetoothCentral, userIndex: Int) {
        GenericHealthSensorService.getInstance()?.let { ghsService ->
            if (ObservationStore.isTemporaryStore && ghsService.isSendLiveObservationsEnabled) {
                ObservationStore.forEachUserTempObservation(userIndex) { obs -> ghsService.sendObservation(obs) }
                ObservationStore.clearObservationsForUser(userIndex)
            }
        }
    }

    companion object {
        val USER_DATA_SERVICE_UUID = UUID.fromString("0000181C-0000-1000-8000-00805f9b34fb")
        val USER_DATABASE_CHANGE_INCREMENT = UUID.fromString("00002a99-0000-1000-8000-00805f9b34fb")
        val USER_INDEX_CHARACTERISTIC_UUID = UUID.fromString("00002a9a-0000-1000-8000-00805f9b34fb")
        val UDS_CONTROL_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002a9f-0000-1000-8000-00805f9b34fb")

        val UDS_AGE_CHARACTERISTIC_UUID = UUID.fromString("00002a80-0000-1000-8000-00805f9b34fb")
        val UDS_FIRST_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002a8a-0000-1000-8000-00805f9b34fb")
        val UDS_LAST_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002a90-0000-1000-8000-00805f9b34fb")

        val REGISTERED_USER_CHARACTERISTIC_UUID = UUID.fromString("00002b37-0000-1000-8000-00805f9b34fb")

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

fun BluetoothCentral.currentUserIndex(): Int = UserDataService.getInstance()?.getCurrentUserIndexForCentral(this)?.toInt() ?: UserDataManager.UNDEFINED_USER_INDEX