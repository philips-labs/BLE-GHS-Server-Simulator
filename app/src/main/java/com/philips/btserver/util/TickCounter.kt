package com.philips.btserver.util

import android.os.SystemClock
import com.philips.btserver.extensions.*
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.nio.ByteOrder

object TickCounter {

    private var tickCounterOffset = 0L

    fun currentTickCounter(): Long {
        return TimestampFlags.currentFlags.getTimeResolutionScaledValue(currentTicks())
    }

    private fun systemTicks(): Long {
        return SystemClock.elapsedRealtime()
    }

    private fun currentTicks(): Long {
        return systemTicks() - tickCounterOffset
    }

    fun setTickCounter(ticks: Long) {
        val milliTicks = TimestampFlags.currentFlags.convertToTimeResolutionScaledMillisValue(ticks)
        tickCounterOffset = systemTicks() - milliTicks
    }

    fun asGHSBytes(): ByteArray {
        return asGHSBytes(TimestampFlags.currentFlags)
    }

    fun asGHSBytes(timestampFlags: BitMask): ByteArray {

        val millis = currentTickCounter()
        // Get the current
        Timber.i("Ticks: $millis Scaled Ticks: ${timestampFlags.getTimeResolutionScaledValue(millis)}")

        return listOf(
            byteArrayOf(timestampFlags.value.toByte()),
            millis.asUInt48ByteArray(),
            byteArrayOf(Timesource.None.value.toByte(), 0.toByte())
        ).merge()

    }

}