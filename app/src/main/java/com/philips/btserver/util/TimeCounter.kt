package com.philips.btserver.util

import android.os.SystemClock
import com.philips.btserver.extensions.*
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.nio.ByteOrder
import java.util.*

object TimeCounter {

    fun setTimeCounterWithETSBytes(clockBytes: ByteArray) {
        val parser = BluetoothBytesParser(clockBytes)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8).toByte().asBitmask()
        val value = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT24).toLong()
                        + parser.getIntValue(BluetoothBytesParser.FORMAT_UINT24).toLong() shl(24)
    }

}

fun Date.asGHSBytes(timestampFlags: BitMask): ByteArray {

    val currentTimeMillis = System.currentTimeMillis()
    val isTickCounter = timestampFlags hasFlag TimestampFlags.isTickCounter

    var millis = if (isTickCounter) SystemClock.elapsedRealtime() else epoch2000mills()
    Timber.i("Epoch millis Value: Unix: ${millis + UTC_TO_UNIX_EPOCH_MILLIS} Y2K: $millis")

    if (!isTickCounter) {
        // Used if the clock is reporting local time, not UTC time. Get UTC offset and add it to the milliseconds reported for local time
        val utcOffsetMillis = if (timestampFlags hasFlag TimestampFlags.isUTC) 0L else TimeZone.getDefault().getOffset(currentTimeMillis).toLong()
        millis += utcOffsetMillis
    }


    val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)

    // Write the flags byte
    parser.setIntValue(timestampFlags.value.toInt(), BluetoothBytesParser.FORMAT_UINT8)
    Timber.i("Add Flag: ${timestampFlags.asTimestampFlagsString()}")

    // Write the utc/local/tick clock value (either milliseconds or seconds)
    if (timestampFlags.hasFlag(TimestampFlags.isMilliseconds)) {
        parser.setLong(millis)
        Timber.i("Add Milliseconds Value: $millis")
    } else {
        parser.setLong(millis / 1000L)
        Timber.i("Add Seconds Value: ${millis / 1000L}")
    }

    var offsetUnits = 0

    if (!isTickCounter) {
        // If a timestamp include the time sync source (NTP, GPS, Network, etc)
        parser.setIntValue(Timesource.currentSource.value, BluetoothBytesParser.FORMAT_UINT8)
        Timber.i("Add Timesource Value: ${Timesource.currentSource.value}")

        val calendar = Calendar.getInstance(Locale.getDefault());
        val timeZoneMillis = if (timestampFlags hasFlag TimestampFlags.isTZPresent) calendar.get(
            Calendar.ZONE_OFFSET) else 0
        val dstMillis =if (timestampFlags hasFlag TimestampFlags.isTZPresent) calendar.get(Calendar.DST_OFFSET) else 0
        offsetUnits = (timeZoneMillis + dstMillis) / MILLIS_IN_15_MINUTES
        Timber.i("Add Offset Value: $offsetUnits")

        parser.setIntValue(offsetUnits, BluetoothBytesParser.FORMAT_SINT8)
    }

//    return parser.value

    val millParser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
    millParser.setLong(millis)

    return listOf(
        byteArrayOf(timestampFlags.value.toByte()),
        millParser.value.copyOfRange(0, 6),
        byteArrayOf(Timesource.currentSource.value.toByte(), offsetUnits.toByte())
    ).merge()

}

fun Byte.asBitmask(): BitMask {
    return BitMask(toLong())
}
