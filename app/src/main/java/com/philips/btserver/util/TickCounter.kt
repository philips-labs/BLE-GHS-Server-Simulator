package com.philips.btserver.util

import android.os.SystemClock
import com.philips.btserver.extensions.TimestampFlags
import com.philips.btserver.extensions.hasFlag

object TickCounter {

    private var tickCounterOffset = 0L

    fun currentTickCounter(): Long {
        val isMilliseconds = TimestampFlags.currentFlags.hasFlag(TimestampFlags.isMilliseconds)
        return currentTicks() / if (isMilliseconds) 1L else 1000L
    }

    private fun systemTicks(): Long {
        return SystemClock.elapsedRealtime()
    }

    private fun currentTicks(): Long {
        return systemTicks() - tickCounterOffset
    }

    fun setTickCounter(ticks: Long) {
        tickCounterOffset = systemTicks() - ticks
    }

}