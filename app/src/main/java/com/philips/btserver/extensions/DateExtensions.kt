package com.philips.btserver.extensions

import android.os.SystemClock
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.nio.ByteOrder
import java.util.*

/*
 * Support for Flags in Kotlin (may want to move to a Flags.kt file, and a util package)
 */
class BitMask(val value: Long)

interface Flags {
    val bit: Long

    fun toBitMask(): BitMask = BitMask(bit)
}

infix fun Flags.and(other: Long): BitMask = BitMask(bit and other)
infix fun <T : Flags> Flags.or(other: T): BitMask = BitMask(bit or other.bit)

operator infix fun Flags.plus(other: Flags): BitMask = BitMask(bit or other.bit)

inline fun <reified T> enabledValues(mask: BitMask): List<T> where T : Enum<T>, T : Flags {
    return enumValues<T>().filter {
        mask hasFlag it
    }
}

infix fun BitMask.or(other: Flags): BitMask = BitMask(value or other.bit)

operator infix fun BitMask.plus(other: BitMask): BitMask = BitMask(value or other.value)
operator infix fun BitMask.plus(other: Flags): BitMask = BitMask(value or other.bit)

infix fun <T : Flags> BitMask.hasFlag(which: T): Boolean {
    // an Undefined flag is a special case.
    if (value == 0L || (value > 0L && which.bit == 0L)) return false

    return value and which.bit == which.bit
}

infix fun <T : Flags> BitMask.unset(which: T): BitMask = BitMask(value xor which.bit)
infix fun <T: Flags> BitMask.set(which: T): BitMask = BitMask(value or which.bit)

// End Flags support stuff

enum class TimestampFlags(override val bit: Long) : Flags {

    zero((0 shl 0).toLong()),
    isTickCounter((1 shl 0).toLong()),
    isUTC((1 shl 1).toLong()),
    isHundredthsMilliseconds((1 shl 2).toLong()),
    isMilliseconds((1 shl 3).toLong()),
    isTZPresent((1 shl 4).toLong()),
    isCurrentTimeline((1 shl 5).toLong()),
    reserved_1((1 shl 6).toLong()),
    reserved_2((1 shl 7).toLong());

    companion object {
        // This "global" holds the flags used to send out observations
        var currentFlags: BitMask = BitMask(TimestampFlags.isMilliseconds.bit)
            .plus(TimestampFlags.isTZPresent)
            .plus(TimestampFlags.isCurrentTimeline)


        fun setLocalFlags() {
            currentFlags = currentFlags.unset(isUTC)
            currentFlags = currentFlags.unset(isTZPresent)
            currentFlags = currentFlags.unset(isTickCounter)
        }

        fun setLocalWithOffsetFlags() {
            currentFlags = currentFlags.unset(isUTC)
            currentFlags = currentFlags.set(isTZPresent)
            currentFlags = currentFlags.unset(isTickCounter)
        }

        fun setUtcOnlyFlags() {
            currentFlags = currentFlags.set(isUTC)
            currentFlags = currentFlags.unset(isTZPresent)
            currentFlags = currentFlags.unset(isTickCounter)
        }

        fun setUtcWithOffsetFlags() {
            currentFlags = currentFlags.set(isUTC)
            currentFlags = currentFlags.set(isTZPresent)
            currentFlags = currentFlags.unset(isTickCounter)
        }

        fun setTickCounterFlags() {
            currentFlags = currentFlags.unset(isUTC)
            currentFlags = currentFlags.unset(isTZPresent)
            currentFlags = currentFlags.set(isTickCounter)
        }

    }
}

fun BitMask.convertToTimeResolutionScaledMillisValue(value: Long): Long {
    return if (isSeconds()) value * 1000L
    else if (isMilliseconds()) value
    else if(isHundredMilliseconds()) value * 100L
    else if(isHundredthsMicroseconds()) value / 10L
    else value
}

fun BitMask.getTimeResolutionScaledValue(millis: Long): Long {
    return if (isSeconds()) millis / 1000L
    else if (isMilliseconds()) millis
    else if(isHundredMilliseconds()) millis / 10L
    else if(isHundredthsMicroseconds()) millis * 10L
    else millis
}

fun BitMask.asTimestampFlagsString(): String {
    val ticksOrTime = if (this hasFlag TimestampFlags.isTickCounter) "Ticks" else "Time"
    val utcOrLocal = if (this hasFlag TimestampFlags.isUTC) "UTC" else "Local"
    val millsOrSecs = if (this hasFlag TimestampFlags.isMilliseconds) "Millis" else "Seconds"
    val hasTZ = if (this hasFlag TimestampFlags.isTZPresent) "TZ" else "No TZ"
    val current = if (this hasFlag TimestampFlags.isCurrentTimeline) "Current" else "Not Current"
    return "Value: ${value.toByte().asHexString()} : $ticksOrTime : $utcOrLocal : $millsOrSecs : $hasTZ : $current timeline"
}


