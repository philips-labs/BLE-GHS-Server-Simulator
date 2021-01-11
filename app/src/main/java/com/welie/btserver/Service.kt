package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

internal interface Service {
    val service: BluetoothGattService
    fun onCharacteristicRead(central: Central, characteristic: BluetoothGattCharacteristic)
    fun onCharacteristicWrite(central: Central, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus
    fun onDescriptorRead(central: Central, descriptor: BluetoothGattDescriptor)
    fun onDescriptorWrite(central: Central, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus
    fun onNotifyingEnabled(central: Central, characteristic: BluetoothGattCharacteristic)
    fun onNotifyingDisabled(central: Central, characteristic: BluetoothGattCharacteristic)
    fun onCentralConnected(central: Central)
    fun onCentralDisconnected(central: Central)
}