package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService


interface ServiceListener {
    fun onCharacteristicRead(characteristic: BluetoothGattCharacteristic)
    fun onCharacteristicWrite(characteristic: BluetoothGattCharacteristic, value: ByteArray)
    fun onDescriptorRead(descriptor: BluetoothGattDescriptor)
    fun onDescriptorWrite(descriptor: BluetoothGattDescriptor, value: ByteArray)
    fun onNotifyingEnabled(characteristic: BluetoothGattCharacteristic)
    fun onNotifyingDisabled(ccharacteristic: BluetoothGattCharacteristic)
    fun onConnected(numberOfConnections: Int)
    fun onDisconnected(numberOfConnections: Int)
}

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

    fun addListener(listener: ServiceListener)
    fun removeListener(listener: ServiceListener)
}
