package com.philips.btserver.extensions

import android.os.SystemClock
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.nio.ByteOrder
import java.util.*

/*
 * Support for Flags in Kotlin (may want to move to a Flags.kt file, and a util package)
 */
class BitMask(val value: Long) {
    val intValue: Int get() = value.toInt()
}

interface Flags {
    val bit: Long

    fun toBitMask(): BitMask = BitMask(bit)

    val intValue: Int get() { return bit.toInt() }
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
    timeScaleBit0((1 shl 2).toLong()),
    timeScaleBit1((1 shl 3).toLong()),
    isTZPresent((1 shl 4).toLong()),
    isCurrentTimeline((1 shl 5).toLong()),
    reserved_1((1 shl 6).toLong()),
    reserved_2((1 shl 7).toLong());

    companion object {
        // This "global" holds the flags used to send out observations
        var currentFlags: BitMask = BitMask(TimestampFlags.timeScaleBit1.bit)
            .plus(TimestampFlags.isTZPresent)
            .plus(TimestampFlags.isCurrentTimeline)
            .plus(TimestampFlags.isUTC)


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

/*
 * Convert the value (secs, millis, 1/10sec, 1/10msec) into milliseconds value based on receiver flags
 */
fun BitMask.convertToTimeResolutionScaledMillisValue(value: Long): Long {
    return if (isSeconds()) value * 1000L
    else if (isMilliseconds()) value
    else if(isHundredMilliseconds()) value * 100L
    else if(isHundredthsMicroseconds()) value / 10L
    else value
}

/*
 * Convert the millisecond value into secs, millis, 1/10sec, 1/10msec based on receiver flags
 */
fun BitMask.getTimeResolutionScaledValue(millis: Long): Long {
    return if (isSeconds()) millis / 1000L
    else if (isMilliseconds()) millis
    else if(isHundredMilliseconds()) millis / 10L
    else if(isHundredthsMicroseconds()) millis * 10L
    else millis
}

fun BitMask.asTimestampFlagsString(): String {
    val ticksOrTime = if (this.isTickCounter()) "Ticks" else "Time"
    val utcOrLocal = if (this hasFlag TimestampFlags.isUTC) "UTC" else "Local"
    val millsOrSecs = if (isMilliseconds()) "Millis" else "Seconds"
    val hasTZ = if (this hasFlag TimestampFlags.isTZPresent) "TZ" else "No TZ"
    val current = if (this hasFlag TimestampFlags.isCurrentTimeline) "Current" else "Not Current"
    return "Value: ${value.toByte().asHexString()} : $ticksOrTime : $utcOrLocal : $millsOrSecs : $hasTZ : $current timeline"
}

fun BitMask.isTickCounter(): Boolean {
    return this hasFlag TimestampFlags.isTickCounter
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
    Timber.i("Timestamp Flags: ${timestampFlags.asTimestampFlagsString()}")
    val bytes = listOf(
        byteArrayOf(BitMask(timestampFlags.value).value.toByte()),
        timestampFlags.getElaspedTimeByteArray(this),
        byteArrayOf(Timesource.currentSource.value.toByte(), timestampFlags.dstOffsetValue().toByte())
    ).merge()
    Timber.i("Converting Date: $this to GHS bytes ${bytes.asFormattedHexString()}... reverse is:")
    bytes.asDateFromGHSBytes()
    return bytes
}

fun ByteArray.asDateFromGHSBytes(): Date {
    val parser = BluetoothBytesParser(this)
    val flagsValue = parser.sInt8
    val counter = parser.uInt48 + UTC_TO_UNIX_EPOCH_MILLIS
    val source = parser.sInt8
    val offset = parser.sInt8
    val flags = BitMask(flagsValue.toLong())
    val scaledCounter = (if(flags.isSeconds()) counter * 1000 else counter) + (offset * MILLIS_IN_15_MINUTES)
    val date = Date(scaledCounter)
    Timber.i("ETS Bytes parsed to flags: $flagsValue millis: $scaledCounter source: ${Timesource.value(source)} offset: $offset date: $date")
    return date
}

fun ByteArray.etsFlags(): BitMask = BitMask(this[0].toLong())

fun ByteArray.etsTicksValue(): Long {
    return this[1].toUByte().toLong() +
            this[2].toUByte().toLong().shl(8) +
            this[3].toUByte().toLong().shl(16) +
            this[4].toUByte().toLong().shl(24) +
            this[5].toUByte().toLong().shl(32) +
            this[6].toUByte().toLong().shl(40)
}

/*
 * Convert the value passed in from a Y2K epoch value to a Unix Epoch (1970) value in millisecods
 * The receiver bit mask encodes the resolution of the value (seconds, millis, hundred millis,
 * hundred microseconds as defined by the spec)
 */
fun BitMask.convertY2KScaledToUTCEpochMillis(value: Long): Long {
    return (if (isSeconds()) value * 1000L
    else if (isMilliseconds()) value
    else if (isHundredMilliseconds()) value * 100L
    else if (isHundredthsMicroseconds()) value / 10L
    else value) + UTC_TO_UNIX_EPOCH_MILLIS
}

fun ByteArray.parseETSDate(): Date? {
    return if (etsFlags().hasFlag(TimestampFlags.isTickCounter)) {
        null
    } else {
        val scaledTicks = etsFlags().convertY2KScaledToUTCEpochMillis(etsTicksValue())
        Date(scaledTicks)
    }
}
fun ByteArray.etsTimesourceValue(): Timesource {
    return Timesource.value(this[7].toInt())
}

fun ByteArray.etsTimezoneOffset(): Int {
    return this[8] * MILLIS_IN_15_MINUTES
}

fun BitMask.timescaleString(): String {
    return if (isMilliseconds()) "milliseconds" else if (isHundredMilliseconds()) "0.1 sec" else if (isHundredthsMicroseconds()) "100 usecs" else "seconds"
}


fun ByteArray.etsDateInfoString(): String {
    val etsFlags = etsFlags()
    return if (etsFlags.hasFlag(TimestampFlags.isTickCounter)) {
        "Bytes are for a tick counter parsed ETS Date (should be null): ${parseETSDate()}"
    } else {
        var infoString = "Flags:"
        if (etsFlags.hasFlag(TimestampFlags.isUTC)) {
            infoString += " UTC time,"
        } else {
            infoString += " local time,"
        }
        if (etsFlags.hasFlag(TimestampFlags.isTZPresent)) {
            infoString += " with TZ/DST offset,"
        } else {
            infoString += " no TZ/DST offset,"
        }
        if (etsFlags.hasFlag(TimestampFlags.isCurrentTimeline)) {
            infoString += " current"
        } else {
            infoString += " not current"
        }
        infoString += ".\n"
        val ticks = etsTicksValue()
        //infoString += "Epoch millis Value: Unix: ${ticks + UTC_TO_UNIX_EPOCH_MILLIS}\nY2K: $ticks\n"
        val timeSource = etsTimesourceValue()
        val offset = etsTimezoneOffset()
        infoString += "Timesource: $timeSource\ntime counter is ${etsFlags.timescaleString()}\n"
        val scaledTicks = etsFlags.convertY2KScaledToUTCEpochMillis(ticks)
        infoString += "UTC Epoch Millis:$scaledTicks\nOffset (msec): $offset\n"
        infoString += "ETS Date: ${parseETSDate()}\n"
        infoString += "ETS offset (15min): ${etsTimezoneOffset() / MILLIS_IN_15_MINUTES}"
        infoString
    }
}

private fun BitMask.getElaspedTimeByteArray(date: Date): ByteArray {
    val currentTimeMillis = System.currentTimeMillis()

    var millis = if (isTickCounter()) SystemClock.elapsedRealtime() else date.epoch2000mills()

    if (!isTickCounter()) {
        // Used if the clock is reporting local time, not UTC time. Get UTC offset and add it to the milliseconds reported for local time
        val utcOffsetMillis = if (this hasFlag TimestampFlags.isUTC) 0L else TimeZone.getDefault().getOffset(currentTimeMillis).toLong()
        Timber.i("Epoch millis Value: Unix: ${millis + UTC_TO_UNIX_EPOCH_MILLIS} Y2K: $millis utc offset: $utcOffsetMillis")
         millis += utcOffsetMillis
    } else {
        Timber.i("Tick Counter Epoch millis Value: Unix: ${millis + UTC_TO_UNIX_EPOCH_MILLIS} Y2K: $millis")
    }

    return getTimeResolutionScaledValue(millis).asUInt48ByteArray()
}

private fun BitMask.dstOffsetValue(): Int {
    var offsetUnits = 0

    if (!isTickCounter()) {
        val calendar = Calendar.getInstance(Locale.getDefault());
        val timeZoneMillis = if (this hasFlag TimestampFlags.isTZPresent) calendar.get(Calendar.ZONE_OFFSET) else 0
        val dstMillis =if (this hasFlag TimestampFlags.isTZPresent) calendar.get(Calendar.DST_OFFSET) else 0
        offsetUnits = (timeZoneMillis + dstMillis) / MILLIS_IN_15_MINUTES
        Timber.i("Add Offset Value: $offsetUnits")
    }

    return offsetUnits
}

fun Long.asUInt48ByteArray(): ByteArray {
    val millParser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
    millParser.setUInt48(this)
    return millParser.value
}

// Return true if current TimestampFlags (a BitMask) indicates  a timestamp value is sent, false if a tick counter
fun BitMask.isTimestamp(): Boolean {
    return !hasFlag(TimestampFlags.isTickCounter)
}

fun BitMask.isSeconds(): Boolean {
    return !(this hasFlag TimestampFlags.timeScaleBit1) and !(this hasFlag TimestampFlags.timeScaleBit0)
}

fun BitMask.isMilliseconds(): Boolean {
    return (this hasFlag TimestampFlags.timeScaleBit1) and !(this hasFlag TimestampFlags.timeScaleBit0)
}

fun BitMask.isHundredMilliseconds(): Boolean {
    return !(this hasFlag TimestampFlags.timeScaleBit1) and (this hasFlag TimestampFlags.timeScaleBit0)
}

fun BitMask.isHundredthsMicroseconds(): Boolean {
    return (this hasFlag TimestampFlags.timeScaleBit1) and (this hasFlag TimestampFlags.timeScaleBit0)
}