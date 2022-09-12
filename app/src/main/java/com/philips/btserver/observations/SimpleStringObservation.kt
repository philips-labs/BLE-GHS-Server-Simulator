package com.philips.btserver.observations

import com.welie.blessed.BluetoothBytesParser
import java.lang.Integer.max
import java.lang.Integer.min
import java.nio.ByteOrder
import java.util.*

data class SimpleStringObservation(
    override val id: Short,
    override val type: ObservationType,
    override val value: String,
    override val timestamp: Date
) : Observation() {

    // This is the nibble that represents the observation class in the header bytes
    override val classByte: ObservationClass = ObservationClass.String

    override val unitCode: UnitCode
        get() = UnitCode.UNKNOWN_CODE

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setIntValue(min(value.length, 0xffff), BluetoothBytesParser.FORMAT_UINT16)
            parser.setString(value.take(0xffff))
            return parser.value
        }

}