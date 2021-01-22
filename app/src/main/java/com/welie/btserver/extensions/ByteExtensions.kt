/*
 * Copyright (c) Koninklijke Philips N.V. 2020.
 * All rights reserved.
 */
package com.welie.btserver.extensions

import com.welie.btserver.BluetoothBytesParser

fun Byte.asHexString(): String {
    var hexString = this.toUINT8().toString(16).toUpperCase()
    if (this.toUINT8() < 16) hexString = "0$hexString"
    return hexString
}

fun ByteArray.formatHexBytes(seperator: String?): String {
    var resultString = ""
    for ((index, value) in this.iterator().withIndex()) {
        resultString += value.asHexString()
        if (seperator != null && index < (this.size - 1)) resultString += seperator
    }
    return resultString
}

fun ByteArray.asHexString(): String {
    return this.formatHexBytes(null)
}

fun ByteArray.asAsciiString(): String {
    var resultString = ""
    forEach {
        resultString += it.toChar()
    }
    return resultString
}

fun List<Byte>.formatHexBytes(seperator: String?): String {
    var resultString = ""
    for ((index, value) in iterator().withIndex()) {
        resultString += value.asHexString()
        if (seperator != null && index < (this.size - 1)) resultString += seperator
    }
    return resultString
}

fun Byte.toUINT8(): Int {
    return this.toInt() and 0xFF
}


/*
 * Read bytes and return the ByteArray of the length passed in.  This will increment the offset
 *
 * @return The DateTime read from the bytes. This will cause an exception if bytes run past end. Will return 0 epoch if unparsable
 */
fun BluetoothBytesParser.getByteArray(length: Int): ByteArray {
    val array = bytes.copyOfRange(offset, offset + length)
    offset += length
    return array
}