package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

import com.welie.btserver.extensions.asBLEDataSegments
import com.welie.btserver.extensions.asHexString
import com.welie.btserver.extensions.merge
import com.welie.btserver.generichealthservice.Observation

import timber.log.Timber
import java.util.*

internal class GenericHealthSensorService(peripheralManager: PeripheralManager) : BaseService(peripheralManager) {
    override val service = BluetoothGattService(GHS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    private val observationCharacteristic = BluetoothGattCharacteristic(OBSERVATION_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0)
    private val controlCharacteristic = BluetoothGattCharacteristic(
            CONTROL_POINT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE)

    override fun onCentralDisconnected(central: Central) {
        super.onCentralDisconnected(central)
        // Need to deal with service listeners when no one is connected... maybe also first connection
        if (noCentralsConnected()) {
            observationCharacteristic.stopNotifiying()
        }
    }

    override fun onNotifyingDisabled(central: Central, characteristic: BluetoothGattCharacteristic) {
        super.onNotifyingDisabled(central, characteristic)
        if (characteristic.uuid == OBSERVATION_CHARACTERISTIC_UUID) {
            observationCharacteristic.stopNotifiying()
        }
    }

    fun sendObservation(observation: Observation) {
        sendBytesInSegments(observation.serialize())
    }

    fun sendObservations(observations: Collection<Observation>) {
        sendBytesInSegments(observations.map { it.serialize() }.merge())
    }

    private fun sendBytesInSegments(bytes: ByteArray) {
        bytes.asBLEDataSegments(peripheralManager.minimalMTU - 4).forEach { it.sendSegment() }
    }

    companion object {
        val GHS_SERVICE_UUID = UUID.fromString("0000183D-0000-1000-8000-00805f9b34fb")
        val OBSERVATION_CHARACTERISTIC_UUID = UUID.fromString("00002AC4-0000-1000-8000-00805f9b34fb")
        val CONTROL_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002AC6-0000-1000-8000-00805f9b34fb")
        private const val OBSERVATION_DESCRIPTION = "Characteristic for ACOM Observation segments."
        private const val CONTROL_POINT_DESCRIPTION = "Control point for generic health sensor."

        // If the BluetoothService has a running GHS service then return it
        fun getInstance(): GenericHealthSensorService? {
            return BluetoothServer.getInstance()?.getServiceWithUUID(GHS_SERVICE_UUID)?.let {it as GenericHealthSensorService }
        }
    }

    init {
        // Add the service
        service.addCharacteristic(observationCharacteristic)
        service.addCharacteristic(controlCharacteristic)
        observationCharacteristic.initWithDescriptor(OBSERVATION_DESCRIPTION)
        observationCharacteristic.initWithDescriptor(CONTROL_POINT_DESCRIPTION)
    }

    fun ByteArray.sendSegment() {
        Timber.i("Sending <%s>", this.asHexString())
        notifyCharacteristicChanged(this, observationCharacteristic)
    }
    fun BluetoothGattCharacteristic.stopNotifiying() {
        getDescriptor(PeripheralManager.CCC_DESCRIPTOR_UUID).value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
    }

    fun BluetoothGattCharacteristic.initWithDescriptor(description: String) {
        addDescriptor(getCccDescriptor())
        addDescriptor(getCudDescriptor(description))
        value = byteArrayOf(0x00)
    }
}
