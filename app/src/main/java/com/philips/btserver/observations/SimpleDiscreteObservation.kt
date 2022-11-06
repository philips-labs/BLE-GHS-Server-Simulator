package com.philips.btserver.observations

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder
import java.util.*

data class SimpleDiscreteObservation(
    override val id: Short,
    override val type: ObservationType,
    override val value: Int,
    override val timestamp: Date
) : Observation() {

    // This is the nibble that represents the observation class in the header bytes
    override val classByte: ObservationClass = ObservationClass.SimpleDiscreet

    // BundledObservations have no unit code (each observation does)
    override val unitCode: UnitCode
        get() = UnitCode.UNKNOWN_CODE

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setUInt32(value)
            return parser.value
        }

}