enum class Timesource(val value: Int) {
    Unknown(0),
    NTP(1),
    GPS(2),
    RadioTimeSignal(3),
    Manual(4),
    Atomic(5),
    CellularNetwork(6),
    None(7);

    override fun toString(): String {
        return when (value) {
            Unknown.value -> "Unknown"
            NTP.value -> "NTP"
            GPS.value -> "GPS"
            RadioTimeSignal.value -> "Radio Time Signal"
            Manual.value -> "Manual"
            Atomic.value -> "Atomic Clock"
            CellularNetwork.value -> "Cellular Network"
            None.value -> "None"
            else -> "Undefined"
        }
    }

    companion object {
        // This "global" holds the current time source used to send out observations
        var currentSource: Timesource = CellularNetwork

        fun value(value: Int): Timesource {
            return when (value) {
                Unknown.value -> Unknown
                NTP.value -> NTP
                GPS.value -> GPS
                RadioTimeSignal.value -> RadioTimeSignal
                Manual.value -> Manual
                Atomic.value -> Atomic
                CellularNetwork.value -> CellularNetwork
                None.value -> None
                else -> Unknown
            }
        }
    }

}

/*
 * Create a binary representation of the receiver based on the timestamp flags set in TimestampFlags.currentFlags
 * The flags and bytes are in the GHS specification in section 2.5.3.2 (as of the 0.5 draft)
 *
 * @returns bytes that are compliant with the GHS Time specification
 */
fun Date.asGHSBytes(): ByteArray {
    return asGHSBytes(TimestampFlags.currentFlags)
}

/*
 * Create a binary representation of the receiver based on the timestamp flags passed in.
 * The flags and bytes are in the GHS specification in section 2.5.3.2 (as of the 0.5 draft)
 *
 * @returns bytes that are compliant with the GHS Time specification
 */

// Magic number 946684800000 is the millisecond offset from 1970 Epoch to Y2K Epoch
const val UTC_TO_UNIX_EPOCH_MILLIS = 946684800000L
const val MILLIS_IN_15_MINUTES = 900000

/*
 * Return the Epoch Y2K milliseconds (used by GHS)
 */
fun Date.epoch2000mills(): Long {
    return time - UTC_TO_UNIX_EPOCH_MILLIS
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
        val timeZoneMillis = if (timestampFlags hasFlag TimestampFlags.isTZPresent) calendar.get(Calendar.ZONE_OFFSET) else 0
        val dstMillis =if (timestampFlags hasFlag TimestampFlags.isTZPresent) calendar.get(Calendar.DST_OFFSET) else 0
        offsetUnits = (timeZoneMillis + dstMillis) / MILLIS_IN_15_MINUTES
        Timber.i("Add Offset Value: $offsetUnits")

        parser.setIntValue(offsetUnits, BluetoothBytesParser.FORMAT_SINT8)
    }

    return listOf(
        byteArrayOf(timestampFlags.value.toByte()),
        millis.asUInt48ByteArray(),
        byteArrayOf(Timesource.currentSource.value.toByte(), offsetUnits.toByte())
    ).merge()

}

fun Long.asUInt48ByteArray(): ByteArray {
    val millParser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
    millParser.setLong(this)
    return millParser.value.copyOfRange(0, 6)
}

// Return true if current TimestampFlags (a BitMask) indicates  a timestamp value is sent, false if a tick counter
fun BitMask.isTimestampSent(): Boolean {
    return !hasFlag(TimestampFlags.isTickCounter)
}

fun BitMask.isMilliseconds(): Boolean {
    return (this hasFlag TimestampFlags.isMilliseconds) and !(this hasFlag TimestampFlags.isHundredthsMilliseconds)
}

fun BitMask.isHundredMilliseconds(): Boolean {
    return !(this hasFlag TimestampFlags.isMilliseconds) and (this hasFlag TimestampFlags.isHundredthsMilliseconds)
}

fun BitMask.isSeconds(): Boolean {
    return !(this hasFlag TimestampFlags.isMilliseconds) and !(this hasFlag TimestampFlags.isHundredthsMilliseconds)
}

fun BitMask.isHundredthsMicroseconds(): Boolean {
    return !(this hasFlag TimestampFlags.isMilliseconds) and !(this hasFlag TimestampFlags.isMilliseconds)
}
