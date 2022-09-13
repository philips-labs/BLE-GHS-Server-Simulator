package com.philips.btserver.util

import android.os.SystemClock
import com.philips.btserver.extensions.TimestampFlags
import com.philips.btserver.extensions.convertToTimeResolutionScaledMillisValue
import com.philips.btserver.extensions.hasFlag

object TickCounter {

    private var tickCounterOffset = 0L

    fun currentTickCounter(): Long {
        return TimestampFlags.currentFlags.convertToTimeResolutionScaledMillisValue(currentTicks())
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

}