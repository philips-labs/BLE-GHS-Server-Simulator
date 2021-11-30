/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder
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

    override val fixedValueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(unitCode.value, BluetoothBytesParser.FORMAT_UINT32)
            parser.setFloatValue(value, valuePrecision)
            return parser.value
        }

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