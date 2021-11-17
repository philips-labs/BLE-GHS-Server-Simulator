package com.philips.btserver.extensions

import android.os.SystemClock
import com.welie.blessed.BluetoothBytesParser
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

    val millis = if (timestampFlags.hasFlag(TimestampFlags.isTickCounter)) {
        SystemClock.elapsedRealtime()
    } else {
        currentTimeMillis - UTC_TO_UNIX_EPOCH_MILLIS
    }

    val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
    parser.setIntValue(timestampFlags.value.toInt(), BluetoothBytesParser.FORMAT_UINT8)
    System.out.println("Add Flag Byte: ${timestampFlags.value.toInt()}")
    if (timestampFlags.hasFlag(TimestampFlags.isMilliseconds)) {
        parser.setLong(millis)
        System.out.println("Add Milliseconds Value: ${timestampFlags.value.toInt()}")
    } else {
        parser.setLong(millis / 1000L)
        System.out.println("Add Seconds Value: ${millis / 1000L}")
    }

    if (!timestampFlags.hasFlag(TimestampFlags.isTickCounter)) {
        parser.setIntValue(Timesource.currentSource.value, BluetoothBytesParser.FORMAT_UINT8)
        System.out.println("Add Timesource Value: ${Timesource.currentSource.value}")

        val tz = TimeZone.getDefault()
        val timeZoneMillis = if (timestampFlags.hasFlag(TimestampFlags.isTZPresent)) tz.getOffset(currentTimeMillis) else 0
        val dstMillis = if (timestampFlags.hasFlag(TimestampFlags.isDSTPresent)) tz.getOffset(currentTimeMillis) else 0
        val offsetUnits = (timeZoneMillis + dstMillis) / MILLIS_IN_15_MINUTES
        parser.setIntValue(offsetUnits, BluetoothBytesParser.FORMAT_SINT8)
        System.out.println("Add Offset Value: $offsetUnits")
    }

    val bytes = parser.value
    System.out.println("GHS Timestamp Bytes: ${bytes.asHexString()}")
    return bytes
}

fun Date.testLogOffsets() {
    val tz = TimeZone.getDefault()
    val cal1: Calendar = Calendar.getInstance(tz) //currentZone: CET/CEST +1/+2, GMT+1:00
    println("System time, " + System.currentTimeMillis()) //UTC current milis
    System.out.println("Calendar time, " + cal1.getTime().getTime()) //UTC current milis
    System.out.println("Calendar millis, " + cal1.getTimeInMillis()) //UTC current milis
    System.out.println("Calendar Zone Offset: " + cal1.get(Calendar.ZONE_OFFSET))
    System.out.println("Calendar DST Offset: " + cal1.get(Calendar.DST_OFFSET))
    System.out.println("Time Zone Raw Offset: " + tz.getRawOffset())
    System.out.println("Time Zone DST Savings Offset: " + tz.getDSTSavings())
    System.out.println("Time Zone millis: " + tz.getOffset(System.currentTimeMillis()))
    System.out.println("Is Time Zone in DST: " + tz.inDaylightTime(this).toString())
    println("")
}
