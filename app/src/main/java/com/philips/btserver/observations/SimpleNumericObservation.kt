/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.observations

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
    override val classByte: ObservationClass = ObservationClass.SimpleNumeric

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            unitCode.writeOn(parser)
            // setFloatValue doesn't update offset... so created an extention setFloat that does
            parser.setFloatValue(value, valuePrecision)
            return parser.value
        }

}