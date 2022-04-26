/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.merge
import com.philips.btserver.observations.Observation
import com.philips.btserver.observations.ObservationEmitter
import com.philips.btserver.observations.ObservationType
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import java.util.*

interface GenericHealthSensorServiceListener {
    fun onObservationsSent(observations: Collection<Observation>) {}
    fun onStoredObservationsSent(observations: Collection<Observation>) {}
}

/**
 * GenericHealthSensorService is the *BaseService* specific to handling
 * the generic health sensor service. The GHS service proposed includes
 * an observation characteristic and a control point characteristic.
 */
class GenericHealthSensorService(peripheralManager: BluetoothPeripheralManager) :
    BaseService(peripheralManager) {

    override val service = BluetoothGattService(GHS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)

    private val controlPointHandler = GhsControlPointHandler(this)
    val racpHandler = GhsRacpHandler(this)

    internal val listeners = mutableSetOf<GenericHealthSensorServiceListener>()

    internal val observationCharacteristic = BluetoothGattCharacteristic(
        OBSERVATION_CHARACTERISTIC_UUID,
        PROPERTY_NOTIFY or PROPERTY_INDICATE,
        0
    )

    internal val storedObservationCharacteristic = BluetoothGattCharacteristic(
        STORED_OBSERVATIONS_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_INDICATE,
        PERMISSION_READ
    )

    private val observationSendHandler = GhsObservationSendHandler(this, observationCharacteristic)
    private val storedObservationSendHandler = GhsObservationSendHandler(this, storedObservationCharacteristic)

    internal val featuresCharacteristic = BluetoothGattCharacteristic(
        GHS_FEATURES_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_INDICATE,
        PERMISSION_READ
    )


    internal val ghsControlPointCharacteristic = BluetoothGattCharacteristic(
        GHS_CONTROL_POINT_CHARACTERISTIC_UUID,
        PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_WRITE
    )

    internal val uniqueDeviceIdCharacteristic = BluetoothGattCharacteristic(
        UNIQUE_DEVICE_ID_CHARACTERISTIC_UUID,
        PROPERTY_READ,
        PERMISSION_READ
    )

    internal val racpCharacteristic = BluetoothGattCharacteristic(
        RACP_CHARACTERISTIC_UUID,
        PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_WRITE
    )

    fun setupHack() { racpHandler.setupHack() }

    fun addListener(listener: GenericHealthSensorServiceListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: GenericHealthSensorServiceListener) {
        listeners.remove(listener)
    }

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
        resetHandlers()
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
        // TODO: What if real-time observations have been enabled?
    }

    /**
     * A notification has been sent
     *
     * @param bluetoothCentral the central
     * @param value the value of the notification
     * @param characteristic the characteristic for which the notification was sent
     * @param status the status of the operation
     */
    override fun onNotificationSent(
        bluetoothCentral: BluetoothCentral,
        value: ByteArray?,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
        if (characteristic.uuid == STORED_OBSERVATIONS_CHARACTERISTIC_UUID) {
            value?.let { storedObservationSendHandler.onSegmentSent(value, status) }
        }
    }

    /*
     * onCharacteristicRead is a non-abstract method with an empty body to have a default behavior to do nothing
     */
    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        when(characteristic.uuid) {
            OBSERVATION_CHARACTERISTIC_UUID -> ObservationEmitter.singleShotEmit()
        }

    }

    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        return when(characteristic.uuid) {
            GHS_CONTROL_POINT_CHARACTERISTIC_UUID -> if (controlPointHandler.isWriteValid(value)) GattStatus.SUCCESS else GattStatus.ILLEGAL_PARAMETER
            RACP_CHARACTERISTIC_UUID -> if (racpHandler.isWriteValid(value)) GattStatus.SUCCESS else GattStatus.ILLEGAL_PARAMETER
            else -> GattStatus.SUCCESS
        }
    }

    override fun onCharacteristicWriteCompleted(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray) {
        when(characteristic.uuid) {
            GHS_CONTROL_POINT_CHARACTERISTIC_UUID -> controlPointHandler.handleReceivedBytes(value)
            RACP_CHARACTERISTIC_UUID -> racpHandler.handleReceivedBytes(value)
        }
    }

    internal fun isStoredObservationCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic == storedObservationCharacteristic
    }

    fun setFeatureCharacteristicTypes(types: List<ObservationType>) {
        val bytes = listOf(
            byteArrayOf(featureFlagsFor(types), types.size.toByte()),
            featureTypeBytesFor(types),
            deviceSpecializationBytes(types)
        ).merge()
//        featuresCharacteristic.value = bytes
        notifyCharacteristicChanged(bytes, featuresCharacteristic)
    }

    private fun featureFlagsFor(types: List<ObservationType>): Byte {
        return (if (hasDeviceSpeciazations(types)) 0x1 else 0x0).toByte()
    }

    private fun featureTypeBytesFor(types: List<ObservationType>): ByteArray {
        return types.map { it.asGHSByteArray() }.merge()
    }

    private fun deviceSpecializationBytes(types: List<ObservationType>): ByteArray {
        // Only sent for blood pressure for now
        // Code = MDC_DEV_SPEC_PROFILE_BP = 00 08 10 07 (only use 2 bytes, assume partition 8), Version = 01
        return if (hasDeviceSpeciazations(types)) byteArrayOf(0x01, 0x07, 0x10, 0x01) else byteArrayOf()
    }

    private fun hasDeviceSpeciazations(types: List<ObservationType>): Boolean {
        return types.contains(ObservationType.MDC_PRESS_BLD_NONINV)
    }

    internal fun setCharacteristicValueAndNotify(value: ByteArray, characteristic: BluetoothGattCharacteristic) {
        notifyCharacteristicChanged(value, characteristic)
    }

    private fun resetHandlers() {
        observationSendHandler.reset()
        storedObservationSendHandler.reset()
        controlPointHandler.reset()
        racpHandler.reset()
    }

    /**
     * Serialize an [observation] into a byte array transmit the bytes in one or more segments.
     */
    fun sendObservation(observation: Observation) {
        observationSendHandler.sendObservation(observation)
    }

    /**
     * Serialize an [observation] into a byte array transmit the bytes in one or more segments.
     */
    fun sendStoredObservation(observation: Observation) {
        storedObservationSendHandler.sendObservation(observation)
    }

    /**
     * Serialize and merge the [observations] into a byte array transmit the bytes in one or more segments.
     */
    fun sendObservations(observations: Collection<Observation>) {
        observationSendHandler.sendObservations(observations)
    }

    /**
     * Serialize and merge the [observations] into a byte array transmit the bytes in one or more segments.
     */
    fun sendStoredObservations(observations: Collection<Observation>) {
        storedObservationSendHandler.sendObservations(observations)
    }

    fun abortSendStoredObservations() {
        storedObservationSendHandler.abortSendStoredObservations()
    }

    companion object {
        val GHS_SERVICE_UUID = UUID.fromString("00007f44-0000-1000-8000-00805f9b34fb")
        val OBSERVATION_CHARACTERISTIC_UUID =
            UUID.fromString("00007f43-0000-1000-8000-00805f9b34fb")
        val STORED_OBSERVATIONS_CHARACTERISTIC_UUID =
            UUID.fromString("00007f42-0000-1000-8000-00805f9b34fb")
        val GHS_FEATURES_CHARACTERISTIC_UUID =
            UUID.fromString("00007f41-0000-1000-8000-00805f9b34fb")
        val UNIQUE_DEVICE_ID_CHARACTERISTIC_UUID =
            UUID.fromString("00007f3a-0000-1000-8000-00805f9b34fb")
        val GHS_CONTROL_POINT_CHARACTERISTIC_UUID =
            UUID.fromString("00007f40-0000-1000-8000-00805f9b34fb")
        val RACP_CHARACTERISTIC_UUID =
            UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb")
        private const val OBSERVATION_DESCRIPTION = "Live observation characteristic"
        private const val STORED_OBSERVATIONS_DESCRIPTION = "Stored observation characteristic"
        private const val FEATURES_DESCRIPTION = "GHS features characteristic"
        private const val UNIQUE_DEVICE_ID_DESCRIPTION = "Unique device ID (UDI) characteristic"
        private const val RACP_DESCRIPTION = "RACP Characteristic."
        private const val GHS_CONTROL_POINT_DESCRIPTION = "Control Point characteristic"

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
        initCharacteristic(ghsControlPointCharacteristic, GHS_CONTROL_POINT_DESCRIPTION)
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
     * Send ByteArray bytes and do a BLE notification over the characteristic.
     */
    fun sendBytesAndNotify(bytes: ByteArray, characteristic: BluetoothGattCharacteristic) {
        notifyCharacteristicChanged(bytes, characteristic)
    }

}
