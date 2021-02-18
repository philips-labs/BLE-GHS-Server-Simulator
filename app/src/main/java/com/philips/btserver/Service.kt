package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.GattStatus

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
    fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic)
    fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus
    fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor)
    fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus
    fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic)
    fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic)
    fun onCentralConnected(central: BluetoothCentral)
    fun onCentralDisconnected(central: BluetoothCentral)

    fun addListener(listener: ServiceListener)
    fun removeListener(listener: ServiceListener)
}
