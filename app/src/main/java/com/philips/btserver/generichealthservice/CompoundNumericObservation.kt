/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import java.util.*

data class CompoundNumericObservation(
    override val id: Short,
    override val type: ObservationType,
    override val value: Array<Pair<ObservationType, Float>>,
    val valuePrecision: Int,
    override val unitCode: UnitCode,
    override val timestamp: Date
) : Observation() {

    override val valueByteArray: ByteArray
        get() {
            return encodeTLV(
                ObservationValueType.MDC_ATTR_NU_CMPD_VAL_OBS.value,
                ObservationValueType.valueByteLength,
                // TODO: Need to handle second value. Just here to get compiling and testing other stuff
                value.first().second,
                valuePrecision
            )
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompoundNumericObservation

        if (id != other.id) return false
        if (type != other.type) return false
        if (!value.contentEquals(other.value)) return false
        if (valuePrecision != other.valuePrecision) return false
        if (unitCode != other.unitCode) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + valuePrecision
        result = 31 * result + unitCode.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}