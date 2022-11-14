/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.observations

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder
import java.util.*

data class CompoundNumericObservation(
    override val id: Short,
    override val type: ObservationType,
    override val value: Array<SimpleNumericValue>,
    override val unitCode: UnitCode,
    override val timestamp: Date
) : Observation() {

    override val classByte: ObservationClass = ObservationClass.Compound

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setUInt8(value.size)
            value.forEach {
                it.type.writeOn(parser)
                parser.setUInt8(ObservationComponentValueType.NUMERIC.value)
                it.unitCode.writeOn(parser)
                parser.setFloatValue(it.value, type.numericPrecision())
            }
            return parser.value
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompoundNumericObservation

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