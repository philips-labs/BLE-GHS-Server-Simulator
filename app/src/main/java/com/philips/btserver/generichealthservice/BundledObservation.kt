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
    override val type: ObservationType
        get() = ObservationType.UNKNOWN_TYPE

    // BundledObservations have no unit code (each observation does)
    override val unitCode: UnitCode
        get() = UnitCode.UNKNOWN_CODE

    // This is the nibble that represents the observation class in the header bytes
    override val classByte: Int = 0x0F   // Bundled observation

    /*
    Bits 0-3:0xF is Observation Bundle with shared attributes
    Bits 5-14: attribute presence
        5.	Observation type present
        6.	Time stamp present
        7.	Measurement duration present
        8.	Measurement Status present
        9.	Object Id present
        10.	Patient present
        11.	Supplemental Information present
        12.	Derived-from present
        13.	hasMember present
        14.	TLVs present
    Bits 15-32: Reserved
     */
    override val attributeFlags: Int = 0x0420 or classByte

    /*
     * For bundled observations value bytes are a byte with number of observations
     * followed by bytes for each observation
     */
    override val valueByteArray: ByteArray
        get() = fixedValueByteArray

    override val fixedValueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setIntValue(value.size, BluetoothBytesParser.FORMAT_UINT8)
            value.forEach { parser.setByteArray(it.fixedFormatByteArray) }
            return parser.value
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
