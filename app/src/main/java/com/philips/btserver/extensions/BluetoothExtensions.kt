/*
 * Copyright (c) Koninklijke Philips N.V. 2022.
 * All rights reserved.
 */
package com.philips.btserver.extensions

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.philips.btserver.BaseService


fun BluetoothGattCharacteristic.isNotifyEnabled(): Boolean {
    return getDescriptor(BaseService.CCC_DESCRIPTOR_UUID)?.isNotifyValue() ?: false
}

fun BluetoothGattCharacteristic.isIndicateEnabled(): Boolean {
    return getDescriptor(BaseService.CCC_DESCRIPTOR_UUID)?.isIndicateValue() ?: false
}

fun BluetoothGattDescriptor.isNotifyValue(): Boolean {
    return value.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
}
fun BluetoothGattDescriptor.isIndicateValue(): Boolean {
    return value.equals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
}
