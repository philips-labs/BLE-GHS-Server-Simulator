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

    fun serialize(): ByteArray { return fixedFormatByteArray }

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
    open val classByte: Int = 0x0   // Simple numeric

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
        parser.setIntValue(length, BluetoothBytesParser.FORMAT_UINT16)
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
            val tsBytes = timestamp.asGHSBytes()
            System.out.println("GHS Timestamp size: ${tsBytes.size} bytes: ${tsBytes.asHexString()}")
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setIntValue(timestampCode, BluetoothBytesParser.FORMAT_UINT32)
            parser.setIntValue(tsBytes.size, BluetoothBytesParser.FORMAT_UINT16)
            return BluetoothBytesParser.mergeArrays(parser.value, tsBytes)
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

        internal const val CONST_F0: UInt = 3840u
        internal const val CONST_F000: UInt = 65280u
    }
}

fun ObservationType.asFixedFormatByteArray(): ByteArray {
    // If Unknow return an empty byte array
    if (this == ObservationType.UNKNOWN_TYPE) return byteArrayOf()

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