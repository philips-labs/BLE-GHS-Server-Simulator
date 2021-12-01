/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import com.philips.btserver.extensions.*
import com.welie.blessed.BluetoothBytesParser
import java.util.*
import java.nio.ByteOrder

/**
 * Observtion is the abstract class representing various measured observations.
 * The varaints of observations are based on value (numeric, compound, sample arrays, strings, enums)
 * This abstract class provides the common implementation for representing common properites and
 * encoding observations into serialized byte arrays. Concrete classes of Observation simply need
 * to implement the value serialization to bytes (along with providing the value unit code and length).
 */
abstract class Observation {
    abstract val id: Short
    abstract val type: ObservationType
    abstract val timestamp: Date
    abstract val value: Any
    abstract val unitCode: UnitCode

    fun serialize(): ByteArray {
        val result = if (USE_TLV_ENCODING) tlvByteArray else fixedFormatByteArray
        return result
    }

    /*
     * Experimental serialization options
     */

    var experimentalOptions = BitSet()

    enum class ExperimentalFeature(val bit: Int) {
        /*
         * Omitting length on TLVs where the "Ts" (types) are of known, fixed length.
         * The types that have a known length are:
         *      Handle
         *      Unit Code
         *      Value type code
         *      Timestamp (see notes below)
         *
         * Notes:
         *
         * Multiple time lengths (4, 6, 8 bytes) are being investigated/supported. However,
         * it is assumed that timestamps (and timezone) will end up being a fixed length type.
         *
         * As more of the ACOM model is implemented, more types will be included and any
         * that are fixed length are assumed to be included in this list
         */
        OmitFixedLengthTypes(0),


        /*
         * Although specified as required the handle is only used if the object is referenced
         * in another object. This is not the case in a vast majority of use cases, so allow
         * the handle element to not be included, unless it will be used in another reference.
         *
         * This also implies that handle should (can?) not be the first element.
         *
         */
        OmitHandleTLV(1),

        /*
         * For many observation types (heart rate, blood pressure, spO2, etc) the units are
         * known and standard, with alternative units being an exception/edge case.
         *
         * This allows the omission ot the unit code in which case the receiver can assume
         * the "standard" unit (e.g. for heart rate bpm, for temperatures degrees centigrade...
         * etc... though for measures like weight there would need to be agreement on grams or
         * kilograms)
         *
         */
        OmitUnitCode(2),

        /*
         * A "short" type code is to experiment with using 2-byte (16-bit) observation type
         * codes rather than 4-byte full MDC codes... however, this implies the receiver can
         * map them back to MDC codes, which potentially dilutes the saving of 2 bytes from each
         * type code element. However, an assumption of the IEEE 10101 partition space can be made
         * to allow short codes. In this case setting UseShortTypeCodes will simply use the least
         * significant 16-bits of the full code (note all example observations fall under
         * partition 2).
         */
        UseShortTypeCodes(3),

    }

    var omitFixedLengthTypes: Boolean
        get() { return experimentalOptions.get(ExperimentalFeature.OmitFixedLengthTypes.bit) }
        set(bool) { experimentalOptions.set(ExperimentalFeature.OmitFixedLengthTypes.bit, bool) }

    var omitHandleTLV: Boolean
        get() = experimentalOptions.get(ExperimentalFeature.OmitHandleTLV.bit)
        set(bool) { experimentalOptions.set(ExperimentalFeature.OmitHandleTLV.bit, bool) }

    var omitUnitCode: Boolean
        get() = experimentalOptions.get(ExperimentalFeature.OmitUnitCode.bit)
        set(bool) { experimentalOptions.set(ExperimentalFeature.OmitUnitCode.bit, bool) }

    var useShortTypeCodes: Boolean
        get() = experimentalOptions.get(ExperimentalFeature.UseShortTypeCodes.bit)
        set(bool) { experimentalOptions.set(ExperimentalFeature.UseShortTypeCodes.bit, bool) }

    @Deprecated("We are done with Experimental options for GHS for now")
    fun serializeWithExperimentalOptions(): ByteArray {
        val typeBytes = if (useShortTypeCodes) experimentalTypeByteArray else typeByteArray
        val serializeArray = mutableListOf(typeBytes)
        if (!omitHandleTLV) {
            serializeArray.add(handleByteArray)
        }
        serializeArray.add(valueByteArray)
        if (!(omitUnitCode && type.isKnownUnitCode())) {
            serializeArray.add(unitByteArray)
        }
        serializeArray.add(timestampByteArray)
        return serializeArray.merge()
    }

    abstract val valueByteArray: ByteArray

    /*
     * Methods to generate bytes for new fixed ordered format
     */

    val fixedFormatByteArray: ByteArray
        get() {
            return listOf(
                flagsByteArray,
                type.asFixedFormatByteArray(),
                timestamp.asFixedFormatByteArray(),
                fixedValueByteArray
            ).merge()
        }

