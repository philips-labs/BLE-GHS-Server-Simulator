/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.asFormattedHexString
import com.philips.btserver.extensions.merge
import com.philips.btserver.observations.Observation
import com.philips.btserver.observations.ObservationEmitter
import com.philips.btserver.observations.ObservationType
import com.philips.btserver.observations.UnitCode
import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import timber.log.Timber
import java.nio.charset.StandardCharsets
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

    // TODO: This is simple, maybe too simple, but we need some mechanism to indicate RACP cannot be done
    public var canHandleRACP = true

    override val service = BluetoothGattService(GHS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)

    internal val listeners = mutableSetOf<GenericHealthSensorServiceListener>()

    private val controlPointHandler = GhsControlPointHandler(this)
    val racpHandler = GhsRacpHandler(this).startup()

    internal val observationCharacteristic = BluetoothGattCharacteristic(
        OBSERVATION_CHARACTERISTIC_UUID,
        PROPERTY_NOTIFY or PROPERTY_INDICATE,
        0
    )

    internal val storedObservationCharacteristic = BluetoothGattCharacteristic(
        STORED_OBSERVATIONS_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_NOTIFY,
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

    internal val observationScheduleCharacteristic = BluetoothGattCharacteristic(
        OBSERVATION_SCHEDULE_CHANGED_CHARACTERISTIC_UUID,
        PROPERTY_INDICATE,
        0
    )

//    fun setupHack() {
//         racpHandler.setupHack()
//    }

    fun addListener(listener: GenericHealthSensorServiceListener) = listeners.add(listener)

    fun removeListener(listener: GenericHealthSensorServiceListener) = listeners.remove(listener)

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
        resetHandlers()
    }

    /**
     * Notification that [central] has disconnected. If there are no other connected bluetooth
     * centrals, then stop emitting observations and reset the transmit (needs to be enabled on connect).
     */
    override fun onCentralDisconnected(central: BluetoothCentral) {
        super.onCentralDisconnected(central)
        if (noCentralsConnected()) {
            ObservationEmitter.stopEmitter()
            isLiveObservationNotifyEnabled = false
        }
    }

    var isLiveObservationNotifyEnabled = false
    var isLiveObservationsStarted = false

    fun isSendLiveObservationsEnabled(): Boolean {
        return isLiveObservationNotifyEnabled && isLiveObservationsStarted
    }

    /**
     * Notification from [central] that [characteristic] has notification enabled. Implies that
     * there is a connection so start emitting observations.
     */
    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (characteristic.uuid == OBSERVATION_CHARACTERISTIC_UUID) {
            isLiveObservationNotifyEnabled = true
        }
