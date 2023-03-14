package com.philips.btserver.util

import com.philips.btserver.extensions.*
import com.philips.btserver.generichealthservice.ElapsedTimeService
import com.philips.btserver.observations.ObservationStore
import com.philips.btserver.observations.ObservationStoreListener
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.util.*


interface TimeCounterListener {
    fun onTimeCounterChanged()
}

object TimeCounter {

    private var epoch2kMillis = System.currentTimeMillis() - UTC_TO_UNIX_EPOCH_MILLIS
    private var counterStartTimeMillis = System.currentTimeMillis()
    private var tzDstOffsetMillis = TimeZone.getDefault().getOffset(counterStartTimeMillis).toLong()
    private var deltaTimeMillis = 0

    private val currentEpoch2kMillis
        get() = epoch2kMillis + System.currentTimeMillis() - counterStartTimeMillis

    private val listeners = mutableListOf<TimeCounterListener>()

    fun addListener(listener: TimeCounterListener) = listeners.add(listener)
    fun removeListener(listener: TimeCounterListener) = listeners.remove(listener)
    private fun broadcastChange() = listeners.forEach { it.onTimeCounterChanged() }

    fun setToCurrentSystemTime() {
        epoch2kMillis = System.currentTimeMillis() - UTC_TO_UNIX_EPOCH_MILLIS
        counterStartTimeMillis = System.currentTimeMillis()
        deltaTimeMillis = 0
        tzDstOffsetMillis = TimeZone.getDefault().getOffset(counterStartTimeMillis).toLong()
        broadcastChange()
    }

    fun addToCurrentSystemTime(milliseconds: Int) {
        deltaTimeMillis += milliseconds
        broadcastChange()
    }

    fun asDate(): Date {
        return Date(System.currentTimeMillis() + deltaTimeMillis)
    }

    fun setTimeCounterWithETSBytes(clockBytes: ByteArray) {
        val parser = BluetoothBytesParser(clockBytes)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8).toByte().asBitmask()
        val value = parser.uInt48
        val timesource = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT8)

        Timesource.currentSource = Timesource.value(timesource)

        val offsetUnits = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT8)
        val offsetMillis = if(!flags.hasFlag(TimestampFlags.isTZPresent)) 0 else offsetUnits * MILLIS_IN_15_MINUTES
        val milliScaledValue = flags.convertToTimeResolutionScaledMillisValue(value)
        Timber.i("setTimeCounterWithETSBytes value: $value scaled millis: $milliScaledValue offset millis: $offsetMillis source: $timesource")

        tzDstOffsetMillis = offsetMillis.toLong()
        epoch2kMillis = milliScaledValue
        counterStartTimeMillis = System.currentTimeMillis()

        broadcastChange()
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

    init {
        setToCurrentSystemTime()
    }
}


fun Byte.asBitmask(): BitMask {
    return BitMask(toLong())
}
