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

// End Flags support stuff

enum class TimestampFlags(override val bit: Long) : Flags {

    isTickCounter((1 shl 0).toLong()),
    isUTC((1 shl 1).toLong()),
    isMilliseconds((1 shl 2).toLong()),
    isHundredthsMicroseconds(1 shl 3),
    isTZPresent(1 shl 4),
    isDSTPresent(1 shl 5),
    isCurrentTimeline(1 shl 6),
    reserved_1(1 shl 7);

    companion object {
        // This "global" holds the flags used to send out observations
        var currentFlags: BitMask = BitMask(TimestampFlags.isMilliseconds.bit)
            .plus(TimestampFlags.isTZPresent)
            .plus(TimestampFlags.isDSTPresent)
            .plus(TimestampFlags.isCurrentTimeline)
    }
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
 * This will assume (and return) a byte array based on UTC milliseconds and current (valid) time clock (0x62 time flags)
 */
fun Date.asFixedTimestampByteArray(): ByteArray {
    val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
    parser.setLong(time)
    return listOf(
        byteArrayOf(0x62),
        parser.value
    ).merge()

}

/*
 * Create a binary representation of the receiver based on the timestamp flags passed in.
 * The flags and bytes are in the GHS specification in section 2.5.3.2 (as of the 0.5 draft)
 *
 * @returns bytes that are compliant with the GHS Time specification
 */

// Magic number 946684800000 is the millisecond offset from 1970 Epoch to Y2K Epoch
private const val UTC_TO_UNIX_EPOCH_MILLIS = 946684800000L
private const val MILLIS_IN_15_MINUTES = 900000

fun Date.asGHSBytes(timestampFlags: BitMask): ByteArray {

    val currentTimeMillis = System.currentTimeMillis()
    val isTickCounter = timestampFlags hasFlag TimestampFlags.isTickCounter

    var millis = if (isTickCounter) SystemClock.elapsedRealtime() else currentTimeMillis - UTC_TO_UNIX_EPOCH_MILLIS

    if (!isTickCounter) {
        // Used if the clock is reporting local time, not UTC time. Get UTC offset and add it to the milliseconds reported for local time
        val utcOffsetMillis = if (timestampFlags hasFlag TimestampFlags.isUTC) 0L else TimeZone.getDefault().getOffset(currentTimeMillis).toLong()
        millis += utcOffsetMillis
    }


    val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
    // Write the flags byte
    parser.setIntValue(timestampFlags.value.toInt(), BluetoothBytesParser.FORMAT_UINT8)
    Timber.i("Add Flag Byte: ${timestampFlags.value.toInt()}")

    // Write the utc/local/tick clock value (either milliseconds or seconds)
    if (timestampFlags.hasFlag(TimestampFlags.isMilliseconds)) {
        parser.setLongValue(millis)
        Timber.i("Add Milliseconds Value: $millis")
    } else {
        parser.setLongValue(millis / 1000L)
        Timber.i("Add Seconds Value: ${millis / 1000L}")
    }

    if (!isTickCounter) {
        // If a timestamp include the time sync source (NTP, GPS, Network, etc)
        parser.setIntValue(Timesource.currentSource.value, BluetoothBytesParser.FORMAT_UINT8)
        Timber.i("Add Timesource Value: ${Timesource.currentSource.value}")
        val localCalendar: Calendar = Calendar.getInstance(TimeZone.getDefault())
        val timeZoneMillis = if (timestampFlags hasFlag TimestampFlags.isTZPresent) localCalendar.get(Calendar.ZONE_OFFSET) else 0
        val dstMillis =if (timestampFlags hasFlag TimestampFlags.isDSTPresent) localCalendar.get(Calendar.DST_OFFSET) else 0
        val offsetUnits = (timeZoneMillis + dstMillis) / MILLIS_IN_15_MINUTES
        parser.setIntValue(offsetUnits, BluetoothBytesParser.FORMAT_SINT8)
        Timber.i("Add Offset Value: $offsetUnits")
    }

    return parser.value
}

fun Date.testLogOffsets() {
    val tz = TimeZone.getDefault()
    val cal1: Calendar = Calendar.getInstance(tz) //currentZone: CET/CEST +1/+2, GMT+1:00
    Timber.i("System time ${System.currentTimeMillis()}")
    Timber.i("Calendar time ${cal1.getTime().getTime()}")
    Timber.i("Calendar millis ${cal1.getTimeInMillis()}")
    Timber.i("Calendar Zone Offset: ${cal1.get(Calendar.ZONE_OFFSET)}")
    Timber.i("Calendar DST Offset: ${cal1.get(Calendar.DST_OFFSET)}")
    Timber.i("Time Zone Raw Offset: ${tz.getRawOffset()}")
    Timber.i("Time Zone DST Savings Offset: ${tz.getDSTSavings()}")
    Timber.i("Time Zone millis: ${tz.getOffset(System.currentTimeMillis())}")
    Timber.i("Is Time Zone in DST: ${tz.inDaylightTime(this)}")
}

// Return true if current TimestampFlags (a BitMask) indicates  a timestamp value is sent, false if a tick counter
fun BitMask.isTimestampSent(): Boolean {
    return !hasFlag(TimestampFlags.isTickCounter)
}


// Return true if current TimestampFlags (a BitMask) indicates  a timestamp value is sent, false if a tick counter
fun BitMask.isTimestampFlagSet(flag: TimestampFlags): Boolean {
    return TimestampFlags.currentFlags hasFlag flag
}