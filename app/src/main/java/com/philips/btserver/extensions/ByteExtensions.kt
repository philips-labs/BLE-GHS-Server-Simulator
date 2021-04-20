/*
 * Copyright (c) Koninklijke Philips N.V. 2020.
 * All rights reserved.
 */
package com.philips.btserver.extensions

import java.util.*
import kotlin.math.ceil

fun Byte.asHexString(): String {
    var hexString = this.toUINT8().toString(16).toUpperCase(Locale.ROOT)
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
 * Merge the ByteArrays in the receiver into the returned ByteArray
 * This could be done with a fold function, but the concat of each cause a lot of allocs
 * So instead the method creates a large result ByteArray and copies each into it.
 * The "optimized" Kotlin implementation is kept commented out for comparison and
 * used fold instead of reduce so that empty list doesn't cause an exception
 */
fun List<ByteArray>.merge(): ByteArray {
    return this.fold(byteArrayOf(), { result, bytes -> result + bytes })
}

fun ByteArray.asBLEDataSegments(segmentSize: Int): List<ByteArray> {
    val numSegs = ceil(size.toFloat().div(segmentSize)).toInt()
    val result = ArrayList<ByteArray>(numSegs)
    for (i in 0 until numSegs) {
        // Compute the segment header byte (first/last seg, seg number)
        val segmentNumber = i + 1
        var segByte = segmentNumber shl 2
        segByte = segByte or if (segmentNumber == 1) 0x01 else 0x0
        segByte = segByte or if (segmentNumber == numSegs) 0x02 else 0x0

        // Get the next segment data
        val startIndex = i * segmentSize
        val endIndex = (startIndex + segmentSize).coerceAtMost(lastIndex + 1)
        val length = endIndex - startIndex
        val segment = ByteArray(length + 1)
        val segmentData = copyOfRange(startIndex, endIndex)
        segment[0] = segByte.toByte()
        System.arraycopy(segmentData, 0, segment, 1, length)
        result.add(segment)
    }
    return result
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