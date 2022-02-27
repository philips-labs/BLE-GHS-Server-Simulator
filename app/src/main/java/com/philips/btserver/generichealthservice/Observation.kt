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
    // TODO unitCode is moving/moved to observation values... should remove from Observation
    abstract val unitCode: UnitCode
    // This will be set to true for observations that are put into a BundledObservation.
    // This could be eliminated if timestamp becomes optional and timestamp is the only effected prop
    var isBundledObservation: Boolean = false
    val ghsByteArray: ByteArray
        get() {
            return listOf(
                flagsByteArray,
                if (type == ObservationType.UNKNOWN_TYPE) byteArrayOf() else type.asGHSByteArray(),
                if (includeTimestamp) timestamp.asGHSByteArray() else byteArrayOf(),
                valueByteArray
            ).merge().withLengthPrefix()
        }

    // Subclasses override to provide the byte array appropriate to their value
    // TODO May want to throw an exception here as this is (and should be declared?) an abstract class
    open val valueByteArray: ByteArray
        get() { return byteArrayOf() }

    val flagsByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setIntValue(attributeFlags or classByte, BluetoothBytesParser.FORMAT_UINT32)
            return parser.value
        }

    private val includeTimestamp: Boolean
        get() {
            return !isBundledObservation
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
    open val attributeFlags: Int
        get() {
            // TODO Add logic for other flags
            val typeFlag = if (type == ObservationType.UNKNOWN_TYPE) 0x0 else 0x10
            val timestampFlag = if (includeTimestamp) 0x20 else 0x0
            return typeFlag or timestampFlag
        }

    companion object {
        internal const val handleCode = 0x00010921
        internal const val handleLength = 2
        internal const val typeCode = 0x0001092F
        internal const val typeLength = 4
        internal const val unitCodeId = 0x00010996
        internal const val unitLength = 4
        internal const val timestampCode = 0x00010990
        internal const val timestampLength = 8

        internal const val CONST_F0: UInt = 3840u
        internal const val CONST_F000: UInt = 65280u
    }
}
