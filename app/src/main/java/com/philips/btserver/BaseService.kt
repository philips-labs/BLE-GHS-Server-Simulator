/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.philips.btserver.generichealthservice.isBonded
import com.welie.blessed.*
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min


abstract class BaseService(peripheralManager: BluetoothPeripheralManager) : BluetoothServerConnectionListener {
    val peripheralManager: BluetoothPeripheralManager = Objects.requireNonNull(peripheralManager)


    protected val disconnectedBondedCentrals = mutableSetOf<String>()
    protected val bondedCentralsToNotify = mutableMapOf<BluetoothGattCharacteristic, MutableSet<String>>()

    private var characteristicValues = mutableMapOf<UUID, ByteArray>()

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

    // TODO The Kotlin compiler is having an issue finding String.asByteArray() and resolves to Any.asByteArray()
    protected open fun convertToByteArray(string: String): ByteArray {
        val parser = BluetoothBytesParser()
        parser.setString(string)
        return parser.value
    }

    protected fun notifyCharacteristicChanged(value: ByteArray, characteristic: BluetoothGattCharacteristic): Boolean {
        updateDisconnectedBondedCentralsToNotify(characteristic)
        return peripheralManager.notifyCharacteristicChanged(value, characteristic)
    }


    protected fun notifyCharacteristicChanged(value: ByteArray, characteristic: BluetoothGattCharacteristic, centrals: List<BluetoothCentral>): Boolean {
        updateDisconnectedBondedCentralsToNotify(characteristic)
        var success = true
        centrals.forEach {
            if (!peripheralManager.notifyCharacteristicChanged(value, it, characteristic )) success = false
        }
        return success
    }

    protected fun notifyCharacteristicChanged(value: ByteArray, central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): Boolean {
        updateDisconnectedBondedCentralsToNotify(characteristic)
        return peripheralManager.notifyCharacteristicChanged(value, central, characteristic )
    }

    protected fun notifyCharacteristicChangedSkipCentral(value: ByteArray, central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): Boolean {
        val success = notifyCharacteristicChangedFilterCentrals(
            value,
            { connCen -> central.let { connCen.address != it.address } },
            characteristic)
        updateDisconnectedBondedCentralsToNotify(characteristic)
        return success
    }


    protected fun notifyCharacteristicChangedFilterCentrals(value: ByteArray, filter: (testCentral: BluetoothCentral) -> Boolean, characteristic: BluetoothGattCharacteristic): Boolean {
        var success = true
        getConnectedCentrals().filter(filter).forEach {
            if (!peripheralManager.notifyCharacteristicChanged(value, it, characteristic)) success = false
        }
        return success
    }

    fun getConnectedCentrals(): Set<BluetoothCentral>{
        return peripheralManager.connectedCentrals
    }

    fun noCentralsConnected(): Boolean {
        return peripheralManager.getConnectedCentrals().isEmpty()
    }

    fun centralsToNotifyUpdateFromCentral(central: BluetoothCentral?): List<BluetoothCentral> {
        return getConnectedCentrals().filter { connCen ->
            central?.let { connCen.address != it.address } ?: true
        }
    }

