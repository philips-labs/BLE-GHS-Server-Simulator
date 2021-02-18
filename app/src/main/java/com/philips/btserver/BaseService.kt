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

internal abstract class BaseService(peripheralManager: BluetoothPeripheralManager): BluetoothServerConnectionListener {
    val peripheralManager: BluetoothPeripheralManager = Objects.requireNonNull(peripheralManager)

    fun getCccDescriptor(): BluetoothGattDescriptor {
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
        get() = min((peripheralManager.connectedCentrals.minOfOrNull { it.currentMtu }
                ?: MAX_MIN_MTU), MAX_MIN_MTU)

    abstract val service: BluetoothGattService

    open fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
    }

    open fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        return GattStatus.SUCCESS
    }

    open fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor) {}

    open fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
        return GattStatus.SUCCESS
    }

    open fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {}

    open fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {}

    open fun onCentralConnected(central: BluetoothCentral) {}

    open fun onCentralDisconnected(central: BluetoothCentral) {}


    companion object {
        val CUD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MAX_MIN_MTU = 23
    }
}
