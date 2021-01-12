package com.welie.btserver

import com.welie.btserver.BluetoothBytesParser.Companion.FORMAT_UINT16
import com.welie.btserver.BluetoothBytesParser.Companion.FORMAT_UINT32
import timber.log.Timber
import java.util.*

data class SimpleNumericObservation(val id: Short, val type: ObservationType, val value: Float, val valuePrecision: Int, val unit: Unit, val timestamp: Date) {

    fun serialize(): ByteArray {
        val handleParser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
        handleParser.setIntValue(handleCode, FORMAT_UINT32)
        handleParser.setIntValue(handleLength, FORMAT_UINT16)
        handleParser.setIntValue(id.toInt(), FORMAT_UINT16)

        val typeParser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
        typeParser.setIntValue(typeCode, FORMAT_UINT32)
        typeParser.setIntValue(typeLength, FORMAT_UINT16)
        typeParser.setIntValue(type.value, FORMAT_UINT32)

        val valueParser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
        valueParser.setIntValue(valueCode, FORMAT_UINT32)
        valueParser.setIntValue(valueLength, FORMAT_UINT16)
        valueParser.setFloatValue(value, valuePrecision)

        val unitParser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
        unitParser.setIntValue(unitCode, FORMAT_UINT32)
        unitParser.setIntValue(unitLength, FORMAT_UINT16)
        unitParser.setIntValue(unit.value, FORMAT_UINT32)

        val timestampParser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
        timestampParser.setIntValue(timestampCode, FORMAT_UINT32)
        timestampParser.setIntValue(timestampLength, FORMAT_UINT16)
        timestampParser.setLong(timestamp.time)

        return BluetoothBytesParser.mergeArrays(
                handleParser.bytes,
                typeParser.bytes,
                valueParser.bytes,
                unitParser.bytes,
                timestampParser.bytes
        )
    }

    companion object {
        private const val handleCode = 0x00010921
        private const val handleLength = 2
        private const val typeCode = 0x0001092F
        private const val typeLength = 4
        private const val valueCode = 0x00010A56
        private const val valueLength = 4
        private const val unitCode = 0x00010996
        private const val unitLength = 4
        private const val timestampCode = 0x00010990
        private const val timestampLength = 8

        fun deserialize(bytes: ByteArray): SimpleNumericObservation {
            val parser = BluetoothBytesParser(bytes, 0, ByteOrder.BIG_ENDIAN)

            // Parse id
            val parsedHandleCode = parser.getIntValue(FORMAT_UINT32)
            if (parsedHandleCode != handleCode) {
                Timber.e("Expected handleCode but got %d", parsedHandleCode)
            }
            val parsedHandleLength = parser.getIntValue(FORMAT_UINT16)
            if (parsedHandleLength != handleLength) {
                Timber.e("Expected handleLength but got %d", parsedHandleLength)
            }
            val parsedId = parser.getIntValue(FORMAT_UINT16)!!

            // Parse type
            val parsedTypeCode = parser.getIntValue(FORMAT_UINT32)
            if (parsedTypeCode != typeCode) {
                Timber.e("Expected typeCode but got %d", parsedTypeCode)
            }
            val parsedTypeLength = parser.getIntValue(FORMAT_UINT16)
            if (parsedTypeLength != typeLength) {
                Timber.e("Expected typeLength but got %d", parsedTypeLength)
            }
            val parsedType = ObservationType.fromValue(parser.getIntValue(FORMAT_UINT32)!!)
            if (parsedType == ObservationType.UNKNOWN_STATUS_CODE) {
                Timber.e("Unknown observation type")
            }

            // Parse value
            val parsedValueCode = parser.getIntValue(FORMAT_UINT32)
            if (parsedValueCode != valueCode) {
                Timber.e("Expected valueCode but got %d", parsedValueCode)
            }
            val parsedValueLength = parser.getIntValue(FORMAT_UINT16)
            if (parsedValueLength != valueLength) {
                Timber.e("Expected valueLength but got %d", parsedValueLength)
            }
            val parsedValue = parser.getFloatValue(BluetoothBytesParser.FORMAT_FLOAT, parser.offset, ByteOrder.BIG_ENDIAN)!!.toFloat()
            val parsedValuePrecision = -(parser.getIntValue(FORMAT_UINT32)!! shr 24).toInt()

            // Parse unit
            val parsedUnitCode = parser.getIntValue(FORMAT_UINT32)
            if (parsedUnitCode != unitCode) {
                Timber.e("Expected unitCode but got %d", parsedUnitCode)
            }
            val parsedUnitLength = parser.getIntValue(FORMAT_UINT16)
            if (parsedUnitLength != unitLength) {
                Timber.e("Expected unitLength but got %d", parsedUnitLength)
            }
            val parsedUnit = Unit.fromValue(parser.getIntValue(FORMAT_UINT32)!!)
            if (parsedUnit === Unit.UNKNOWN_CODE) {
                Timber.e("Unknown unit type")
            }

            // Parse timestamp
            val parsedTimestampCode = parser.getIntValue(FORMAT_UINT32)
            if (parsedTimestampCode != timestampCode) {
                Timber.e("Expected timestampCode but got %d", parsedTimestampCode)
            }
            val parsedTimestampLength = parser.getIntValue(FORMAT_UINT16)
            if (parsedTimestampLength != timestampLength) {
                Timber.e("Expected timestampLength but got %d", parsedTimestampLength)
            }
            val parsedTimestamp = Date(parser.longValue)
            return SimpleNumericObservation(parsedId.toShort(), parsedType, parsedValue, parsedValuePrecision, parsedUnit, parsedTimestamp)
        }
    }
}