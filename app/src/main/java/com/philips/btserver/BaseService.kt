package com.philips.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.min

internal abstract class BaseService(peripheralManager: BluetoothPeripheralManager) : Service, BluetoothServerConnectionListener {
    protected val peripheralManager: BluetoothPeripheralManager = Objects.requireNonNull(peripheralManager)
    private val listeners = mutableSetOf<ServiceListener>()

    fun getCccDescriptor() : BluetoothGattDescriptor {
            val cccDescriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
            cccDescriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            return cccDescriptor
        }

    fun getCudDescriptor(defaultValue: String): BluetoothGattDescriptor {
        val cudDescriptor = BluetoothGattDescriptor(CUD_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ)
        cudDescriptor.value = defaultValue.toByteArray(StandardCharsets.UTF_8)
        return cudDescriptor
    }

    protected fun notifyCharacteristicChanged(value: ByteArray, characteristic: BluetoothGattCharacteristic) {
        peripheralManager.notifyCharacteristicChanged(value, characteristic)
    }

    fun numberOfCentralsConnected(): Int {
        return peripheralManager.getConnectedCentrals().size
    }

    fun noCentralsConnected(): Boolean {
        return peripheralManager.getConnectedCentrals().size == 0
    }

    val minimalMTU: Int
        get() = min((peripheralManager.connectedCentrals.minOfOrNull { it.currentMtu } ?: MAX_MIN_MTU), MAX_MIN_MTU)

    abstract override val service: BluetoothGattService
    override fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        listeners.forEach { it.onCharacteristicRead(characteristic) }
    }
    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        listeners.forEach { it.onCharacteristicWrite(characteristic, value) }
        return GattStatus.SUCCESS
    }

    override fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor) {
        listeners.forEach { it.onDescriptorRead(descriptor) }
    }

    override fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
        listeners.forEach { it.onDescriptorWrite(descriptor, value) }
        return GattStatus.SUCCESS
    }

    override fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        listeners.forEach { it.onNotifyingEnabled(characteristic) }
    }

    override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        listeners.forEach { it.onNotifyingDisabled(characteristic) }
    }

    override fun onCentralConnected(central: BluetoothCentral) {
        listeners.forEach {it.onConnected(numberOfCentralsConnected())}
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        listeners.forEach {it.onDisconnected(numberOfCentralsConnected())}
    }

    override fun addListener(listener: ServiceListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ServiceListener) {
        listeners.remove(listener)
    }

    companion object {
        val CUD_DESCRIPTOR_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MAX_MIN_MTU = 23
    }
}