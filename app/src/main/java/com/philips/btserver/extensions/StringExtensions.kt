/*
 * Copyright (c) Koninklijke Philips N.V. 2020.
 * All rights reserved.
 */
package com.philips.btserver.extensions

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * This is the same as toByteArray() available in the JVM and here for multiplatform support
 *
 * @return the receiver converted into a ByteArray with each chaacter byte
 */
fun String.asByteArray(): ByteArray {
    val result = ByteArray(this.length)
    var i = 0
    this.iterator().forEach {
        result[i] = it.toByte()
        i++
    }
    return result
}

fun String.convertHexStringtoByteArray(): ByteArray {
    val result = ByteArray(this.length / 2)
    for (i in result.indices) {
        val index = i * 2
        val j: Int = this.substring(index, index + 2).toInt(16)
        result[i] = j.toByte()
    }
    return result
}

/**
 * Return the float receiver as a string display with numOfDec after the decimal (rounded)
 * (e.g. 35.72 with numOfDec = 1 will be 35.7, 35.78 with numOfDec = 2 will be 35.80)
 *
 * @param numOfDec number of decimal places to show (receiver is rounded to that number)
 * @return the String representation of the receiver up to numOfDec decimal places
 */
fun Float.toString(numOfDec: Int): String {
    val integerDigits = this.toInt()
    val floatDigits = ((this - integerDigits) * 10f.pow(numOfDec)).roundToInt()
    return "${integerDigits}.${floatDigits}"
}