    val flagsByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setIntValue(attributeFlags or classByte, BluetoothBytesParser.FORMAT_UINT32)
            return parser.value
        }

    // This is the nibble that represents the observation class in the header bytes
    open val classByte: Int = 0x0   // Note 0 is value for simple numeric... may want to use 0xFF?

    /*
    Bits 5-12: attribute presence
        5.	Observation type present
        6.	Time stamp present
        7.	Measurement duration present
        8.	Measurement Status present
        9.	Object Id present
        10.	Patient present
        11.	Supplemental Information present
        12.	Derived-from present
        13.	hasMember present
        14.	TLVs present
     */
    open val attributeFlags: Int = 0x0030

    /*
     * Methods to generate bytes for old pure, unordered TLV format
     */

    val tlvByteArray: ByteArray
        get() {
            return listOf(
                typeByteArray,
                handleByteArray,
                valueByteArray,
                unitByteArray,
                timestampByteArray).merge()
        }

    // Made public for ObservationTest
    val handleByteArray: ByteArray
        get() { return encodeTLV(handleCode, handleLength, id) }

    // Made public for ObservationTest
    val typeByteArray: ByteArray
        get() { return encodeTLV(typeCode, typeLength, type.value) }

    // If type code is in partition two, 2 bytes otherwise normal 4 byte type code
    val experimentalTypeByteArray: ByteArray
        get() {
            return if (type.isShortTypeCode()) {
                encodeTLV(typeCode, shortTypeLength, type.shortTypeValue())
            } else {
                typeByteArray
            }
        }


    // Made public for ObservationTest
    val unitByteArray: ByteArray
        get() {
            val ba = encodeTLV(unitCodeId, unitLength, unitCode.value)
            return ba
        }

    // Used by fixed length, Short/Int/Long/Float fields (handle, type, unit)
    protected fun encodeTLV(type: Int, length: Int, value: Number, precision: Int = 2): ByteArray {
        val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
        parser.setIntValue(type, BluetoothBytesParser.FORMAT_UINT32)
        if (!(omitFixedLengthTypes && isFixedLengthType(type))) {
            parser.setIntValue(length, BluetoothBytesParser.FORMAT_UINT16)
        }
        when (value) {
            is Int -> parser.setIntValue(value, BluetoothBytesParser.FORMAT_UINT32)
            is Short -> parser.setIntValue(value.toInt(), BluetoothBytesParser.FORMAT_UINT16)
            is Long -> parser.setLong(value)
            is Float -> parser.setFloatValue(value, precision)
            else -> error("Unsupported value type sent to encodeTLV()")
        }
        return parser.value
    }

    // Subclasses override to provide the byte array appropriate to their value
    // TODO May want to throw an exception here as this is (and should be declared?) an abstract class
    open val fixedValueByteArray: ByteArray
        get() { return byteArrayOf() }

    /*
     * Concrete classes can override for types (like sample arrays) that are not fixed length
     */
    open fun isFixedLengthType(type: Int): Boolean {
        return true
    }

    // Made public for ObservationTest
    val timestampByteArray: ByteArray
        get() {
            return getGHSTimestampBytes()
//            return getSimpleTimestampBytes()
        }

    private fun getGHSTimestampBytes(): ByteArray {
        val tsBytes = timestamp.asGHSBytes()
        System.out.println("GHS Timestamp size: ${tsBytes.size} bytes: ${tsBytes.asHexString()}")
        val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
        parser.setIntValue(timestampCode, BluetoothBytesParser.FORMAT_UINT32)
        parser.setIntValue(tsBytes.size, BluetoothBytesParser.FORMAT_UINT16)
        return BluetoothBytesParser.mergeArrays(parser.value, tsBytes)
    }

    private fun getSimpleTimestampBytes(): ByteArray {
        val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
        parser.setIntValue(timestampCode, BluetoothBytesParser.FORMAT_UINT32)
        if (!omitFixedLengthTypes) parser.setIntValue(timestampLength, BluetoothBytesParser.FORMAT_UINT16)
        parser.setLong(timestamp.time)
        return parser.value
    }

    companion object {
        internal const val handleCode = 0x00010921
        internal const val handleLength = 2
        internal const val typeCode = 0x0001092F
        internal const val typeLength = 4
        internal const val shortTypeLength = 2
        internal const val unitCodeId = 0x00010996
        internal const val unitLength = 4
        internal const val timestampCode = 0x00010990
        internal const val timestampLength = 8

        // For GHS Byte Array use flex TLV or fixed header encoding schemes
        private const val USE_TLV_ENCODING = false

        internal const val CONST_F0: UInt = 3840u
        internal const val CONST_F000: UInt = 65280u
    }
}

fun ObservationType.asFixedFormatByteArray(): ByteArray {
    val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
    parser.setIntValue(value, BluetoothBytesParser.FORMAT_UINT32)
    return parser.value
}

fun ObservationType.isKnownUnitCode(): Boolean {
    return this == ObservationType.MDC_ECG_HEART_RATE
}

// MDC Codes in partition 2 can have high order 16-bits masked out and partition 2 assumed
fun ObservationType.isShortTypeCode(): Boolean {
    return (this.value shr 16) == 0x2
}

// MDC Codes in partition 2 can have high order 16-bits masked out and partition 2 assumed
fun ObservationType.shortTypeValue(): Short {
    return (this.value and 0xFFFF).toShort()
}