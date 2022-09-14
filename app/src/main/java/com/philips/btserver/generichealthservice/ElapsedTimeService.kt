package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothAdapter
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
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import timber.log.Timber
import java.util.*

internal class ElapsedTimeService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(ELAPSED_TIME_SERVICE_UUID, SERVICE_TYPE_PRIMARY)

    private val disconnectedBondedCentrals = mutableSetOf<String>()
    private val bondedCentralsToNotify = mutableSetOf<String>()

    private val simpleTimeCharacteristic = BluetoothGattCharacteristic(
        ELASPED_TIME_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_READ or PERMISSION_WRITE
    )

    private fun hasBondedCentralReconnected(central: BluetoothCentral): Boolean {
        return disconnectedBondedCentrals.contains(central.address)
    }

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
        if(hasBondedCentralReconnected(central)) bondedReconnected(central)
    }

    private fun bondedReconnected(central: BluetoothCentral) {
        Timber.i("Reconnecting bonded central: $central")
        disconnectedBondedCentrals.remove(central.address)
        if (bondedCentralsToNotify.contains(central.address)) {
            notifyReconnectedBondedCentral(central)
            bondedCentralsToNotify.remove(central.address)
        }
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        super.onCentralDisconnected(central)
        if(central.isBonded()) {
            Timber.i("Disconnecting bonded central: $central")
            disconnectedBondedCentrals.add(central.address)
        }
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
    fun sendClockBytes(notify: Boolean = true) {
        val bytes = listOf(currentTimeBytes(), clockStatusBytes(), clockCapabilitiesBytes()).merge()
        simpleTimeCharacteristic.value = bytes
        if (notify) {
            notifyCharacteristicChanged(bytes, simpleTimeCharacteristic)
            // Mark any disconnected bonded centrals as needing to be notified on connection.
            bondedCentralsToNotify.addAll(disconnectedBondedCentrals)
        }
    }

    private fun notifyReconnectedBondedCentral(central: BluetoothCentral) {
        // TODO This should only notify the central passed in, but doing that gets buried in BluetoothPerpheralManager>>notifyCharacteristicChanged
        notifyCharacteristicChanged(simpleTimeCharacteristic.value, simpleTimeCharacteristic)
    }

    private fun currentTimeBytes(): ByteArray {
        return TimeCounter.asGHSBytes()
    }

    // Always wants the clock to be set
    private fun clockStatusBytes(): ByteArray { return byteArrayOf(0x1) }

    private fun clockCapabilitiesBytes(): ByteArray { return byteArrayOf(0) }

    init {
        service.addCharacteristic(simpleTimeCharacteristic)
        simpleTimeCharacteristic.addDescriptor(getCccDescriptor())
        simpleTimeCharacteristic.addDescriptor(getCudDescriptor(SIMPLE_TIME_DESCRIPTION))
        sendClockBytes(false)
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

fun BluetoothCentral.isBonded(): Boolean {
    return BluetoothServer.getInstance()?.isCentralBonded(this) ?: false
}

