package com.philips.btserver.observations

import android.os.Parcelable
import com.philips.btserver.extensions.asByteArray
import com.welie.blessed.BluetoothBytesParser

data class TLValue(val type: ObservationType, val value: Any) {

    fun asGHSBytes() : ByteArray {
        val parser = BluetoothBytesParser()
        parser.setByteArray(type.asGHSByteArray())
        val valueBytes = value.asByteArray() // valueByteArray()
        parser.setIntValue(valueBytes.size, BluetoothBytesParser.FORMAT_UINT16)
        parser.setIntValue(formatType(), BluetoothBytesParser.FORMAT_UINT8)
        parser.setByteArray(valueBytes)
        return parser.value
    }

    fun valueByteArray(): ByteArray {
        return when (value) {
            is String -> value.asByteArray()
            is Int -> value.asByteArray()
            is Float -> value.asByteArray()
            else -> byteArrayOf()
        }
    }

    fun formatType(): Int {
        return when (value) {
            is String -> BluetoothValueFormatType.UTF8S.value
            is Int -> BluetoothValueFormatType.UINT32.value
            is Float -> BluetoothValueFormatType.FLOAT32.value
            else -> BluetoothValueFormatType.STRUCT.value
        }
    }
}

fun Any.asByteArray(): ByteArray {
    return byteArrayOf()
}

fun Float.asByteArray(): ByteArray {
    val parser = BluetoothBytesParser()
    parser.setFloatValue(this, 4)
    return parser.value
}