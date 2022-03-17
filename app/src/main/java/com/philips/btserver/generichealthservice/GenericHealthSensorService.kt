/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.*
import java.util.*

/**
 * GenericHealthSensorService is the *BaseService* specific to handling
 * the generic health sensor service. The GHS service proposed includes
 * an observation characteristic and a control point characteristic.
 */
internal class GenericHealthSensorService(peripheralManager: BluetoothPeripheralManager) :
    BaseService(peripheralManager) {

    override val service = BluetoothGattService(GHS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)

    private var currentSegmentNumber: Int = 0

    private val observationCharacteristic = BluetoothGattCharacteristic(
        OBSERVATION_CHARACTERISTIC_UUID,
        PROPERTY_NOTIFY or PROPERTY_INDICATE,
        0
    )

    private val storedObservationCharacteristic = BluetoothGattCharacteristic(
        STORED_OBSERVATIONS_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_INDICATE,
        BluetoothGattCharacteristic.PERMISSION_READ
    )

    private val featuresCharacteristic = BluetoothGattCharacteristic(
        GHS_FEATURES_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_INDICATE,
        PERMISSION_READ
    )

    private val uniqueDeviceIdCharacteristic = BluetoothGattCharacteristic(
        UNIQUE_DEVICE_ID_CHARACTERISTIC_UUID,
        PROPERTY_READ,
        PERMISSION_READ
    )

    private val racpCharacteristic = BluetoothGattCharacteristic(
        RACP_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_READ or PERMISSION_WRITE
    )

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
        currentSegmentNumber = 0
    }

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
//        if (characteristic.uuid == SIMPLE_TIME_CHARACTERISTIC_UUID) {
//            sendClockBytes()
//        }


//        if (characteristic.uuid == OBSERVATION_CHARACTERISTIC_UUID) {
//            ObservationEmitter.singleShotEmit()
//        }

        if (characteristic.uuid == GHS_FEATURES_CHARACTERISTIC_UUID) {
            ObservationEmitter.singleShotEmit()
        }

    }

    fun setFeatureCharacteristicTypes(types: List<ObservationType>) {
        val bytes = listOf(
            byteArrayOf(0x0, types.size.toByte()),
            types.map { it.asGHSByteArray() }.merge()
        ).merge()
        featuresCharacteristic.value = bytes
        notifyCharacteristicChanged(bytes, featuresCharacteristic)
    }

//    /*
//     * send the current clock in the GHS byte format based on current flags
//     */
//    private fun sendClockBytes() {
//        val bytes = Date().asGHSBytes()
//        simpleTimeCharacteristic.value = bytes
//        notifyCharacteristicChanged(bytes, simpleTimeCharacteristic)
//    }

    /**
     * Serialize an [observation] into a byte array transmit the bytes in one or more segments.
     */
    fun sendObservation(observation: Observation) {
        sendBytesInSegments(observation.ghsByteArray)
    }

    /**
     * Serialize and merge the [observations] into a byte array transmit the bytes in one or more segments.
     */
    fun sendObservations(observations: Collection<Observation>) {
        observations.forEach { sendObservation(it) }
    }

    /**
     * Private ByteArray extension to break up the receiver into segments that fit in the MTU and
     * send each segment in sequence over BLE
     */
    private fun sendBytesInSegments(bytes: ByteArray) {
        // asBLEDataSegments returns Pair<List<ByteArray>, Int> with the segments and next segment number
        val segments = bytes.asBLEDataSegments(minimalMTU - 5, currentSegmentNumber)
        segments.first.forEach { it.sendSegment() }
        currentSegmentNumber = segments.second
    }

    companion object {
        val GHS_SERVICE_UUID = UUID.fromString("00007f44-0000-1000-8000-00805f9b34fb")
        val OBSERVATION_CHARACTERISTIC_UUID =
            UUID.fromString("00007f43-0000-1000-8000-00805f9b34fb")
        val STORED_OBSERVATIONS_CHARACTERISTIC_UUID =
            UUID.fromString("00007f42-0000-1000-8000-00805f9b34fb")
        val GHS_FEATURES_CHARACTERISTIC_UUID =
            UUID.fromString("00007f41-0000-1000-8000-00805f9b34fb")
//        val SIMPLE_TIME_CHARACTERISTIC_UUID =
//            UUID.fromString("00007f3d-0000-1000-8000-00805f9b34fb")
        val UNIQUE_DEVICE_ID_CHARACTERISTIC_UUID =
            UUID.fromString("00007f3a-0000-1000-8000-00805f9b34fb")
        val RACP_CHARACTERISTIC_UUID =
            UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb")
        private const val OBSERVATION_DESCRIPTION = "Characteristic for live observation segments."
//        private const val SIMPLE_TIME_DESCRIPTION = "Characteristic for GHS clock data and flags."
        private const val STORED_OBSERVATIONS_DESCRIPTION = "Characteristic for stored observation segments."
        private const val FEATURES_DESCRIPTION = "Characteristic for GHS features."
        private const val UNIQUE_DEVICE_ID_DESCRIPTION = "Characteristic for unique device ID (UDI)."
        private const val RACP_DESCRIPTION = "RACP Characteristic."

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
//        initCharacteristic(simpleTimeCharacteristic, SIMPLE_TIME_DESCRIPTION)
        initCharacteristic(featuresCharacteristic, FEATURES_DESCRIPTION)
        initCharacteristic(uniqueDeviceIdCharacteristic, UNIQUE_DEVICE_ID_DESCRIPTION)
        initCharacteristic(racpCharacteristic, RACP_DESCRIPTION)
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
