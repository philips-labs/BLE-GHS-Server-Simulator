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
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import timber.log.Timber
import java.util.*

internal class ElapsedTimeService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(ELAPSED_TIME_SERVICE_UUID, SERVICE_TYPE_PRIMARY)

    private val simpleTimeCharacteristic = BluetoothGattCharacteristic(
        ELASPED_TIME_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_READ or PERMISSION_WRITE
    )

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
        sendClockBytes()
    }

    /**
     * Notification from [central] that [characteristic] has notification enabled. Implies that
     * there is a connection so start emitting observations.
     */
    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        sendClockBytes()
    }

    /*
     * onCharacteristicRead is a non-abstract method with an empty body to have a default behavior to do nothing
     */
    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (characteristic.uuid == ELASPED_TIME_CHARACTERISTIC_UUID) {
            sendClockBytes(notify = false)
        }
    }


    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        Timber.i("onCharacteristicWrite with Bytes: ${value.asFormattedHexString()}")
        return writeSTSBytes(value)
    }

    override fun onCharacteristicWriteCompleted(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        sendClockBytes(notify = true)
        Timber.i("onCharacteristicWriteCompleted")
    }

    private fun writeSTSBytes(value: ByteArray) : GattStatus {
        val writeFlags = value.first().asBitmask()
        if (!writeFlagsValid(writeFlags)) return GattStatus.INTERNAL_ERROR
        if (writeFlags.hasFlag(TimestampFlags.isTickCounter)) {
            val ticks = value[1].toLong() +
                    value[2].toLong().shl(8) +
                    value[3].toLong().shl(16) +
                    value[4].toLong().shl(24) +
                    value[5].toLong().shl(32) +
                    value[6].toLong().shl(40)
            TickCounter.setTickCounter(ticks)
        } else {
            Timber.i("Writing ETS Time Bytes: ${value.asFormattedHexString()}")
            TimeCounter.setTimeCounterWithETSBytes(value)
        }

        return GattStatus.SUCCESS
    }

    private fun writeFlagsValid(flags: BitMask) : Boolean {
        val myFlags = TimestampFlags.currentFlags
        return (flags.hasFlag(TimestampFlags.isTickCounter) == myFlags.hasFlag(TimestampFlags.isTickCounter)) &&
                (flags.hasFlag(TimestampFlags.isUTC) == myFlags.hasFlag(TimestampFlags.isUTC)) &&
                (flags.hasFlag(TimestampFlags.isTZPresent) == myFlags.hasFlag(TimestampFlags.isTZPresent)) &&
                (flags.hasFlag(TimestampFlags.isMilliseconds) == myFlags.hasFlag(TimestampFlags.isMilliseconds)) &&
                (flags.hasFlag(TimestampFlags.isHundredthsMilliseconds) == myFlags.hasFlag(TimestampFlags.isHundredthsMilliseconds))
    }

    /*
     * send the current clock in the GHS byte format based on current flags
     */
    private fun sendClockBytes(notify: Boolean = true) {
        val bytes = listOf(currentTimeBytes(), clockStatusBytes(), clockCapabilitiesBytes()).merge()
        // TODO Add the Clock status and clock capailities flags for real
        simpleTimeCharacteristic.value = bytes
        if (notify) notifyCharacteristicChanged(bytes, simpleTimeCharacteristic)
    }

    private fun currentTimeBytes(): ByteArray {
        return TimeCounter.asGHSBytes()
    }

    private fun clockStatusBytes(): ByteArray {
        return byteArrayOf(0x1)
    }

    private fun clockCapabilitiesBytes(): ByteArray {
        val clockFlags = TimestampFlags.currentFlags
        return byteArrayOf(if (clockFlags.hasFlag(TimestampFlags.isTZPresent)) 0x6 else 0)
    }

    init {
        initCharacteristic(simpleTimeCharacteristic, SIMPLE_TIME_DESCRIPTION)
    }

    // TODO This is a dupe of the method in SimpleTimeService... let's make this an extension!
    private fun initCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        description: String
    ) {
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(getCccDescriptor())
        characteristic.addDescriptor(getCudDescriptor(description))
        characteristic.value = byteArrayOf(0x00)
    }

    companion object {
        val ELAPSED_TIME_SERVICE_UUID = UUID.fromString("00007f3E-0000-1000-8000-00805f9b34fb")
        val ELASPED_TIME_CHARACTERISTIC_UUID =
            UUID.fromString("00007f3d-0000-1000-8000-00805f9b34fb")
        private const val SIMPLE_TIME_DESCRIPTION = "Simple Time Service Characteristic"

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