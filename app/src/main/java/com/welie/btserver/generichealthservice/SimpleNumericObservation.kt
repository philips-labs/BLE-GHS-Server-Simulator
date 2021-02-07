package com.welie.btserver.generichealthservice

import com.welie.btserver.BluetoothBytesParser
import com.welie.btserver.BluetoothBytesParser.Companion.FORMAT_UINT16
import com.welie.btserver.BluetoothBytesParser.Companion.FORMAT_UINT32
import com.welie.btserver.ByteOrder
import timber.log.Timber
import java.util.*

data class SimpleNumericObservation(override val id: Short, override val type: ObservationType, override val value: Float, val valuePrecision: Int, override val unitCode: UnitCode, override val timestamp: Date): Observation() {

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(valueCode, FORMAT_UINT32)
            parser.setIntValue(valueLength, FORMAT_UINT16)
            parser.setFloatValue(value, valuePrecision)
            return parser.bytes
        }

    companion object {

        internal const val valueCode = 0x00010A56
        internal const val valueLength = 4

        fun deserialize(bytes: ByteArray): SimpleNumericObservation {
            val parser = BluetoothBytesParser(bytes, 0, ByteOrder.BIG_ENDIAN)

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
            if (parsedUnitCode != unitCodeId) {
                Timber.e("Expected unitCode but got %d", parsedUnitCode)
            }
            val parsedUnitLength = parser.getIntValue(FORMAT_UINT16)
            if (parsedUnitLength != unitLength) {
                Timber.e("Expected unitLength but got %d", parsedUnitLength)
            }
            val parsedUnit = UnitCode.fromValue(parser.getIntValue(FORMAT_UINT32)!!)
            if (parsedUnit === UnitCode.UNKNOWN_CODE) {
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