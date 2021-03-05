package com.philips.btserver.generichealthservice

import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.nio.ByteOrder
import java.util.*

data class SampleArrayObservation(override val id: Short,
                                  override val type: ObservationType,
                                  override val value: ByteArray,
                                  override val unitCode: UnitCode,
                                  override val timestamp: Date): Observation() {

    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
            parser.setIntValue(valueCode, BluetoothBytesParser.FORMAT_UINT32)
            parser.setIntValue(value.size, BluetoothBytesParser.FORMAT_UINT16)
            return parser.value + value
        }

    companion object {
        internal const val valueCode = 0x0001096E
    }
}
