/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import com.philips.btserver.extensions.setFloat
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
    override val classByte: Int = 0

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            unitCode.writeOn(parser)
            // TODO setFloatValue doesn't update offset... this works because we're not adding anything after
            parser.setFloat(value, valuePrecision)
            return parser.value
        }

}