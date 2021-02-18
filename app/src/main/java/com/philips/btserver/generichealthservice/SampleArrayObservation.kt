package com.welie.btserver.generichealthservice

import com.welie.blessed.BluetoothBytesParser
import com.welie.btserver.extensions.getByteArray
import timber.log.Timber
import java.nio.ByteOrder
import java.util.*

data class SampleArrayObservation(override val id: Short,
                                  override val type: ObservationType,
                                  override val value: ByteArray,
                                  override val unitCode: UnitCode,
                                  override val timestamp: Date): Observation() {

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(valueCode, BluetoothBytesParser.FORMAT_UINT32)
            parser.setIntValue(value.size, BluetoothBytesParser.FORMAT_UINT16)
            return parser.value + value
        }

    companion object {

        internal const val valueCode = 0x0001096E

        fun deserialize(bytes: ByteArray): SampleArrayObservation {
            val parser = BluetoothBytesParser(bytes, 0, ByteOrder.BIG_ENDIAN)

            // Parse type
            val parsedTypeCode = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32)
            if (parsedTypeCode != typeCode) {
                Timber.e("Expected typeCode but got %d", parsedTypeCode)
            }
            val parsedTypeLength = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            if (parsedTypeLength != typeLength) {
                Timber.e("Expected typeLength but got %d", parsedTypeLength)
            }
            val parsedType = ObservationType.fromValue(parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32)!!)
            if (parsedType == ObservationType.UNKNOWN_STATUS_CODE) {
                Timber.e("Unknown observation type")
            }

            // Parse id
            val parsedHandleCode = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32)
            if (parsedHandleCode != handleCode) {
                Timber.e("Expected handleCode but got %d", parsedHandleCode)
            }
            val parsedHandleLength = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            if (parsedHandleLength != handleLength) {
                Timber.e("Expected handleLength but got %d", parsedHandleLength)
            }
            val parsedId = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)!!

            // Parse value
            val parsedValueCode = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32)
            if (parsedValueCode != valueCode) {
                Timber.e("Expected valueCode but got %d", parsedValueCode)
            }
            val parsedValueLength = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            val parsedValue = parser.getByteArray(parsedValueLength!!)

            // Parse unit
            val parsedUnitCode = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32)
            if (parsedUnitCode != unitCodeId) {
                Timber.e("Expected unitCode but got %d", parsedUnitCode)
            }
            val parsedUnitLength = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            if (parsedUnitLength != unitLength) {
                Timber.e("Expected unitLength but got %d", parsedUnitLength)
            }
            val parsedUnit = UnitCode.fromValue(parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32)!!)
            if (parsedUnit === UnitCode.UNKNOWN_CODE) {
                Timber.e("Unknown unit type")
            }

            // Parse timestamp
            val parsedTimestampCode = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32)
            if (parsedTimestampCode != timestampCode) {
                Timber.e("Expected timestampCode but got %d", parsedTimestampCode)
            }
            val parsedTimestampLength = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            if (parsedTimestampLength != timestampLength) {
                Timber.e("Expected timestampLength but got %d", parsedTimestampLength)
            }
            val parsedTimestamp = Date(parser.longValue)
            return SampleArrayObservation(parsedId.toShort(), parsedType, parsedValue, parsedUnit, parsedTimestamp)
        }
    }
}
