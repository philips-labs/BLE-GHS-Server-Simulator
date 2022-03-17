package com.philips.btserver.generichealthservice

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder
import java.util.*

data class BundledObservation(
override val id: Short,
override val value: List<Observation>,
override val timestamp: Date
) : Observation() {
    // BundledObservations have no observation type
    override var type: ObservationType = ObservationType.UNKNOWN_TYPE

    // BundledObservations have no unit code (each observation does)
    override val unitCode: UnitCode
        get() = UnitCode.UNKNOWN_CODE

    override val classByte: ObservationClass = ObservationClass.ObservationBundle   // Bundled observation

    /*
     * For bundled observations value bytes are a byte with number of observations
     * followed by bytes for each observation
     */
    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setIntValue(value.size, BluetoothBytesParser.FORMAT_UINT8)
            value.forEach {
                // Based on the current 0.7 spec each bundled observation byte array included
                // also includes the length of the observation byte array (3.2.1.2)
                val bytesToSend = it.ghsByteArray
                parser.setByteArray(bytesToSend)
            }
            return parser.value
        }

    // Let all the observations bundled know they're bundled for byte array encoding purposes
    init {
        value.forEach { it.isBundledObservation = true }
    }
}

/**
 * Set byte array to the bytes at current offset
 *
 * @param byteArray byteArray to be added to parser's byte array
 */
fun BluetoothBytesParser.setByteArray(bytes: ByteArray) {
    setByteArray(bytes, offset)
}

/**
 * Set byte array to a string at specified offset position
 *
 * @param bytes  byte array to be added to byte array
 * @param offset the offset to place the string at
 */
fun BluetoothBytesParser.setByteArray(bytes: ByteArray, offset: Int) {
    expandArray(offset + bytes.size)
    System.arraycopy(bytes, 0, getValue(), offset, bytes.size)
    setOffset(offset + bytes.size)
}

/*
 * This is the same as prepareArray which is private
 */
fun BluetoothBytesParser.expandArray(neededLength: Int) {
    if (getValue() == null) setValue(ByteArray(neededLength))
    if (neededLength > getValue().size) {
        val largerByteArray = ByteArray(neededLength)
        System.arraycopy(getValue(), 0, largerByteArray, 0, getValue().size)
        setValue(largerByteArray)
    }
}
