/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.observations

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder
import java.util.*

data class SampleArrayObservation(
    override val id: Short,
    override val type: ObservationType,
    override val value: ByteArray,
    override val unitCode: UnitCode,
    override val timestamp: Date
) : Observation() {

    var scaleFactor: Float = 1F
    var scaleOffset: Float = 0F
    var samplePeriodSeconds: Float = 1F
    var samplesPerPeriod: UByte = 0xff.toUByte()
    var bytesPerSample: UByte = 1u

    override val classByte: ObservationClass = ObservationClass.RealTimeSampleArray

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            UnitCode.MDC_DIM_DIMLESS.writeOn(parser)
            parser.setFloatValue(scaleFactor, 2)
            parser.setFloatValue(scaleOffset, 2)
            parser.setSInt32((value.minOfOrNull {(it * scaleFactor) + scaleOffset} ?: 0).toInt())
            parser.setSInt32((value.maxOfOrNull {(it * scaleFactor) + scaleOffset} ?: 0).toInt())
            parser.setFloatValue(samplePeriodSeconds, 2)

            parser.setUInt8(samplesPerPeriod.toInt())
            parser.setUInt8(bytesPerSample.toInt())
            parser.setUInt32(value.size)

            return parser.value + value
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
