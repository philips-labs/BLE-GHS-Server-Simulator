package com.philips.btserver.generichealthservice

import com.welie.blessed.BluetoothBytesParser
import java.util.*
import com.philips.btserver.extensions.merge
import java.nio.ByteOrder

abstract class Observation() {
    abstract val id: Short
    abstract val type: ObservationType
    abstract val timestamp: Date
    abstract val value: Any
    abstract val unitCode: UnitCode

    fun serialize(): ByteArray {
        return listOf(
                typeByteArray,
                handleByteArray,
                valueByteArray,
                unitByteArray,
                timestampByteArray).merge()
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
        omitFixedLengthTypes(0),


        /*
         * Although specified as required the handle is only used if the object is referenced
         * in another object. This is not the case in a vast majority of use cases, so allow
         * the handle element to not be included, unless it will be used in another reference.
         *
         * This also implies that handle should (can?) not be the first element.
         *
         */
        omitHandleTLV(1),

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
        omitUnitCode(2),

        /*
         * A "short" type code is to experiment with using 2-byte (16-bit) observation type
         * codes rather than 4-byte full MDC codes... however, this implies the receiver can
         * map them back to MDC codes, which potentially dilutes the saving of 2 bytes from each
         * type code element. However, an assumption of the IEEE 10101 partition space can be made
         * to allow short codes. In this case setting useShortTypeCodes will simply use the least
         * significant 16-bits of the full code (note all example observations fall under
         * partition 2).
         */
        useShortTypeCodes(3),

    }

    fun serializeWithExperimentalOptions(): ByteArray {
        val serializeArray = mutableListOf(typeByteArray)
        if (!experimentalOptions.get(ExperimentalFeature.omitHandleTLV.bit)) {
            serializeArray.add(handleByteArray)
        }
        serializeArray.add(valueByteArray)
        if (!experimentalOptions.get(ExperimentalFeature.omitUnitCode.bit) && type.isKnownUnitCode()) {
            serializeArray.add(unitByteArray)
        }
        serializeArray.add(timestampByteArray)
        return serializeArray.merge()
    }

    abstract val valueByteArray: ByteArray

    private val omitFixedLengthTypes: Boolean
        get() { return experimentalOptions.get(ExperimentalFeature.omitFixedLengthTypes.bit) }

    private val handleByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(handleCode, BluetoothBytesParser.FORMAT_UINT32)
            if (!omitFixedLengthTypes) parser.setIntValue(handleLength, BluetoothBytesParser.FORMAT_UINT16)
            parser.setIntValue(id.toInt(), BluetoothBytesParser.FORMAT_UINT16)
            return parser.value
        }

    private val typeByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(typeCode, BluetoothBytesParser.FORMAT_UINT32)
            if (!omitFixedLengthTypes) parser.setIntValue(typeLength, BluetoothBytesParser.FORMAT_UINT16)
            parser.setIntValue(type.value, BluetoothBytesParser.FORMAT_UINT32)
            return parser.value
        }


    val unitByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(unitCodeId, BluetoothBytesParser.FORMAT_UINT32)
            if (!omitFixedLengthTypes) parser.setIntValue(unitLength, BluetoothBytesParser.FORMAT_UINT16)
            parser.setIntValue(unitCode.value, BluetoothBytesParser.FORMAT_UINT32)
            return parser.value
        }

    val timestampByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
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
        internal const val unitCodeId = 0x00010996
        internal const val unitLength = 4
        internal const val timestampCode = 0x00010990
        internal const val timestampLength = 8
    }
}

fun ObservationType.isKnownUnitCode(): Boolean {
    return this == ObservationType.MDC_ECG_HEART_RATE
}