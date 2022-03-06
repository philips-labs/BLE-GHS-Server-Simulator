/*
 * Copyright (c) Koninklijke Philips N.V. 2020.
 * All rights reserved.
 */
package com.philips.btserver.extensions

import com.philips.btserver.util.CRC16
import com.welie.blessed.BluetoothBytesParser
import java.util.*
import kotlin.math.ceil

fun Byte.asHexString(): String {
    var hexString = this.toUINT8().toString(16).uppercase(Locale.ROOT)
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

fun ByteArray.asFormattedHexString(): String {
    return this.formatHexBytes(" ")
}

fun ByteArray.asHexString(): String {
    return this.formatHexBytes(null)
}

fun ByteArray.asAsciiString(): String {
    var resultString = ""
    forEach {
        resultString += it.toInt().toChar()
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
 * Merge the ByteArrays in the receiver into the returned ByteArray
 * This could be done with a fold function, but the concat of each cause a lot of allocs
 * So instead the method creates a large result ByteArray and copies each into it.
 * The "optimized" Kotlin implementation is kept commented out for comparison and
 * used fold instead of reduce so that empty list doesn't cause an exception
 */
fun List<ByteArray>.merge(): ByteArray {
    return this.fold(byteArrayOf(), { result, bytes -> result + bytes })
}

/*
 * Return the receiver as an array of BLE segments (with segment headers). This method returns a pair:
 * First object in the pair is the list of byte arrays that make up the segment. Next item in the pair
 * is the next segment number that will be used for next segments... this is because segment numbers
 * persist during a device connection.
 */
fun ByteArray.asBLEDataSegments(segmentSize: Int, startingSegNumber: Int = 0): Pair<List<ByteArray>, Int> {
    val numSegs = ceil(size.toFloat().div(segmentSize)).toInt()
    val result = ArrayList<ByteArray>(numSegs)
    var segmentNumber = startingSegNumber
    for (i in 0 until numSegs) {
        // Compute the segment header byte (first/last seg, seg number)
        var segByte = segmentNumber shl 2
        segByte = segByte or if (i == 0) 0x01 else 0x0
        segByte = segByte or if (i == numSegs - 1) 0x02 else 0x0

        // Get the next segment data
        val startIndex = i * segmentSize
        val endIndex = (startIndex + segmentSize).coerceAtMost(lastIndex + 1)
        val length = endIndex - startIndex
        val segment = ByteArray(length + 1)
        val segmentData = copyOfRange(startIndex, endIndex)
        segment[0] = segByte.toByte()
        System.arraycopy(segmentData, 0, segment, 1, length)
        result.add(segment)
        if (++segmentNumber > 63) segmentNumber = 0
    }
    return Pair(result, segmentNumber)
}

fun ByteArray.withLengthPrefix(): ByteArray {
    // Include length as first 2 bytes. Note length DOES NOT include the length bytes
    val length = this.size
    return listOf(
        byteArrayOf(length.asMaskedByte(), (length shr 8).asMaskedByte()),
        this,
    ).merge()
}

fun Int.asMaskedByte(): Byte {
    return (this and 0xFF).toByte()
}

private fun ByteArray.bleCRC(): ByteArray {
    val crc = CRC16.CCITT_Kermit(this, 0, this.size)
    return byteArrayOf(crc.asMaskedByte(), (crc shr 8).asMaskedByte())
}

fun ByteArray.fillWith(action: (Int) -> Byte) {
    for (i in 0..size - 1) {
        this[i] = action(i)
    }
}

fun ByteArray.findFirst(sequence: ByteArray, startFrom: Int = 0): Int {
    if (sequence.isEmpty()) throw IllegalArgumentException("non-empty byte sequence is required")
    if (startFrom < 0) throw IllegalArgumentException("startFrom must be non-negative")
    var matchOffset = 0
    var start = startFrom
    var offset = startFrom
    while (offset < size) {
        if (this[offset] == sequence[matchOffset]) {
            if (matchOffset++ == 0) start = offset
            if (matchOffset == sequence.size) return start
        } else
            matchOffset = 0
        offset++
    }
    return -1
}

fun BluetoothBytesParser.setLongValue(value: Long): Boolean {
    val result = setLong(value)
    if (result) { offset += 8 }
    return result
}

fun BluetoothBytesParser.setFloat(value: Float, precision: Int): Boolean {
    val result = setFloatValue(value, precision)
    if (result) { offset += 4 }
    return result
}
