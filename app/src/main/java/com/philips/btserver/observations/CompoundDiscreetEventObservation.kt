package com.philips.btserver.observations

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder
import java.util.*

data class CompoundDiscreetEventObservation(
    override val id: Short,
    override val type: ObservationType,
    override val value: List<ObservationEvent>,
    override val timestamp: Date
) : Observation() {

    // This is the nibble that represents the observation class in the header bytes
    override val classByte: ObservationClass = ObservationClass.CompoundDiscreteEvent

    override val unitCode: UnitCode
        get() = UnitCode.UNKNOWN_CODE

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setIntValue(value.size, BluetoothBytesParser.FORMAT_UINT8)
            value.forEach { parser.setIntValue(it.value, BluetoothBytesParser.FORMAT_UINT32) }
            return parser.value
        }

}
