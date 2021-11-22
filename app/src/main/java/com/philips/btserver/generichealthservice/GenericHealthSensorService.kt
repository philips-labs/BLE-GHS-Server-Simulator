/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.asBLEDataSegments
import com.philips.btserver.extensions.asGHSBytes
import com.philips.btserver.extensions.merge
import java.util.*

/**
 * GenericHealthSensorService is the *BaseService* specific to handling
 * the generic health sensor service. The GHS service proposed includes
 * an observation characteristic and a control point characteristic.
 */
internal class GenericHealthSensorService(peripheralManager: BluetoothPeripheralManager) :
    BaseService(peripheralManager) {

    override val service = BluetoothGattService(GHS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)

    private val observationCharacteristic = BluetoothGattCharacteristic(
        OBSERVATION_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_NOTIFY,
        0
    )

    private val storedObservationCharacteristic = BluetoothGattCharacteristic(
        STORED_OBSERVATIONS_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_INDICATE,
        0
    )

    private val simpleTimeCharacteristic = BluetoothGattCharacteristic(
        SIMPLE_TIME_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_WRITE
    )

    private val featuresCharacteristic = BluetoothGattCharacteristic(
        GHS_FEATURES_CHARACTERISTIC_UUID,
        PROPERTY_READ,
        0
    )

    private val uniqueDeviceIdCharacteristic = BluetoothGattCharacteristic(
        UNIQUE_DEVICE_ID_CHARACTERISTIC_UUID,
        PROPERTY_READ,
        0
    )

    /**
     * Notification that [central] has disconnected. If there are no other connected bluetooth
     * centrals, then stop emitting observations.
     */
    override fun onCentralDisconnected(central: BluetoothCentral) {
        super.onCentralDisconnected(central)
        if (noCentralsConnected()) {
            ObservationEmitter.stopEmitter()
        }
    }

    /**
     * Notification from [central] that [characteristic] has notification enabled. Implies that
     * there is a connection so start emitting observations.
     */
    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
//        ObservationEmitter.startEmitter()
    }

    /**
     * Notification from [central] that [characteristic] has notification disabled. If the
     * characteristic is the observation characteristic then stop emitting observations.
     */
    override fun onNotifyingDisabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        super.onNotifyingDisabled(central, characteristic)
        if (characteristic.uuid == OBSERVATION_CHARACTERISTIC_UUID) {
            ObservationEmitter.stopEmitter()
        }
    }

    /*
     * onCharacteristicRead is a non-abstract method with an empty body to have a default behavior to do nothing
     */
    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (characteristic.uuid == SIMPLE_TIME_CHARACTERISTIC_UUID) {
            sendClockBytes()
        }
    }

    /*
     * send the current clock in the GHS byte format based on current flags
     */
    private fun sendClockBytes() {
        val bytes = Date().asGHSBytes()
        simpleTimeCharacteristic.value = bytes
        notifyCharacteristicChanged(bytes, simpleTimeCharacteristic)
    }

    /**
     * Serialize an [observation] into a byte array transmit the bytes in one or more segments.
     */
    fun sendObservation(observation: Observation) {
        sendBytesInSegments(observation.serialize())
    }

    /**
     * Serialize and merge the [observations] into a byte array transmit the bytes in one or more segments.
     */
    fun sendObservations(observations: Collection<Observation>) {
        sendBytesInSegments(observations.map { it.serialize() }.merge())
    }

    /**
     * Private ByteArray extension to break up the receiver into segments that fit in the MTU and
     * send each segment in sequence over BLE
     */
    private fun sendBytesInSegments(bytes: ByteArray) {
        bytes.asBLEDataSegments(minimalMTU - 4).forEach { it.sendSegment() }
    }

    companion object {
        val GHS_SERVICE_UUID = UUID.fromString("00007f44-0000-1000-8000-00805f9b34fb")
        val OBSERVATION_CHARACTERISTIC_UUID =
            UUID.fromString("00007f43-0000-1000-8000-00805f9b34fb")
        val STORED_OBSERVATIONS_CHARACTERISTIC_UUID =
            UUID.fromString("00007f42-0000-1000-8000-00805f9b34fb")
        val GHS_FEATURES_CHARACTERISTIC_UUID =
            UUID.fromString("00007f41-0000-1000-8000-00805f9b34fb")
        val SIMPLE_TIME_CHARACTERISTIC_UUID =
            UUID.fromString("00007f3d-0000-1000-8000-00805f9b34fb")
        val UNIQUE_DEVICE_ID_CHARACTERISTIC_UUID =
            UUID.fromString("00007f3a-0000-1000-8000-00805f9b34fb")
        private const val OBSERVATION_DESCRIPTION = "Characteristic for live observation segments."
        private const val SIMPLE_TIME_DESCRIPTION = "Characteristic for GHS clock data and flags."
        private const val STORED_OBSERVATIONS_DESCRIPTION = "Characteristic for stored observation segments."
        private const val FEATURES_DESCRIPTION = "Characteristic for GHS features."
        private const val UNIQUE_DEVICE_ID_DESCRIPTION = "Characteristic for unique device ID (UDI)."

        /**
         * If the [BluetoothServer] singleton has an instance of a GenericHealthSensorService return it (otherwise null)
         */
        fun getInstance(): GenericHealthSensorService? {
            val bleServer = BluetoothServer.getInstance()
            val ghs = bleServer?.getServiceWithUUID(GHS_SERVICE_UUID)
            return ghs?.let { it as GenericHealthSensorService }
        }
    }

    init {
        initCharacteristic(observationCharacteristic, OBSERVATION_DESCRIPTION)
        initCharacteristic(storedObservationCharacteristic, STORED_OBSERVATIONS_DESCRIPTION)
        initCharacteristic(simpleTimeCharacteristic, SIMPLE_TIME_DESCRIPTION)
        initCharacteristic(featuresCharacteristic, FEATURES_DESCRIPTION)
        initCharacteristic(uniqueDeviceIdCharacteristic, UNIQUE_DEVICE_ID_DESCRIPTION)
    }

    private fun initCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        description: String
    ) {
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(getCccDescriptor())
        characteristic.addDescriptor(getCudDescriptor(description))
        characteristic.value = byteArrayOf(0x00)
    }

    /**
     * ByteArray extension to send `this`'s bytes and do a BLE notification
     * over the observation characteristic.
     */
    fun ByteArray.sendSegment() {
        observationCharacteristic.value = this
        notifyCharacteristicChanged(this, observationCharacteristic)
    }

}