//        ObservationEmitter.startEmitter()
    }

    /**
     * Notification from [central] that [characteristic] has notification disabled. If the
     * characteristic is the observation characteristic then stop emitting observations.
     *
     * @param central the central that is notifying
     * @param characteristic the characteristic for which notifications are disabled
     */
    override fun onNotifyingDisabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        super.onNotifyingDisabled(central, characteristic)
        if (characteristic.uuid == OBSERVATION_CHARACTERISTIC_UUID) {
            ObservationEmitter.stopEmitter()
            isLiveObservationNotifyEnabled = false
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
            GHS_CONTROL_POINT_CHARACTERISTIC_UUID -> writeGattStatusFor(value)
            RACP_CHARACTERISTIC_UUID -> if (racpHandler.isWriteValid(value)) GattStatus.SUCCESS else GattStatus.ILLEGAL_PARAMETER
            else -> GattStatus.WRITE_NOT_PERMITTED
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

    private fun writeGattStatusFor(value: ByteArray): GattStatus {
        return if (canHandleRACP) {
            controlPointHandler.writeGattStatusFor(value)
        } else {
            GattStatus.PROCEDURE_IN_PROGRESS
        }
    }

    internal fun isStoredObservationCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic == storedObservationCharacteristic
    }

    fun setFeatureCharacteristicTypes(types: List<ObservationType>) {
        val bytes = types.featureCharacteristicBytes()
        Timber.i("Sending feature characteristic bytes: ${bytes.asFormattedHexString()}")
        setCharacteristicValueAndNotify(bytes, featuresCharacteristic)
    }

    internal fun setCharacteristicValueAndNotify(value: ByteArray, characteristic: BluetoothGattCharacteristic) {
        characteristic.value = value
        notifyCharacteristicChanged(value, characteristic)
    }

    fun setObservationSchedule(observationType: ObservationType, updateInterval: Float, measurementPeriod: Float) {
        val scheduleDesc = BluetoothGattDescriptor(OBSERVATION_SCHEDULE_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ)
        val parser = BluetoothBytesParser()
        parser.setIntValue(observationType.value, BluetoothBytesParser.FORMAT_UINT32)
        parser.setFloatValue(measurementPeriod, 3)
        parser.setFloatValue(updateInterval, 3)
        scheduleDesc.value = parser.value
        featuresCharacteristic.addDescriptor(scheduleDesc)
    }

    fun setValidRangeAndAccuracy(observationType: ObservationType, unitCode: UnitCode, lowerLimit: Float, upperLimit: Float, accuracy: Float) {
        val validDesc = BluetoothGattDescriptor(VALID_RANGE_AND_ACCURACY_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ)
        val parser = BluetoothBytesParser()
        parser.setIntValue(observationType.value, BluetoothBytesParser.FORMAT_UINT32)
        parser.setIntValue(unitCode.value, BluetoothBytesParser.FORMAT_UINT16)

        parser.setFloatValue(lowerLimit, 3)
        parser.setFloatValue(upperLimit, 3)
        parser.setFloatValue(accuracy, 3)
        validDesc.value = parser.value
        featuresCharacteristic.addDescriptor(validDesc)
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
        val OBSERVATION_SCHEDULE_CHANGED_CHARACTERISTIC_UUID =
            UUID.fromString("00007f3f-0000-1000-8000-00805f9b34fb")
        val OBSERVATION_SCHEDULE_DESCRIPTOR_UUID =
            UUID.fromString("00007f35-0000-1000-8000-00805f9b34fb")
        val VALID_RANGE_AND_ACCURACY_DESCRIPTOR_UUID =
            UUID.fromString("00007f34-0000-1000-8000-00805f9b34fb")

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

private fun List<ObservationType>.featureCharacteristicBytes(): ByteArray {
    return listOf(
        byteArrayOf(featureFlagsFor(), this.size.toByte()),
        featureTypeBytesFor(),
        deviceSpecializationBytes()
    ).merge()
}

private fun List<ObservationType>.featureFlagsFor(): Byte {
    return (if (deviceSpecializations().isEmpty()) 0x0 else 0x1).toByte()
}

private fun List<ObservationType>.featureTypeBytesFor(): ByteArray {
    return this.map { it.asGHSByteArray() }.merge()
}

private fun List<ObservationType>.deviceSpecializationBytes(): ByteArray {
    var result = byteArrayOf()
    val specializations = deviceSpecializations()
    if (!specializations.isEmpty()){
        result += byteArrayOf(specializations.size.toByte())
        specializations.forEach { result += it.asByteArray() }
    }
    return result
}

private fun ObservationType.deviceSpecializationBytes(): ByteArray {
    return when (this) {
        ObservationType.MDC_PRESS_BLD_NONINV -> byteArrayOf(0x01, 0x07, 0x10, 0x01)
        else -> byteArrayOf()
    }
}

private fun List<ObservationType>.deviceSpecializations(): List<DeviceSpecialization> {
    // Yeah, we could do it this way to be "cool" but is it really better?
    // return map { it.deviceSpecialization() }.toSet().toList()
    val result = mutableSetOf<DeviceSpecialization>()
    forEach { result.add(it.deviceSpecialization()) }
    return result.toList()
}

private fun ObservationType.deviceSpecialization(): DeviceSpecialization {
    return when(this) {
        ObservationType.MDC_TEMP_BODY -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_TEMP
        ObservationType.MDC_PULS_OXIM_SAT_O2 -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_PULS_OXIM
        ObservationType.MDC_PPG_TIME_PD_PP -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_ECG
        ObservationType.MDC_PRESS_BLD_NONINV -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_BP
        ObservationType.MDC_PRESS_BLD_NONINV_SYS -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_BP
        ObservationType.MDC_PRESS_BLD_NONINV_DIA -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_BP
        else -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_HYDRA
    }
}
