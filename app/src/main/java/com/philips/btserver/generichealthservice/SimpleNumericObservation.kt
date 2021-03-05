package com.philips.btserver.generichealthservice


import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothBytesParser.FORMAT_UINT16
import com.welie.blessed.BluetoothBytesParser.FORMAT_UINT32
import timber.log.Timber
import java.nio.ByteOrder
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