/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import java.util.*

data class SimpleNumericObservation(override val id: Short,
                                    override val type: ObservationType,
                                    override val value: Float,
                                    val valuePrecision: Int,
                                    override val unitCode: UnitCode,
                                    override val timestamp: Date): Observation() {

    override val valueByteArray: ByteArray
        get() { return encodeTLV(valueCode, valueLength, value, valuePrecision) }

    companion object {
        internal const val valueCode = 0x00010A56
        internal const val valueLength = 4
    }
}