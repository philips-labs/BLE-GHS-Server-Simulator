package com.philips.btserver.util

import android.os.SystemClock
import com.philips.btserver.extensions.*
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.nio.ByteOrder
import java.util.*

object TimeCounter {

    private var epoch2kMillis = System.currentTimeMillis() - UTC_TO_UNIX_EPOCH_MILLIS
    private var counterStartTimeMillis = System.currentTimeMillis()
    private var tzDstOffsetMillis = TimeZone.getDefault().getOffset(counterStartTimeMillis).toLong()

    private val currentEpoch2kMillis
        get() = epoch2kMillis + System.currentTimeMillis() - counterStartTimeMillis

    fun setToCurrentSystemTime() {
        epoch2kMillis = System.currentTimeMillis() - UTC_TO_UNIX_EPOCH_MILLIS
        counterStartTimeMillis = System.currentTimeMillis()
        tzDstOffsetMillis = TimeZone.getDefault().getOffset(counterStartTimeMillis).toLong()
    }

    fun setTimeCounterWithETSBytes(clockBytes: ByteArray) {
        val parser = BluetoothBytesParser(clockBytes)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8).toByte().asBitmask()
        val lowUint24Value = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT24).toLong()
        val highUint24Value = (parser.getIntValue(BluetoothBytesParser.FORMAT_UINT24).toLong().shl(24))
        val value =  lowUint24Value + highUint24Value

        val timesource = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT8)

        Timesource.currentSource = Timesource.value(timesource)

        val offsetUnits = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT8)
        val offsetMillis = if(!flags.hasFlag(TimestampFlags.isTZPresent)) 0 else offsetUnits * MILLIS_IN_15_MINUTES
        val milliScaledValue = flags.convertToTimeResolutionScaledMillisValue(value)
        Timber.i("setTimeCounterWithETSBytes value: $value scaled millis: $milliScaledValue offset millis: $offsetMillis source: $timesource")

        tzDstOffsetMillis = offsetMillis.toLong()
        epoch2kMillis = milliScaledValue
        counterStartTimeMillis = System.currentTimeMillis()
    }

    fun asGHSBytes(): ByteArray {
        return asGHSBytes(TimestampFlags.currentFlags)
    }

    fun asGHSBytes(timestampFlags: BitMask): ByteArray {

        var millis = currentEpoch2kMillis
        Timber.i("Current in Epoch Y2K: $currentEpoch2kMillis")

        // Used if the clock is reporting local time, not UTC time. Get UTC offset and add it to the milliseconds reported for local time
        millis += if (timestampFlags hasFlag TimestampFlags.isUTC) 0L else tzDstOffsetMillis
        val offsetUnits = tzDstOffsetMillis.toInt() / MILLIS_IN_15_MINUTES

        // Write the flags byte
        Timber.i("Timestamp Flags: ${timestampFlags.asTimestampFlagsString()}")
        Timber.i("Scaled Current Epoch Y2K: ${timestampFlags.getTimeResolutionScaledValue(millis)}")
        Timber.i("Timesource Value: ${Timesource.currentSource.value}")
        Timber.i("Offset Value: $offsetUnits")

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
