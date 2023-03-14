/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.observations

import com.philips.btserver.extensions.*
import com.philips.btserver.userdataservice.UserDataManager
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
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
    val patientId: Int = UserDataManager.currentUserIndex
    val supplimentalInfo: List<ObservationType> = emptyList()
    val ghsByteArray: ByteArray
        get() { return ghsByteArray(false) }

    var isCurrentTimeline = true

    internal fun ghsByteArray(isBundled: Boolean = false): ByteArray {
        return listOf(
                byteArrayOf(classByte.value),
                flagsByteArray(!isBundled),
                if (type == ObservationType.UNKNOWN_TYPE) byteArrayOf() else type.asGHSByteArray(),
                if (isBundled) byteArrayOf() else timestampByteArray,
                patientIdByteArray,
                supplimentalInfoByteArray,
                valueByteArray).merge().withLengthPrefix()
    }

    // Subclasses override to provide the byte array appropriate to their value
    // TODO May want to throw an exception here as this is (and should be declared?) an abstract class
    open val valueByteArray: ByteArray
        get() { return byteArrayOf() }

    val patientIdByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setUInt8(patientId)
            return parser.value
        }

    private val timestampByteArray: ByteArray
        get() {
            var flagsBitmask = BitMask(TimestampFlags.currentFlags.value)
            flagsBitmask = if (isCurrentTimeline) flagsBitmask.set(TimestampFlags.isCurrentTimeline)
                else flagsBitmask.unset(TimestampFlags.isCurrentTimeline)
            return timestamp.asGHSBytes(flagsBitmask)
        }

    private fun flagsByteArray(includeTS: Boolean): ByteArray {
        val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
        parser.setUInt16(attributeFlags(includeTS))
        return parser.value
    }

    val supplimentalInfoByteArray: ByteArray
        get() {
            return if (supplimentalInfo.isEmpty()) { byteArrayOf() } else {
                val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
                parser.setUInt8(supplimentalInfo.size)
                supplimentalInfo.forEach { parser.setUInt32(it.value) }
                parser.value
            }
        }

    // This is the nibble that represents the observation class in the header bytes
    open val classByte: ObservationClass = ObservationClass.Unknown   // Simple numeric

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
        get() { return attributeFlags(true) }

    fun attributeFlags(includeTS: Boolean = true): Int {
        // TODO Add logic for other flags
        val typeFlag = if (type == ObservationType.UNKNOWN_TYPE) 0x0 else 0x1
        val supplementalInfoFlag = if (supplimentalInfo.isEmpty()) 0x0 else 0x40
        val timestampFlag = if (includeTS) 0x2 else 0x0
        val patientIdFlag = if (patientId > 0) 0x20 else 0x0
        return typeFlag or supplementalInfoFlag or timestampFlag or patientIdFlag
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

enum class ObservationClass(val value: Byte) {
    SimpleNumeric(0x01),
    SimpleDiscreet(0x02),
    String(0x03),
    RealTimeSampleArray(0x04),
    CompoundDiscreteEvent(0x05),
    CompoundState(0x06),
    Compound(0x07),
    TLVEncoded(0x08),
    ObservationBundle(0xFF.toByte()),
    Unknown(0xF0.toByte());

    companion object {
        fun fromValue(value: Byte): ObservationClass {
            return values().find { it.value == value } ?: Unknown
        }
    }

}
