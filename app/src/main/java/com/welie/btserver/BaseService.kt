package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.welie.btserver.GattStatus
import java.nio.charset.StandardCharsets
import java.util.*

internal abstract class BaseService(peripheralManager: PeripheralManager) : Service {
    protected val peripheralManager: PeripheralManager
    private val listeners = mutableSetOf<ServiceListener>()

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

    fun numberOfCentralsConnected(): Int {
        return peripheralManager.getConnectedCentrals().size
    }

    fun noCentralsConnected(): Boolean {
        return peripheralManager.getConnectedCentrals().size == 0
    }

    abstract override val service: BluetoothGattService
    override fun onCharacteristicRead(central: Central, characteristic: BluetoothGattCharacteristic) {
        listeners.forEach { it.onCharacteristicRead(characteristic) }
    }
    override fun onCharacteristicWrite(central: Central, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        listeners.forEach { it.onCharacteristicWrite(characteristic, value) }
        return GattStatus.SUCCESS
    }

    override fun onDescriptorRead(central: Central, descriptor: BluetoothGattDescriptor) {
        listeners.forEach { it.onDescriptorRead(descriptor) }
    }

    override fun onDescriptorWrite(central: Central, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
        listeners.forEach { it.onDescriptorWrite(descriptor, value) }
        return GattStatus.SUCCESS
    }

    override fun onNotifyingEnabled(central: Central, characteristic: BluetoothGattCharacteristic) {
        listeners.forEach { it.onNotifyingEnabled(characteristic) }
    }

    override fun onNotifyingDisabled(central: Central, characteristic: BluetoothGattCharacteristic) {
        listeners.forEach { it.onNotifyingDisabled(characteristic) }
    }

    override fun onCentralConnected(central: Central) {
        listeners.forEach {it.onConnected(numberOfCentralsConnected())}
    }

    override fun onCentralDisconnected(central: Central) {
        listeners.forEach {it.onDisconnected(numberOfCentralsConnected())}
    }

    override fun addListener(listener: ServiceListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ServiceListener) {
        listeners.remove(listener)
    }

    init {
        this.peripheralManager = Objects.requireNonNull(peripheralManager)
    }
}