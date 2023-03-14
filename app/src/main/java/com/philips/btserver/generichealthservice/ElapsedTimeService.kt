package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.*
import com.philips.btserver.util.TickCounter
import com.philips.btserver.util.TimeCounter
import com.welie.blessed.*
import timber.log.Timber
import java.util.*

internal class ElapsedTimeService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(ELAPSED_TIME_SERVICE_UUID, SERVICE_TYPE_PRIMARY)

    private val simpleTimeCharacteristic = BluetoothGattCharacteristic(
        ELASPED_TIME_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_READ or PERMISSION_WRITE
    )

    private val statusCharacteristic = BluetoothGattCharacteristic(
        CLOCK_STATUS_CHARACTERISTIC_UUID,
        PROPERTY_READ,
        PERMISSION_READ
    )

    private val capabilitiesCharacteristic = BluetoothGattCharacteristic(
        CLOCK_CAPABILITIES_CHARACTERISTIC_UUID,
        PROPERTY_READ,
        PERMISSION_READ
    )

    private fun hasBondedCentralReconnected(central: BluetoothCentral): Boolean {
        return disconnectedBondedCentrals.contains(central.address)
    }

    /**
     * Notification from [central] that [characteristic] has notification enabled.
     */
    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        // TODO: Remove after testing as the ETS spec does not call for doing this so don't just send the clock bytes
        // sendClockBytes()
    }

    /*
     * onCharacteristicRead is a non-abstract method with an empty body to have a default behavior to do nothing
     */
    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ): ReadResponse {
        return when(characteristic.uuid) {
            ELASPED_TIME_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, currentClockBytes())
            CLOCK_STATUS_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, clockStatusBytes())
            CLOCK_CAPABILITIES_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, clockCapabilitiesBytes())
            else -> super.onCharacteristicRead(central, characteristic)
        }
    }

    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        Timber.i("onCharacteristicWrite with Bytes: ${value.asFormattedHexString()}")
        return writeETSBytes(value)
    }

    override fun onCharacteristicWriteCompleted(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        notifyClockBytes(notify = true)
        Timber.i("onCharacteristicWriteCompleted")
    }

    private fun writeETSBytes(value: ByteArray) : GattStatus {
        val writeFlags = value.first().asBitmask()
        if (!writeFlagsValid(writeFlags)) return ERROR_INCORRECT_TIME_FORMAT
        val source = Timesource.value(value[7].toInt())
        if (!isTimesourceValid(source)) return ERROR_TIMESOUCE_QUALITY_TOO_LOW
//        val minimumDateAllowed = 1640995200000 - UTC_TO_UNIX_EPOCH_MILLIS
        val ticks = value[1].toLong() +
                value[2].toLong().shl(8) +
                value[3].toLong().shl(16) +
                value[4].toLong().shl(24) +
                value[5].toLong().shl(32) +
                value[6].toLong().shl(40)
//        if (!writeFlags.hasFlag(TimestampFlags.isTickCounter) && (writeFlags.getTimeResolutionScaledValue(ticks) < minimumDateAllowed) ) {
//            return ERROR_OUT_OF_RANGE
//        }

        if (writeFlags.isTickCounter()) {
            TickCounter.setTickCounter(ticks)
        } else {
            Timber.i("Writing ETS Time Bytes: ${value.asFormattedHexString()}")
            TimeCounter.setTimeCounterWithETSBytes(value)
        }

        return GattStatus.SUCCESS
    }

    private fun isTimesourceValid(source: Timesource): Boolean {
        return source != Timesource.Manual
    }

    private fun writeFlagsValid(flags: BitMask) : Boolean {
        val myFlags = TimestampFlags.currentFlags
        return (flags.isTickCounter() == myFlags.isTickCounter()) &&
                (flags.hasFlag(TimestampFlags.isUTC) == myFlags.hasFlag(TimestampFlags.isUTC)) &&
                (flags.hasFlag(TimestampFlags.isTZPresent) == myFlags.hasFlag(TimestampFlags.isTZPresent)) &&
                (flags.hasFlag(TimestampFlags.isMilliseconds) == myFlags.hasFlag(TimestampFlags.isMilliseconds)) &&
                (flags.hasFlag(TimestampFlags.isHundredthsMilliseconds) == myFlags.hasFlag(TimestampFlags.isHundredthsMilliseconds))
    }

    /*
     * send the current clock in the GHS byte format based on current flags
     */
    fun notifyClockBytes(notify: Boolean = true) {
        val bytes = currentClockBytes()
        Timber.i("Sending ETS Bytes: ${bytes.asFormattedHexString()}")
        if (notify) {
            notifyCharacteristicChanged(bytes, simpleTimeCharacteristic)
            // Mark any disconnected bonded centrals as needing to be notified on connection.
            updateDisconnectedBondedCentralsToNotify(simpleTimeCharacteristic)
        }
    }

    fun currentClockBytes(): ByteArray {
        return if (TimestampFlags.currentFlags.isTickCounter()) TickCounter.asGHSBytes() else TimeCounter.asGHSBytes()
    }

    // Always wants the clock to be set
    private fun clockStatusBytes(): ByteArray { return byteArrayOf(0x1) }

    private fun clockCapabilitiesBytes(): ByteArray { return byteArrayOf(0) }

    init {
        initCharacteristic(simpleTimeCharacteristic, ELAPSED_TIME_DESCRIPTION)
        notifyClockBytes(false)
    }

    companion object {
        val ELAPSED_TIME_SERVICE_UUID = UUID.fromString("00007f3E-0000-1000-8000-00805f9b34fb")
        val ELASPED_TIME_CHARACTERISTIC_UUID = UUID.fromString("00007f3d-0000-1000-8000-00805f9b34fb")
        val CLOCK_STATUS_CHARACTERISTIC_UUID = UUID.fromString("00007f3c-0000-1000-8000-00805f9b34fb")
        val CLOCK_CAPABILITIES_CHARACTERISTIC_UUID = UUID.fromString("00007f3b-0000-1000-8000-00805f9b34fb")
        private const val ELAPSED_TIME_DESCRIPTION = "Elapsed Time Service Characteristic"

        private val ERROR_TIMESOUCE_QUALITY_TOO_LOW = GattStatus.fromValue(0x80)
        private val ERROR_INCORRECT_TIME_FORMAT = GattStatus.fromValue(0x81)
        private val ERROR_OUT_OF_RANGE = GattStatus.fromValue(0xFF)

        /**
         * If the [BluetoothServer] singleton has an instance of a GenericHealthSensorService return it (otherwise null)
         */
        fun getInstance(): ElapsedTimeService? {
            val bleServer = BluetoothServer.getInstance()
            val ghs = bleServer?.getServiceWithUUID(ELAPSED_TIME_SERVICE_UUID)
            return ghs?.let { it as ElapsedTimeService }
        }
    }
}

fun Byte.asBitmask(): BitMask {
    return BitMask(toLong())
}

fun BluetoothCentral.isBonded(): Boolean {
    return BluetoothServer.getInstance()?.isCentralBonded(this) ?: false
}

