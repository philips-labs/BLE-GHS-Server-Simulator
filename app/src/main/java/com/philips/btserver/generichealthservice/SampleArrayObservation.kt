/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import com.welie.blessed.BluetoothBytesParser
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
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SampleArrayObservation

        if (id != other.id) return false
        if (type != other.type) return false
        if (!value.contentEquals(other.value)) return false
        if (unitCode != other.unitCode) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + unitCode.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
