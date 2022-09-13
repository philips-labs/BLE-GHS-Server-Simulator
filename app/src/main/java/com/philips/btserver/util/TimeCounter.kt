package com.philips.btserver.util

import android.os.SystemClock
import com.philips.btserver.extensions.*
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.nio.ByteOrder
import java.util.*

object TimeCounter {

    private var tzDstOffsetMillis = 0L
    private var epoch2kMillis = 0L
    private var currentTimeMillis = 0L

    private val currentEpoch2kMillis
        get() = epoch2kMillis + System.currentTimeMillis() - currentTimeMillis

    fun setToCurrentSystemTime() {
        epoch2kMillis = System.currentTimeMillis() - UTC_TO_UNIX_EPOCH_MILLIS
        currentTimeMillis = System.currentTimeMillis()
        tzDstOffsetMillis = TimeZone.getDefault().getOffset(currentTimeMillis).toLong()
    }

    fun setTimeCounterWithETSBytes(clockBytes: ByteArray) {
        val parser = BluetoothBytesParser(clockBytes)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8).toByte().asBitmask()
        val lowUint24Value = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT24).toLong()
        val value =  lowUint24Value + (parser.getIntValue(BluetoothBytesParser.FORMAT_UINT24).toLong().shl(24))

        val offsetUnits = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT8)
        val offsetMillis = if(flags.hasFlag(TimestampFlags.isTZPresent)) 0 else offsetUnits * MILLIS_IN_15_MINUTES
        val milliScaledValue = flags.convertToTimeResolutionScaledMillisValue(value)
        Timber.i("setTimeCounterWithETSBytes value: $value scaled millis: $milliScaledValue offset millis: $offsetMillis")

        tzDstOffsetMillis = offsetMillis.toLong()
        epoch2kMillis = milliScaledValue
        currentTimeMillis = System.currentTimeMillis()
    }

    fun asGHSBytes(): ByteArray {
        return asGHSBytes(TimestampFlags.currentFlags)
    }

    fun asGHSBytes(timestampFlags: BitMask): ByteArray {

        // Get the current
        var millis = currentEpoch2kMillis
        Timber.i("Epoch Y2K: $currentEpoch2kMillis")

        // Used if the clock is reporting local time, not UTC time. Get UTC offset and add it to the milliseconds reported for local time
        millis += if (timestampFlags hasFlag TimestampFlags.isUTC) 0L else tzDstOffsetMillis

        val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)

        // Write the flags byte
        parser.setIntValue(timestampFlags.value.toInt(), BluetoothBytesParser.FORMAT_UINT8)
        Timber.i("Add Flag: ${timestampFlags.asTimestampFlagsString()}")

        // Write the utc/local/tick clock value in the time resolution units
        parser.setLong(timestampFlags.getTimeResolutionScaledValue(millis))

        // If a timestamp include the time sync source (NTP, GPS, Network, etc)
        parser.setIntValue(Timesource.currentSource.value, BluetoothBytesParser.FORMAT_UINT8)
        Timber.i("Add Timesource Value: ${Timesource.currentSource.value}")

        val offsetUnits = tzDstOffsetMillis.toInt() / MILLIS_IN_15_MINUTES
        Timber.i("Add Offset Value: $offsetUnits")
        parser.setIntValue(offsetUnits, BluetoothBytesParser.FORMAT_SINT8)

        return listOf(
            byteArrayOf(timestampFlags.value.toByte()),
            millis.asUInt48ByteArray(),
            byteArrayOf(Timesource.currentSource.value.toByte(), offsetUnits.toByte())
        ).merge()

    }

}


fun Byte.asBitmask(): BitMask {
    return BitMask(toLong())
}
