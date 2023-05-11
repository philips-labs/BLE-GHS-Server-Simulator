package com.philips.btserver.util

import android.os.SystemClock
import com.philips.btserver.extensions.*
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.util.*

interface TimeSourceListener {
    fun onTimeSourceChanged()
}

object TimeSource {

    private var clockStartUTCMillis = System.currentTimeMillis()
    private var tickStartMillis = SystemClock.elapsedRealtime()
    private var baseTickMillis = 0L

    val currentTickCounter get() = SystemClock.elapsedRealtime() - tickStartMillis + baseTickMillis
    val currentUTCMillis get() = clockStartUTCMillis + currentTickCounter
    val currentEpoch2kMillis get() = currentUTCMillis - UTC_TO_UNIX_EPOCH_MILLIS
    var tzDstOffsetMillis = TimeZone.getDefault().getOffset(clockStartUTCMillis).toLong()
    val currentDate get() = Date(currentUTCMillis)

    private val listeners = mutableListOf<TimeSourceListener>()

    fun addListener(listener: TimeSourceListener) = listeners.add(listener)
    fun removeListener(listener: TimeSourceListener) = listeners.remove(listener)
    private fun broadcastChange() = listeners.forEach { it.onTimeSourceChanged() }

    // Adjust (offset) the current time/ticks by the number of milliseconds passed in
    fun adjustTimeSourceMillis(deltaMillis: Int) {
        baseTickMillis += deltaMillis
        broadcastChange()
    }

    fun setToCurrentSystemTime() {
        clockStartUTCMillis = System.currentTimeMillis()
        tickStartMillis = SystemClock.elapsedRealtime()
        tzDstOffsetMillis = TimeZone.getDefault().getOffset(clockStartUTCMillis).toLong()
        broadcastChange()
    }

    fun resetTickCounter(baseCountMillis: Long = 0L) {
        clockStartUTCMillis = System.currentTimeMillis()
        tickStartMillis = SystemClock.elapsedRealtime()
        baseTickMillis = baseCountMillis
    }

    fun setTimeSourceWithETSBytes(clockBytes: ByteArray) {
        val parser = BluetoothBytesParser(clockBytes)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8).toByte().asBitmask()
        if (flags.isTickCounter()) setTickCounterWithBytes(clockBytes) else setTimeWithBytes(clockBytes)
    }

    private fun setTickCounterWithBytes(clockBytes: ByteArray) {
        val parser = BluetoothBytesParser(clockBytes)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8).toByte().asBitmask()
        val startTickMillis = flags.convertToTimeResolutionScaledMillisValue(parser.uInt48)
        resetTickCounter(startTickMillis)
        Timber.i("Set tick millis: $startTickMillis offset")
    }

    private fun setTimeWithBytes(clockBytes: ByteArray) {
        val parser = BluetoothBytesParser(clockBytes)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8).toByte().asBitmask()
        val timeMillis = flags.convertToTimeResolutionScaledMillisValue(parser.uInt48)
        Timesource.currentSource = Timesource.value(parser.getIntValue(BluetoothBytesParser.FORMAT_SINT8))

//        val timesource = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT8)
//        Timesource.currentSource = Timesource.value(timesource)

        val offsetUnits = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT8)
        val offsetMillis = if(!flags.hasFlag(TimestampFlags.isTZPresent)) 0 else offsetUnits * MILLIS_IN_15_MINUTES

        Timber.i("Set time millis: $timeMillis offset millis: $tzDstOffsetMillis source: ${Timesource.currentSource}")

        clockStartUTCMillis = System.currentTimeMillis()
        tickStartMillis = SystemClock.elapsedRealtime()
        tzDstOffsetMillis = offsetMillis.toLong()
        broadcastChange()
    }


    fun asGHSBytes(): ByteArray {
        return asGHSBytes(TimestampFlags.currentFlags)
    }

    fun asGHSBytes(timestampFlags: BitMask): ByteArray {
        val millis = if(timestampFlags.isTickCounter()) {
            Timber.i("asGHSBytes current tick millis: ${currentEpoch2kMillis}")
            currentTickCounter
        } else {
            Timber.i("asGHSBytes current millis in Epoch Y2K: ${currentEpoch2kMillis}")
            currentEpoch2kMillis + if (timestampFlags hasFlag TimestampFlags.isUTC) 0L else tzDstOffsetMillis
        }

        val offsetUnits = if(timestampFlags hasFlag TimestampFlags.isTZPresent) tzDstOffsetMillis.toInt() / MILLIS_IN_15_MINUTES else 0

        Timber.i("Timestamp Flags: ${timestampFlags.asTimestampFlagsString()}")
        Timber.i("Scaled Current Epoch Y2K: ${timestampFlags.getTimeResolutionScaledValue(millis)}")
        Timber.i("Timesource Value: ${Timesource.currentSource.value}")
        Timber.i("Offset Value: $offsetUnits")

        val tc = timestampFlags.getTimeResolutionScaledValue(millis).asUInt48ByteArray()

        return listOf(
            byteArrayOf(timestampFlags.value.toByte()),
            tc,
            //millis.asUInt48ByteArray(),
            byteArrayOf(Timesource.currentSource.value.toByte(), offsetUnits.toByte())
        ).merge()
    }
}

fun Byte.asBitmask(): BitMask {
    return BitMask(toLong())
}