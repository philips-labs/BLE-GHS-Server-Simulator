package com.philips.btserver.observations

data class CompoundStateEventValue(
    val supportedMaskBits: ByteArray,
    val stateOrEventBits: ByteArray,
    val value: ByteArray,
) {

    fun asGHSBytes(): ByteArray {
        val result = byteArrayOf(value.size.toByte())
        return result + supportedMaskBits + stateOrEventBits + value
    }
}

