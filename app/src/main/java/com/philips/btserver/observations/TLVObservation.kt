package com.philips.btserver.observations

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder
import java.util.*

data class TLVObservation(
    override val id: Short,
    override val type: ObservationType,
    override val value: List<TLValue>,
    override val timestamp: Date
) : Observation() {

    override val unitCode: UnitCode
        get() = UnitCode.UNKNOWN_CODE

    override val classByte: ObservationClass = ObservationClass.TLVEncoded

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setIntValue(value.size, BluetoothBytesParser.FORMAT_UINT8)
            value.forEach {
                val bytesToSend = it.asGHSBytes()
                parser.setByteArray(bytesToSend)
            }
            return parser.value
        }

}
