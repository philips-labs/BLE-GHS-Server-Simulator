package com.philips.btserver.userdataservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattService
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.generichealthservice.ElapsedTimeService
import com.philips.btserver.generichealthservice.GenericHealthSensorService
import com.philips.btserver.generichealthservice.isBonded
import com.philips.btserver.observations.ObservationStore
import com.philips.btserver.observations.asByteArray
import com.welie.blessed.*
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
        USER_DATABASE_CHANGE_INCREMENT_UUID,
        PROPERTY_READ or PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_READ or PERMISSION_WRITE
    )

    internal val registeredUserCharacteristic = BluetoothGattCharacteristic(
        REGISTERED_USER_CHARACTERISTIC_UUID,
        PROPERTY_INDICATE,
        0
    )

    internal val indexCharacteristic = BluetoothGattCharacteristic(
        USER_INDEX_CHARACTERISTIC_UUID,
        PROPERTY_READ,
        PERMISSION_READ_ENCRYPTED
    )

    internal val controlPointCharacteristic = BluetoothGattCharacteristic(
        UDS_CONTROL_POINT_CHARACTERISTIC_UUID,
        PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_WRITE_ENCRYPTED
    )

    internal val firstNameCharacteristic = BluetoothGattCharacteristic(
        UDS_FIRST_NAME_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_WRITE,
        PERMISSION_READ or PERMISSION_WRITE
    )

    internal val lastNameCharacteristic = BluetoothGattCharacteristic(
        UDS_LAST_NAME_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_WRITE,
        PERMISSION_READ or PERMISSION_WRITE
    )

    private val controlPointHandler = UserDataControlPointHandler(this)

    internal val registeredUsersSendHandler = RegisteredUsersSendHandler(this, registeredUserCharacteristic)

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
//        if (!central.isBonded()) central.createBond()
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
            USER_INDEX_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, byteArrayOf(getCurrentUserIndexForCentral(central).toByte()))
            USER_DATABASE_CHANGE_INCREMENT_UUID -> ReadResponse(GattStatus.SUCCESS, getCurrentUserDatabaseIncrementForCentral(central).asByteArray())
            UDS_FIRST_NAME_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, getCurrentFirstNameForCentral(central).asByteArray())
            UDS_LAST_NAME_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, getCurrentLastNameForCentral(central).asByteArray())
            else -> super.onCharacteristicRead(central, characteristic)
        }
    }

    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        return when(characteristic.uuid) {
            UDS_CONTROL_POINT_CHARACTERISTIC_UUID -> controlPointHandler.writeGattStatusFor(value)
            UDS_FIRST_NAME_CHARACTERISTIC_UUID,
            UDS_LAST_NAME_CHARACTERISTIC_UUID -> GattStatus.SUCCESS
            else -> GattStatus.WRITE_NOT_PERMITTED
        }
    }

    override fun onCharacteristicWriteCompleted(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray) {
        when(characteristic.uuid) {
            UDS_CONTROL_POINT_CHARACTERISTIC_UUID -> controlPointHandler.handleReceivedBytes(central, value)
            UDS_FIRST_NAME_CHARACTERISTIC_UUID -> setCurrentFirstNameForCentral(central, value)
            UDS_LAST_NAME_CHARACTERISTIC_UUID -> setCurrentLastNameForCentral(central, value)
            USER_DATABASE_CHANGE_INCREMENT_UUID -> setCurrentUserDatabaseIncrementForCentral(central, value)
        }
    }

    fun getCurrentUserIndexForCentral(central: BluetoothCentral): UByte {
        return getCurrentUserIndexForCentralAddress(central.address)
    }

    fun getCurrentUserIndexForCentralAddress(address: String): UByte {
        return (currentUserIndexes[address] ?: 0xFF).toUByte()
    }

    fun setUserIndexForCentral(central: BluetoothCentral, userIndex: Int) {
        currentUserIndexes.put(central.address, userIndex)
        sendTempStoredObservations(central, userIndex)
        // TODO Send any pending temp stored observations
    }

    fun userDataForCentral(central: BluetoothCentral): UserData? {
        return UserDataManager.getInstance().userDataForIndex(getCurrentUserIndexForCentral(central).toInt())
    }

    fun getCurrentFirstNameForCentral(central: BluetoothCentral): String {
        return userDataForCentral(central)?.firstName ?: ""
    }

    fun setCurrentFirstNameForCentral(central: BluetoothCentral, firstName: ByteArray) {
        userDataForCentral(central)?.let { it.firstName = String(firstName)}
    }

    fun getCurrentLastNameForCentral(central: BluetoothCentral): String {
        return userDataForCentral(central)?.firstName ?: ""
    }

    fun setCurrentLastNameForCentral(central: BluetoothCentral, firstName: ByteArray) {
        userDataForCentral(central)?.let { it.lastName = String(firstName)}
    }

    fun getCurrentUserDatabaseIncrementForCentral(central: BluetoothCentral): Int {
        return userDataForCentral(central)?.version ?: 0
    }

    fun setCurrentUserDatabaseIncrementForCentral(central: BluetoothCentral, value: ByteArray) {
        if (value.size != 4) return
        userDataForCentral(central)?.let {
            it.version = BluetoothBytesParser(value).uInt32
            updateCentralsDatabaseIncrement(value, central)
        }
    }

    private fun updateCentralsDatabaseIncrement(value: ByteArray, central: BluetoothCentral) {
        val userIndex = getCurrentUserIndexForCentral(central).toInt()
        notifyCharacteristicChangedFilterCentrals(
            value,
            {  it.address != central.address && (getCurrentUserIndexForCentral(it).toInt() == userIndex) },
            dbChangeIncrementCharacteristic)
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
        val USER_DATABASE_CHANGE_INCREMENT_UUID = UUID.fromString("00002a99-0000-1000-8000-00805f9b34fb")
        val USER_INDEX_CHARACTERISTIC_UUID = UUID.fromString("00002a9a-0000-1000-8000-00805f9b34fb")
        val UDS_CONTROL_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002a9f-0000-1000-8000-00805f9b34fb")

        val UDS_AGE_CHARACTERISTIC_UUID = UUID.fromString("00002a80-0000-1000-8000-00805f9b34fb")
        val UDS_FIRST_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002a8a-0000-1000-8000-00805f9b34fb")
        val UDS_LAST_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002a90-0000-1000-8000-00805f9b34fb")

        val REGISTERED_USER_CHARACTERISTIC_UUID = UUID.fromString("00002b37-0000-1000-8000-00805f9b34fb")

        private const val USER_DATABASE_CHANGE_DESCRIPTION = "User database change increment"

        private const val USER_INDEX_DESCRIPTION = "User index characteristic"
        private const val UDS_CONTROL_POINT_DESCRIPTION = "Control Point characteristic"
        private const val UDS_DATABASE_INCREMENT_DESCRIPTION = "User DB Increment characteristic"
        private const val UDS_FIRST_NAME_DESCRIPTION = "First Name"
        private const val UDS_LAST_NAME_DESCRIPTION = "Last Name"

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
        initCharacteristic(dbChangeIncrementCharacteristic, UDS_DATABASE_INCREMENT_DESCRIPTION)
        initCharacteristic(firstNameCharacteristic, UDS_FIRST_NAME_DESCRIPTION)
        initCharacteristic(firstNameCharacteristic, UDS_LAST_NAME_DESCRIPTION)
    }

}

fun BluetoothCentral.currentUserIndex(): Int = UserDataService.getInstance()?.getCurrentUserIndexForCentral(this)?.toUByte()?.toInt() ?: UserDataManager.UNDEFINED_USER_INDEX