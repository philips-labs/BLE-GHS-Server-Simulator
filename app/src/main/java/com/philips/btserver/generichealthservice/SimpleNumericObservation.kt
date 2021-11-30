/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import java.util.*

data class SimpleNumericObservation(
    override val id: Short,
    override val type: ObservationType,
    override val value: Float,
    val valuePrecision: Int,
    override val unitCode: UnitCode,
    override val timestamp: Date
) : Observation() {

    // This is the nibble that represents the observation class in the header bytes
    override val classByte: UInt = 0u

    // This is the nibble that represents the presence of attributes  header bytes
    override val attributeFlags: UInt = 0u

    override val valueByteArray: ByteArray
        get() {
            return encodeTLV(
                ObservationValueType.MDC_ATTR_NU_VAL_OBS.value,
                ObservationValueType.valueByteLength,
                value,
                valuePrecision
            )
        }
}