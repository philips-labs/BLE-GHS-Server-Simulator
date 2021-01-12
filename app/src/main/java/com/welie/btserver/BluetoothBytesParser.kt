/*
 * Copyright (c) Koninklijke Philips N.V. 2020
 * All rights reserved.
 */
package com.welie.btserver

import com.welie.btserver.extensions.asAsciiString
import com.welie.btserver.extensions.asByteArray
import com.welie.btserver.extensions.formatHexBytes
import java.util.*
import kotlin.math.pow

class BluetoothBytesParser(
        var bytes: ByteArray,
        var offset: Int,
        var byteOrder: ByteOrder
) {

    /**
     * Create a BluetoothBytesParser that does not contain an empty byte array and sets the byte order to [ByteOrder.LITTLE_ENDIAN].
     */
    constructor() : this(ByteArray(0), 0, ByteOrder.LITTLE_ENDIAN)

    /**
     * Create a BluetoothBytesParser with a ByteArray and sets the byte order to [ByteOrder.LITTLE_ENDIAN]
     */
    constructor(value: ByteArray) : this(value, 0, ByteOrder.LITTLE_ENDIAN)

    /**
     * Create a BluetoothBytesParser that does not contain a byte array and sets the byteOrder.
     */
    constructor(byteOrder: ByteOrder) : this(ByteArray(0), 0, byteOrder)

    constructor(value: ByteArray, offset: Int) : this(value, offset, ByteOrder.LITTLE_ENDIAN)

    /**
     * Return an Integer value of the specified type. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte(s) value
     * @return An Integer object or null in case the byte array was not valid
     */
    fun getIntValue(formatType: Int): Int? {
        val result = getIntValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    /**
     * Return an Integer value of the specified type and specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType the format type used to interpret the byte(s) value
     * @return an Integer object or null in case the byte array was not valid
     */
    fun getIntValue(formatType: Int, byteOrder: ByteOrder): Int? {
        val result = getIntValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    /**
     * Return a Long value. This operation will automatically advance the internal offset to the next position.
     *
     * @return an Long object or null in case the byte array was not valid
     */
    val longValue: Long
        get() = getLongValue(byteOrder)

    /**
     * Return a Long value using the specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @return an Long object or null in case the byte array was not valid
     */
    fun getLongValue(byteOrder: ByteOrder): Long {
        val result = getLongValue(offset, byteOrder)
        offset += 8
        return result
    }

    /**
     * Return a Long value using the specified byte order and offset position. This operation will not advance the internal offset to the next position.
     *
     * @return an Long object or null in case the byte array was not valid
     */
    fun getLongValue(offset: Int, byteOrder: ByteOrder): Long {
        var value = 0x00FFL
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            value = value and bytes[offset + 7].toLong()
            for (i in 6 downTo 0) {
                value = value shl 8
                value += 0x00FFL and bytes[i + offset].toLong()
            }
        } else {
            value = value and bytes[offset].toLong()
            for (i in 1..7) {
                value = value shl 8
                value += 0x00FFL and bytes[i + offset].toLong()
            }
        }
        return value
    }

    /**
     * Return an Integer value of the specified type. This operation will not advance the internal offset to the next position.
     *
     *
     * The formatType parameter determines how the byte array
     * is to be interpreted. For example, settting formatType to
     * [FORMAT_UINT16] specifies that the first two bytes of the
     * byte array at the given offset are interpreted to generate the
     * return value.
     *
     * @param formatType The format type used to interpret the byte array.
     * @param offset     Offset at which the integer value can be found.
     * @param byteOrder  the byte order, either [ByteOrder.LITTLE_ENDIAN] or [ByteOrder.BIG_ENDIAN]
     * @return Cached value of the byte array or null of offset exceeds value size.
     */
    fun getIntValue(formatType: Int, offset: Int, byteOrder: ByteOrder): Int? {
        if (offset + getTypeLen(formatType) > bytes.size) return null

        when (formatType) {
            FORMAT_UINT8 -> return unsignedByteToInt(bytes[offset])

            FORMAT_UINT16 -> return if (byteOrder == ByteOrder.LITTLE_ENDIAN)
                unsignedBytesToInt(bytes[offset], bytes[offset + 1])
            else
                unsignedBytesToInt(bytes[offset + 1], bytes[offset])

            FORMAT_UINT32 -> return if (byteOrder == ByteOrder.LITTLE_ENDIAN)
                unsignedBytesToInt(
                        bytes[offset], bytes[offset + 1],
                        bytes[offset + 2], bytes[offset + 3]
                )
            else
                unsignedBytesToInt(
                        bytes[offset + 3], bytes[offset + 2],
                        bytes[offset + 1], bytes[offset]
                )

            FORMAT_SINT8 -> return unsignedToSigned(unsignedByteToInt(bytes[offset]), 8)

            FORMAT_SINT16 -> return if (byteOrder == ByteOrder.LITTLE_ENDIAN)
                unsignedToSigned(
                        unsignedBytesToInt(
                                bytes[offset],
                                bytes[offset + 1]
                        ), 16
                )
            else
                unsignedToSigned(
                        unsignedBytesToInt(
                                bytes[offset + 1],
                                bytes[offset]
                        ), 16
                )

            FORMAT_SINT32 -> return if (byteOrder == ByteOrder.LITTLE_ENDIAN)
                unsignedToSigned(
                        unsignedBytesToInt(
                                bytes[offset],
                                bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]
                        ), 32
                )
            else
                unsignedToSigned(
                        unsignedBytesToInt(
                                bytes[offset + 3],
                                bytes[offset + 2], bytes[offset + 1], bytes[offset]
                        ), 32
                )
        }

        return null
    }

    /**
     * Return a float value of the specified format. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @return The float value at the position of the internal offset
     */
    fun getFloatValue(formatType: Int): Float? {
        val result = getFloatValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    /**
     * Return a float value of the specified format and byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @param byteOrder  the byte order, either [ByteOrder.LITTLE_ENDIAN] or [ByteOrder.BIG_ENDIAN]
     * @return The float value at the position of the internal offset
     */
    fun getFloatValue(formatType: Int, byteOrder: ByteOrder): Float? {
        val result = getFloatValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    /**
     * Return a float value of the specified format, offset and byte order. This operation will not advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @param byteOrder  the byte order, either [ByteOrder.LITTLE_ENDIAN] or [ByteOrder.BIG_ENDIAN]
     * @return The float value at the position of the internal offset
     */
    fun getFloatValue(formatType: Int, offset: Int, byteOrder: ByteOrder): Float? {
        if (offset + getTypeLen(formatType) > bytes.size) return null

        when (formatType) {
            FORMAT_SFLOAT -> return if (byteOrder == ByteOrder.LITTLE_ENDIAN)
                bytesToFloat(bytes[offset], bytes[offset + 1])
            else
                bytesToFloat(bytes[offset + 1], bytes[offset])

            FORMAT_FLOAT -> return if (byteOrder == ByteOrder.LITTLE_ENDIAN)
                bytesToFloat(
                        bytes[offset], bytes[offset + 1],
                        bytes[offset + 2], bytes[offset + 3]
                )
            else
                bytesToFloat(
                        bytes[offset + 3], bytes[offset + 2],
                        bytes[offset + 1], bytes[offset]
                )
        }

        return null
    }

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
     *
     * @return String value representated by the byte array
     */
    val stringValue: String?
        get() = getStringValue(offset)

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
     *
     * @param offset Offset at which the string value can be found.
     * @return String value representated by the byte array
     */
    fun getStringValue(offset: Int): String? { // Check if there are enough bytes to parse
        // Check if there are enough bytes to parse
        if (offset > bytes.size) return null

        // Copy all bytes
        val strBytes = ByteArray(bytes.size - offset)
        for (i in 0 until bytes.size - offset) strBytes[i] = bytes[offset + i]

        // Get rid of trailing zero/space bytes
        var j = strBytes.size
        while (j > 0 && (strBytes[j - 1].toInt() == 0 || strBytes[j - 1].toInt() == 0x20)) j--

        // Convert to string
        return strBytes.copyOfRange(0, j).asAsciiString()
    }

    /**
     * Return a the date represented by the byte array.
     *
     * The byte array must conform to the Date specification (year, month, day, hour, min, sec)
     *
     * @return the Date represented by the byte array
     */
    val getDateTime: Date
        get() {
            val result = getDateTime(offset)
            offset += 7
            return result
        }

    /**
     * Get Date from characteristic with offset
     *
     * @param offset Offset of value
     * @return Parsed date from value
     */
    private fun getDateTime(offset: Int): Date {
        // Date+Time is always in little endian
        var byteOffset = offset
        val year = getIntValue(FORMAT_UINT16, byteOffset, ByteOrder.LITTLE_ENDIAN)!!
        byteOffset += getTypeLen(FORMAT_UINT16)
        val month = getIntValue(FORMAT_UINT8, byteOffset, ByteOrder.LITTLE_ENDIAN)!!
        byteOffset += getTypeLen(FORMAT_UINT8)
        val day = getIntValue(FORMAT_UINT8, byteOffset, ByteOrder.LITTLE_ENDIAN)!!
        byteOffset += getTypeLen(FORMAT_UINT8)
        val hour = getIntValue(FORMAT_UINT8, byteOffset, ByteOrder.LITTLE_ENDIAN)!!
        byteOffset += getTypeLen(FORMAT_UINT8)
        val min = getIntValue(FORMAT_UINT8, byteOffset, ByteOrder.LITTLE_ENDIAN)!!
        byteOffset += getTypeLen(FORMAT_UINT8)
        val sec = getIntValue(FORMAT_UINT8, byteOffset, ByteOrder.LITTLE_ENDIAN)!!

        val calendar = GregorianCalendar(year, month - 1, day, hour, min, sec)
        return calendar.time
    }

    /**
     * Set the locally stored value of this byte array
     *
     * @param valueToSet      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @param offsetToSet     Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    fun setIntValue(valueToSet: Int, formatType: Int, offsetToSet: Int): Boolean {
        var value = valueToSet
        var bytesIndex = offsetToSet
        prepareArray(bytesIndex + getTypeLen(formatType))
        when (formatType) {
            FORMAT_SINT8 -> {
                value = intToSignedBits(value, 8)
                bytes[bytesIndex] = (value and 0xFF).toByte()
            }
            FORMAT_UINT8 -> bytes[bytesIndex] = (value and 0xFF).toByte()
            FORMAT_SINT16 -> {
                value = intToSignedBits(value, 16)
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    bytes[bytesIndex++] = (value and 0xFF).toByte()
                    bytes[bytesIndex] = (value shr 8 and 0xFF).toByte()
                } else {
                    bytes[bytesIndex++] = (value shr 8 and 0xFF).toByte()
                    bytes[bytesIndex] = (value and 0xFF).toByte()
                }
            }
            FORMAT_UINT16 -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                bytes[bytesIndex++] = (value and 0xFF).toByte()
                bytes[bytesIndex] = (value shr 8 and 0xFF).toByte()
            } else {
                bytes[bytesIndex++] = (value shr 8 and 0xFF).toByte()
                bytes[bytesIndex] = (value and 0xFF).toByte()
            }
            FORMAT_SINT32 -> {
                value = intToSignedBits(value, 32)
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    bytes[bytesIndex++] = (value and 0xFF).toByte()
                    bytes[bytesIndex++] = (value shr 8 and 0xFF).toByte()
                    bytes[bytesIndex++] = (value shr 16 and 0xFF).toByte()
                    bytes[bytesIndex] = (value shr 24 and 0xFF).toByte()
                } else {
                    bytes[bytesIndex++] = (value shr 24 and 0xFF).toByte()
                    bytes[bytesIndex++] = (value shr 16 and 0xFF).toByte()
                    bytes[bytesIndex++] = (value shr 8 and 0xFF).toByte()
                    bytes[bytesIndex] = (value and 0xFF).toByte()
                }
            }
            FORMAT_UINT32 -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                bytes[bytesIndex++] = (value and 0xFF).toByte()
                bytes[bytesIndex++] = (value shr 8 and 0xFF).toByte()
                bytes[bytesIndex++] = (value shr 16 and 0xFF).toByte()
                bytes[bytesIndex] = (value shr 24 and 0xFF).toByte()
            } else {
                bytes[bytesIndex++] = (value shr 24 and 0xFF).toByte()
                bytes[bytesIndex++] = (value shr 16 and 0xFF).toByte()
                bytes[bytesIndex++] = (value shr 8 and 0xFF).toByte()
                bytes[bytesIndex] = (value and 0xFF).toByte()
            }
            else -> return false
        }
        return true
    }

    /**
     * Set byte array to an Integer with specified format.
     *
     * @param value      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @return true if the locally stored value has been set
     */
    fun setIntValue(value: Int, formatType: Int): Boolean {
        val result = setIntValue(value, formatType, offset)
        if (result) {
            offset += getTypeLen(formatType)
        }
        return result
    }

    /**
     * Set byte array to a long
     *
     * @param value New long value for this byte array
     * @return true if the locally stored value has been set
     */
    fun setLong(value: Long): Boolean {
        return setLong(value, offset)
    }

    /**
     * Set byte array to a long
     *
     * @param valueToSet  New long value for this byte array
     * @param offset Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    private fun setLong(valueToSet: Long, offset: Int): Boolean {
        var value = valueToSet
        prepareArray(offset + 8)
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            for (i in 7 downTo 0) {
                bytes[i + offset] = (value and 0xFF).toByte()
                value = value shr 8
            }
        } else {
            for (i in 0..7) {
                bytes[i + offset] = (value and 0xFF).toByte()
                value = value shr 8
            }
        }
        return true
    }

    /**
     * Set byte array to a float of the specified type.
     *
     * @param floatMantissa   Mantissa for this float value
     * @param floatExponent   exponent value for this float value
     * @param formatType Float format type used to transform the value parameter
     * @param arrayOffset     Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    fun setFloatValue(
            floatMantissa: Int,
            floatExponent: Int,
            formatType: Int,
            arrayOffset: Int
    ): Boolean {
        var mantissa = floatMantissa
        var exponent = floatExponent
        var offset = arrayOffset
        prepareArray(offset + getTypeLen(formatType))
        when (formatType) {
            FORMAT_SFLOAT -> {
                mantissa = intToSignedBits(mantissa, 12)
                exponent = intToSignedBits(exponent, 4)
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    bytes[offset++] = (mantissa and 0xFF).toByte()
                    bytes[offset] = (mantissa shr 8 and 0x0F).toByte()
//                    bytes[offset] += (exponent and 0x0F shl 4).toByte()
                    bytes[offset] = (bytes[offset] + (exponent and 0x0F shl 4)).toByte()
//                    bytes[offset] = (12+ 13).toByte()
                } else {
                    bytes[offset] = (mantissa shr 8 and 0x0F).toByte()
                    bytes[offset++] = (bytes[offset] + (exponent and 0x0F shl 4)).toByte()
                    bytes[offset] = (mantissa and 0xFF).toByte()
                }
            }
            FORMAT_FLOAT -> {
                mantissa = intToSignedBits(mantissa, 24)
                exponent = intToSignedBits(exponent, 8)
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    bytes[offset++] = (mantissa and 0xFF).toByte()
                    bytes[offset++] = (mantissa shr 8 and 0xFF).toByte()
                    bytes[offset++] = (mantissa shr 16 and 0xFF).toByte()
                    bytes[offset] = (bytes[offset] + (exponent and 0xFF)).toByte()
                } else {
                    bytes[offset++] = (bytes[offset] + (exponent and 0xFF)).toByte()
                    bytes[offset++] = (mantissa shr 16 and 0xFF).toByte()
                    bytes[offset++] = (mantissa shr 8 and 0xFF).toByte()
                    bytes[offset] = (mantissa and 0xFF).toByte()
                }
            }
            else -> return false
        }
        return true
    }

    /**
     * Create byte[] value from Float usingg a given precision, i.e. number of digits after the comma
     *
     * @param value     Float value to create byte[] from
     * @param precision number of digits after the comma to use
     * @return true if the locally stored value has been set
     */
    fun setFloatValue(value: Float, precision: Int): Boolean {
        val exponent : Int = 10.toDouble().pow(precision).toInt()
        val mantissa = (value * exponent).toInt()
        return setFloatValue(
                mantissa, -precision,
                FORMAT_FLOAT, offset
        )
    }

    /**
     * Set byte array to a string at current offset
     *
     * @param value String to be added to byte array
     * @return true if the locally stored value has been set
     */
    fun setString(value: String?): Boolean {
        if (value != null) {
            setString(value, offset)
            offset += value.asByteArray().size
            return true
        }
        return false
    }

    /**
     * Set byte array to a string at specified offset position
     *
     * @param value  String to be added to byte array
     * @param offset the offset to place the string at
     * @return true if the locally stored value has been set
     */
    fun setString(value: String?, offset: Int): Boolean {
        return if (value != null) {
            prepareArray(offset + value.length)
            val valueBytes = value.asByteArray()
            valueBytes.copyInto(bytes, offset, 0, valueBytes.size)
            true
        } else false
    }

    /**
     * Sets the byte array to represent the current date in CurrentTime format
     *
     * @param Date the date/time object representing the current date
     * @return false if the date object was null, otherwise true
     */
    fun setCurrentTime(calendar: Calendar): Boolean {
        prepareArray(10)
        setDateTime(calendar)
        bytes[7] = ((calendar[Calendar.DAY_OF_WEEK] + 5) % 7 + 1).toByte()
        bytes[8] = (calendar[Calendar.MILLISECOND] * 256 / 1000).toByte()
        bytes[9] = 1
        return true
    }

    /**
     * Sets the byte array to represent the current date in Date format
     *
     * @param Date the date/time object representing the current date
     * @return false if the calendar object was null, otherwise true
     */
    fun setDateTime(calendar: Calendar): Boolean {
        prepareArray(7)
        bytes[0] = calendar[Calendar.YEAR].toByte()
        bytes[1] = (calendar[Calendar.YEAR] shr 8).toByte()
        bytes[2] = (calendar[Calendar.MONTH] + 1).toByte()
        bytes[3] = calendar[Calendar.DATE].toByte()
        bytes[4] = calendar[Calendar.HOUR_OF_DAY].toByte()
        bytes[5] = calendar[Calendar.MINUTE].toByte()
        bytes[6] = calendar[Calendar.SECOND].toByte()
        return true
    }

    /**
     * Returns the size of a give value type.
     */
    private fun getTypeLen(formatType: Int): Int {
        return formatType and 0xF
    }

    /**
     * Convert a signed byte to an unsigned int.
     */
    private fun unsignedByteToInt(b: Byte): Int {
        return b.toInt() and 0xFF
    }

    /**
     * Convert signed bytes to a 16-bit unsigned int.
     */
    private fun unsignedBytesToInt(b0: Byte, b1: Byte): Int {
        return unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
    }

    /**
     * Convert signed bytes to a 32-bit unsigned int.
     */
    private fun unsignedBytesToInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
                + (unsignedByteToInt(b2) shl 16) + (unsignedByteToInt(b3) shl 24))
    }

    /**
     * Convert signed bytes to a 16-bit short float value.
     */
    private fun bytesToFloat(b0: Byte, b1: Byte): Float {
        val mantissa = unsignedToSigned(
                unsignedByteToInt(b0)
                        + (unsignedByteToInt(b1) and 0x0F shl 8), 12
        )
        val exponent = unsignedToSigned(unsignedByteToInt(b1) shr 4, 4)
        return (mantissa * 10.toDouble().pow(exponent.toDouble())).toFloat()
    }

    /**
     * Convert signed bytes to a 32-bit short float value.
     */
    private fun bytesToFloat(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Float {
        val mantissa = unsignedToSigned(
                unsignedByteToInt(b0)
                        + (unsignedByteToInt(b1) shl 8)
                        + (unsignedByteToInt(b2) shl 16), 24
        )
        return (mantissa * 10.toDouble().pow(b3.toDouble())).toFloat()
    }

    /**
     * Convert an unsigned integer value to a two's-complement encoded
     * signed value.
     */
    private fun unsignedToSigned(unsignedValue: Int, size: Int): Int {
        var unsigned = unsignedValue
        if (unsigned and (1 shl size - 1) != 0) {
            unsigned = -1 * ((1 shl size - 1) - (unsigned and (1 shl size - 1) - 1))
        }
        return unsigned
    }

    /**
     * Convert an integer into the signed bits of a given length.
     */
    private fun intToSignedBits(value: Int, size: Int): Int {
        var i = value
        if (i < 0) {
            i = (1 shl size - 1) + (i and (1 shl size - 1) - 1)
        }
        return i
    }

    private fun prepareArray(neededSize: Int) {
        if (neededSize > bytes.size) {
            bytes += ByteArray(neededSize - bytes.size)
        }
    }

    override fun toString(): String {
        return bytes.formatHexBytes(null)
    }

    companion object {
        /**
         * Characteristic value format type uint8
         */
        const val FORMAT_UINT8 = 0x11

        /**
         * Characteristic value format type uint16
         */
        const val FORMAT_UINT16 = 0x12

        /**
         * Characteristic value format type uint32
         */
        const val FORMAT_UINT32 = 0x14

        /**
         * Characteristic value format type sint8
         */
        const val FORMAT_SINT8 = 0x21

        /**
         * Characteristic value format type sint16
         */
        const val FORMAT_SINT16 = 0x22

        /**
         * Characteristic value format type sint32
         */
        const val FORMAT_SINT32 = 0x24

        /**
         * Characteristic value format type sfloat (16-bit float)
         */
        const val FORMAT_SFLOAT = 0x32

        /**
         * Characteristic value format type float (32-bit float)
         */
        const val FORMAT_FLOAT = 0x34

        /**
         * Merge multiple arrays intro one array
         *
         * @param arrays Arrays to merge
         * @return Merge array
         */
        fun mergeArrays(vararg arrays: ByteArray): ByteArray {
            var result = byteArrayOf()
            arrays.forEach { result += it }

            return result
        }
    }
}