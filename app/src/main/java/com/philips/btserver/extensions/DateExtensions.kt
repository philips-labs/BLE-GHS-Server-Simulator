package com.philips.btserver.extensions

import java.util.*

/*
 * Support for Flags in Kotlin (may want to move to a Flags.kt file
 */
class BitMask(val value: Long)

interface Flags  {
    val bit: Long

    fun toBitMask(): BitMask = BitMask(bit)
}

infix fun Flags.and(other: Long): BitMask = BitMask(bit and other)
infix fun <T: Flags> Flags.or(other: T): BitMask = BitMask(bit or other.bit)

operator infix fun Flags.plus(other: Flags): BitMask = BitMask(bit or other.bit)

inline fun <reified T> enabledValues(mask: BitMask) : List<T> where T : Enum<T>, T : Flags {
    return enumValues<T>().filter {
        mask hasFlag it
    }
}

infix fun BitMask.or(other: Flags): BitMask = BitMask(value or other.bit)

operator infix fun BitMask.plus(other: BitMask): BitMask = BitMask(value or other.value)
operator infix fun BitMask.plus(other: Flags): BitMask = BitMask(value or other.bit)

infix fun <T: Flags> BitMask.hasFlag(which: T): Boolean {
    // an Undefined flag is a special case.
    if(value == 0L || (value > 0L && which.bit == 0L)) return false

    return value and which.bit == which.bit
}

infix fun <T: Flags> BitMask.unset(which: T): BitMask = BitMask(value xor which.bit)

// End Flags support stuff

enum class TimestampFlags(override val bit: Long) : Flags {
    isUTC(1 shl 0),
    isTZPresent(1 shl 1),
    isDSTPresent(1 shl 2),
    isFractionsPresent(1 shl 3),
    isCurrentTimeline(1 shl 4),
    reserved_1(1 shl 5),
    reserved_2(1 shl 6),
    reserved_3(1 shl 7);
}

/*
 * Create a binary representation of the receiver based on the timestamp flags passed in.
 *
 * @returns bytes that are compliant with the GHS Time specification
 */
fun Date.asGHSBytes(timestampFlags: TimestampFlags): ByteArray {
    return byteArrayOf(0)
//    var hexString = this.toUINT8().toString(16).toUpperCase(Locale.ROOT)
//    if (this.toUINT8() < 16) hexString = "0$hexString"
//    return hexString
}
