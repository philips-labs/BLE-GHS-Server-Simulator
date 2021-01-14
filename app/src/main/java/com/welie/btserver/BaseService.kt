package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.welie.btserver.GattStatus
import java.nio.charset.StandardCharsets
import java.util.*

internal abstract class BaseService(peripheralManager: PeripheralManager) : Service {
    protected val peripheralManager: PeripheralManager

    fun getCccDescriptor() : BluetoothGattDescriptor {
            val cccDescriptor = BluetoothGattDescriptor(PeripheralManager.CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
            cccDescriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            return cccDescriptor
        }

    fun getCudDescriptor(defaultValue: String): BluetoothGattDescriptor {
        val cudDescriptor = BluetoothGattDescriptor(PeripheralManager.CUD_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ)
        cudDescriptor.value = defaultValue.toByteArray(StandardCharsets.UTF_8)
        return cudDescriptor
    }

    protected fun notifyCharacteristicChanged(characteristic: BluetoothGattCharacteristic) {
        peripheralManager.notifyCharacteristicChanged(characteristic)
    }

    fun noCentralsConnected(): Boolean {
        return peripheralManager.getConnectedCentrals().size == 0
    }

    abstract override val service: BluetoothGattService
    override fun onCharacteristicRead(central: Central, characteristic: BluetoothGattCharacteristic) {}
    override fun onCharacteristicWrite(central: Central, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        return GattStatus.SUCCESS
    }

    override fun onDescriptorRead(central: Central, descriptor: BluetoothGattDescriptor) {}
    override fun onDescriptorWrite(central: Central, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
        return GattStatus.SUCCESS
    }

    override fun onNotifyingEnabled(central: Central, characteristic: BluetoothGattCharacteristic) {}
    override fun onNotifyingDisabled(central: Central, characteristic: BluetoothGattCharacteristic) {}
    override fun onCentralConnected(central: Central) {}
    override fun onCentralDisconnected(central: Central) {}

    init {
        this.peripheralManager = Objects.requireNonNull(peripheralManager)
    }
}