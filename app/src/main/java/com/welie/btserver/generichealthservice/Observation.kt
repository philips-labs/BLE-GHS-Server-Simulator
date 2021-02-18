package com.welie.btserver.generichealthservice

import com.welie.blessed.BluetoothBytesParser
import com.welie.btserver.*
import java.util.*
import com.welie.btserver.extensions.merge
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

    abstract val valueByteArray: ByteArray

    private val handleByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(handleCode, BluetoothBytesParser.FORMAT_UINT32)
            parser.setIntValue(handleLength, BluetoothBytesParser.FORMAT_UINT16)
            parser.setIntValue(id.toInt(), BluetoothBytesParser.FORMAT_UINT16)
            return parser.value
        }

    private val typeByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(typeCode, BluetoothBytesParser.FORMAT_UINT32)
            parser.setIntValue(typeLength, BluetoothBytesParser.FORMAT_UINT16)
            parser.setIntValue(type.value, BluetoothBytesParser.FORMAT_UINT32)
            return parser.value
        }


    val unitByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(unitCodeId, BluetoothBytesParser.FORMAT_UINT32)
            parser.setIntValue(unitLength, BluetoothBytesParser.FORMAT_UINT16)
            parser.setIntValue(unitCode.value, BluetoothBytesParser.FORMAT_UINT32)
            return parser.value
        }

    val timestampByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(timestampCode, BluetoothBytesParser.FORMAT_UINT32)
            parser.setIntValue(timestampLength, BluetoothBytesParser.FORMAT_UINT16)
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