    internal fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic): ByteArray? {
        return characteristicValues[characteristic.uuid]
    }

    internal fun setCharacteristicValue(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        characteristicValues[characteristic.uuid] = value
        characteristic.value = value
    }

    internal fun setCharacteristicValueAndNotify(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        setCharacteristicValue(characteristic, value)
        return peripheralManager.notifyCharacteristicChanged(value, characteristic)
    }

    val minimalMTU: Int
        get() = maxOf((peripheralManager.connectedCentrals.minOfOrNull { it.currentMtu }
                ?: MAX_MIN_MTU), MAX_MIN_MTU)

    abstract val service: BluetoothGattService

    /*
     * onCharacteristicRead is a non-abstract method with an empty body to have a default behavior to do nothing
     * Subclasses do not need to provide an implementation
     */
    open fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse  {
        // To be implemented by sub class
        return if (characteristicValues.containsKey(characteristic.uuid))
         ReadResponse(GattStatus.SUCCESS, characteristicValues[characteristic.uuid])
        else ReadResponse(GattStatus.REQUEST_NOT_SUPPORTED, byteArrayOf())
    }

    open fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        characteristicValues[characteristic.uuid] = value
        return GattStatus.SUCCESS
    }

    open fun onCharacteristicWriteCompleted(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {}

    /*
     * onDescriptorRead is a non-abstract method with an empty body to have a default behavior to do nothing
     * Subclasses do not need to provide an implementation
     */
    open fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor): ReadResponse  {
        // To be implemented by sub class
        return ReadResponse(GattStatus.SUCCESS, byteArrayOf())
    }

    open fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
        descriptor.value = value
        return GattStatus.SUCCESS
    }

    /*
     * onNotifyingEnabled is a non-abstract method with an empty body to have a default behavior to do nothing
     * Subclasses do not need to provide an implementation
     */
    open fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        // To be implemented by sub class
    }

    /*
     * onNotifyingDisabled is a non-abstract method with an empty body to have a default behavior to do nothing
     * Subclasses do not need to provide an implementation
     */
    open fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        // To be implemented by sub class
    }


    /**
     * A notification has been sent to a central
     *
     * @param bluetoothCentral the central
     * @param value the value of the notification
     * @param characteristic the characteristic for which the notification was sent
     * @param status the status of the operation
     */
    open fun onNotificationSent(
        bluetoothCentral: BluetoothCentral,
        value: ByteArray?,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
    }

    /*
     * onCentralConnected is a non-abstract method with an empty body to have a default behavior to do nothing
     * Subclasses do not need to provide an implementation
     */
    override fun onCentralConnected(central: BluetoothCentral) {
        if(hasBondedCentralReconnected(central)) bondedReconnected(central)
    }

    private fun hasBondedCentralReconnected(central: BluetoothCentral): Boolean {
        return disconnectedBondedCentrals.contains(central.address)
    }


    private fun bondedReconnected(central: BluetoothCentral) {
        Timber.i("Reconnecting bonded central: ${central.address} to notify list is: ${bondedCentralsToNotify.values}")
        disconnectedBondedCentrals.remove(central.address)
        bondedCentralsToNotify.forEach {
            if (it.value.contains(central.address)) {
                Timber.i("Notifiying reconnected bonded central: ${central.address} char: ${it.key.uuid}")
                it.value.remove(central.address)
                Executors.newSingleThreadScheduledExecutor().schedule({
                    notifyReconnectedBondedCentral(central, it.key)
                }, 1, TimeUnit.SECONDS)
                // TODO if the set is now empty should we remove the entry from the map?
            }
        }
    }

    protected fun updateDisconnectedBondedCentralsToNotify(characteristic: BluetoothGattCharacteristic) {
        val centrals = bondedCentralsToNotify.getOrPut(characteristic, {mutableSetOf()})
        centrals.addAll(disconnectedBondedCentrals)
    }

    private fun notifyReconnectedBondedCentral(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        notifyCharacteristicChanged(characteristic.value, central, characteristic)
    }

    /*
     * onCentralDisconnected is a non-abstract method with an empty body to have a default behavior to do nothing
     * Subclasses do not need to provide an implementation
     */
    override fun onCentralDisconnected(central: BluetoothCentral) {
        if(central.isBonded()) {
            Timber.i("Disconnecting bonded central: $central")
            disconnectedBondedCentrals.add(central.address)
        }
    }

    /**
     * Send ByteArray bytes and do a BLE notification over the characteristic.
     */
    fun sendBytesAndNotify(bytes: ByteArray, characteristic: BluetoothGattCharacteristic) {
        notifyCharacteristicChanged(bytes, characteristic)
    }

    /**
     * Send ByteArray bytes and do a BLE notification over the characteristic.
     */
    fun sendBytesAndNotify(bytes: ByteArray, characteristic: BluetoothGattCharacteristic, centrals: List<BluetoothCentral>) {
        notifyCharacteristicChanged(bytes, characteristic, centrals)
    }

    companion object {
        val CUD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MAX_MIN_MTU = 23
    }


    protected open fun initCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        description: String
    ) {
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(getCccDescriptor())
        characteristic.addDescriptor(getCudDescriptor(description))
    }

}
