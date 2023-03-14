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
import android.os.Build
import android.preference.PreferenceManager
import androidx.annotation.RequiresApi
import androidx.databinding.library.baseAdapters.BR
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.asFormattedHexString
import com.philips.btserver.extensions.merge
import com.philips.btserver.observations.*
import com.philips.btserver.userdataservice.UserDataManager
import com.philips.btserver.userdataservice.UserDataService
import com.welie.blessed.*
import timber.log.Timber
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
@RequiresApi(Build.VERSION_CODES.O)
class GenericHealthSensorService(peripheralManager: BluetoothPeripheralManager) :
    BaseService(peripheralManager) {

    // TODO: This is simple, maybe too simple, but we need some mechanism to indicate RACP cannot be done
    var canHandleRACP = true

    var serverBusy = false

    var observationCharacteristicIndicate: Boolean = true
        set(value) {
            field = value
            restartGattService()
            notifyCharacteristicChanged(serviceChangedHandleRangeBytes, serviceChangedCharacteristic)
        }

    var observationCharacteristicNotify: Boolean = true
        set(value) {
            field = value
            restartGattService()
            notifyCharacteristicChanged(serviceChangedHandleRangeBytes, serviceChangedCharacteristic)
        }

    var delayBetweenObservationSends: Boolean
        get() = storedObservationSendHandler.delayBetweenObservationSends
        set(value) {
            storedObservationSendHandler.delayBetweenObservationSends = value
        }

    internal val observationCharacteristicProperties: Int get() {
        return (if (observationCharacteristicIndicate) PROPERTY_INDICATE else 0) or
                (if (observationCharacteristicNotify) PROPERTY_NOTIFY else 0)
    }

    override val service = BluetoothGattService(GHS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)

    private val serviceChangedHandleRangeBytes = byteArrayOf(0x7f, 0x42, 0x7f, 0x43)

    internal val listeners = mutableSetOf<GenericHealthSensorServiceListener>()

    internal val observationCharacteristic = BluetoothGattCharacteristic(
        OBSERVATION_CHARACTERISTIC_UUID,
        observationCharacteristicProperties,
        0
    )

    internal val storedObservationCharacteristic = BluetoothGattCharacteristic(
        STORED_OBSERVATIONS_CHARACTERISTIC_UUID,
        observationCharacteristicProperties,
        0
    )

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

    internal val securityLevelsCharacteristic = BluetoothGattCharacteristic(
        LE_GATT_SECURITY_LEVELS_UUID,
        PROPERTY_READ,
        PERMISSION_READ
    )

    internal val serviceChangedCharacteristic = BluetoothGattCharacteristic(
        SERVICE_CHANGED_UUID,
        PROPERTY_INDICATE,
        0
    )

    // to do: make this match the required security level(s)
    private val  securtyLevelsByteArray = byteArrayOf(0x1,0x4)

    private val controlPointHandler = GhsControlPointHandler(this)
    private val observationScheduleHandler = GhsObservationScheduleHandler(this)
    private val racpHandler = GhsRacpHandler(this).startup()
    private val observationSendHandler = GhsObservationSendHandler(this, observationCharacteristic)
    private val storedObservationSendHandler =
        GhsObservationSendHandler(this, storedObservationCharacteristic)

    fun addListener(listener: GenericHealthSensorServiceListener) = listeners.add(listener)

    fun removeListener(listener: GenericHealthSensorServiceListener) = listeners.remove(listener)

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
        resetHandlers()
//        sendTempStoredObservations()
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

    val isSendLiveObservationsEnabled get() = isLiveObservationNotifyEnabled && isLiveObservationsStarted

    var isRacpNotifyEnabled = mutableMapOf<String, Boolean>()

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
            if (isSendLiveObservationsEnabled) sendTempStoredObservations(central)
        } else if (characteristic.uuid == RACP_CHARACTERISTIC_UUID) {
            isRacpNotifyEnabled.put(central.address, true)
        }
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
        } else if (characteristic.uuid == RACP_CHARACTERISTIC_UUID) {
            isRacpNotifyEnabled.put(central.address, false)
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
    ): ReadResponse {
        return when (characteristic.uuid) {
            OBSERVATION_CHARACTERISTIC_UUID -> {
                ObservationEmitter.singleShotEmit()
                ReadResponse(GattStatus.SUCCESS, byteArrayOf())
            }
            OBSERVATION_SCHEDULE_CHANGED_CHARACTERISTIC_UUID -> ReadResponse(
                GattStatus.SUCCESS,
                observationScheduleHandler.observationScheduleByteArray
            )
            LE_GATT_SECURITY_LEVELS_UUID -> ReadResponse(
                GattStatus.SUCCESS,
                securtyLevelsByteArray
            )
            // Features characteristic bytes are stored via the BaseService
            else -> super.onCharacteristicRead(central, characteristic)
        }
    }

    override fun onCharacteristicWrite(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): GattStatus {
        return when (characteristic.uuid) {
            GHS_CONTROL_POINT_CHARACTERISTIC_UUID -> writeGattStatusFor(value)
            RACP_CHARACTERISTIC_UUID -> racpHandler.writeGattStatusFor(central, value)
            else -> GattStatus.WRITE_NOT_PERMITTED
        }
    }

    override fun onCharacteristicWriteCompleted(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        when (characteristic.uuid) {
            GHS_CONTROL_POINT_CHARACTERISTIC_UUID -> controlPointHandler.handleReceivedBytes(value, bluetoothCentral)
            RACP_CHARACTERISTIC_UUID -> racpHandler.handleReceivedBytes(value, bluetoothCentral)
        }
    }

    /*
    * onDescriptorRead is a non-abstract method with an empty body to have a default behavior to do nothing
    * Subclasses do not need to provide an implementation
    */
    override fun onDescriptorRead(
        central: BluetoothCentral,
        descriptor: BluetoothGattDescriptor
    ): ReadResponse {
        return if (descriptor.uuid == OBSERVATION_SCHEDULE_DESCRIPTOR_UUID) {
            ReadResponse(
                GattStatus.SUCCESS,
                observationScheduleHandler.getObservationDescriptionValue(descriptor)
            )
        } else {
            super.onDescriptorRead(central, descriptor)
        }
    }

    override fun onDescriptorWrite(
        central: BluetoothCentral,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): GattStatus {
        return if (descriptor.uuid == OBSERVATION_SCHEDULE_DESCRIPTOR_UUID) {
            observationScheduleHandler.configureObservationScheduleDescriptor(
                descriptor,
                central,
                value
            )
        } else {
            super.onDescriptorWrite(central, descriptor, value)
        }
    }

    fun sendTempStoredObservations(central: BluetoothCentral) {
        if (ObservationStore.isTemporaryStore && isSendLiveObservationsEnabled) {
            val userIndex = UserDataService.getInstance()?.getCurrentUserIndexForCentral(central)?.toInt() ?: UserDataManager.UNDEFINED_USER_INDEX
            sendObservations(ObservationStore.observationsForUser(userIndex))
            ObservationStore.clearObservationsForUser(userIndex)
        }
    }

    internal fun broadcastObservationScheduleValueNotifyToCentral(
        central: BluetoothCentral?,
        value: ByteArray
    ) {
        centralsToNotifyUpdateFromCentral(central).forEach {
            Timber.i("setObservationScheduleDescriptorValue notify central: ${it.address}")
            peripheralManager.notifyCharacteristicChanged(
                value,
                it,
                observationScheduleCharacteristic
            )
        }
        updateDisconnectedBondedCentralsToNotify(observationScheduleCharacteristic)
    }

    private fun writeGattStatusFor(value: ByteArray): GattStatus {
        return if (canHandleRACP) {
            controlPointHandler.writeGattStatusFor(value)
        } else {
            GattStatus.PROCEDURE_IN_PROGRESS
        }
    }

    internal fun isRacpIndicateEnabled(central: BluetoothCentral): Boolean {
        return peripheralManager.getCentralsWantingIndications(racpCharacteristic).contains(central)
    }

    internal fun isStoredObservationCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic == storedObservationCharacteristic
    }

    fun setFeatureCharacteristicTypes(types: List<ObservationType>) {
        val bytes = types.featureCharacteristicBytes()
        Timber.i("Sending feature characteristic bytes: ${bytes.asFormattedHexString()}")
        setCharacteristicValueAndNotify(featuresCharacteristic, bytes)
    }

    fun setObservationSchedule(
        observationType: ObservationType,
        updateInterval: Float,
        measurementPeriod: Float
    ) {
        observationScheduleHandler.setObservationSchedule(
            observationType,
            updateInterval,
            measurementPeriod
        )
    }

    fun setValidRangeAndAccuracy(
        observationType: ObservationType,
        unitCode: UnitCode,
        lowerLimit: Float,
        upperLimit: Float,
        accuracy: Float
    ) {
        val validDesc = BluetoothGattDescriptor(
            VALID_RANGE_AND_ACCURACY_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ
        )
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
        observationScheduleHandler.reset()
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
        storedObservationSendHandler.sendObservations(observations, true)
    }

    fun abortSendStoredObservations() {
        storedObservationSendHandler.abortSendStoredObservations()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun restartGattService() {
        BluetoothServer.getInstance()?.restartGattService(service)
    }

    companion object {
        val GHS_SERVICE_UUID = UUID.fromString("00007f44-0000-1000-8000-00805f9b34fb")
        val OBSERVATION_CHARACTERISTIC_UUID =
            UUID.fromString("00007f43-0000-1000-8000-00805f9b34fb")
        val STORED_OBSERVATIONS_CHARACTERISTIC_UUID =
            UUID.fromString("00007f42-0000-1000-8000-00805f9b34fb")
        val GHS_FEATURES_CHARACTERISTIC_UUID =
            UUID.fromString("00007f41-0000-1000-8000-00805f9b34fb")
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
        val LE_GATT_SECURITY_LEVELS_UUID =
            UUID.fromString("00002BF5-0000-1000-8000-00805f9b34fb")
        val SERVICE_CHANGED_UUID =
            UUID.fromString("00002A05-0000-1000-8000-00805f9b34fb")

        private const val OBSERVATION_DESCRIPTION = "Live observation characteristic"
        private const val STORED_OBSERVATIONS_DESCRIPTION = "Stored observation characteristic"
        private const val FEATURES_DESCRIPTION = "GHS features characteristic"
        private const val UNIQUE_DEVICE_ID_DESCRIPTION = "Unique device ID (UDI) characteristic"
        private const val RACP_DESCRIPTION = "RACP Characteristic."
        private const val GHS_CONTROL_POINT_DESCRIPTION = "Control Point characteristic"
        private const val OBSERVATION_SCHEDULE_DESCRIPTION = "Observation Schedule characteristic"
        private const val LE_GATT_SECURITY_LEVELS_DESCRIPTION = "LE GATT Security Levels characteristic"

        /**
         * If the [BluetoothServer] singleton has an instance of a GenericHealthSensorService return it (otherwise null)
         */
        fun getInstance(): GenericHealthSensorService? {
            val ghs = BluetoothServer.getInstance()?.getServiceWithUUID(GHS_SERVICE_UUID)
            return ghs?.let { it as GenericHealthSensorService }
        }
    }

    init {
        initCharacteristic(observationCharacteristic, OBSERVATION_DESCRIPTION)
        initCharacteristic(storedObservationCharacteristic, STORED_OBSERVATIONS_DESCRIPTION)
        initCharacteristic(featuresCharacteristic, FEATURES_DESCRIPTION)
        initCharacteristic(racpCharacteristic, RACP_DESCRIPTION)
        initCharacteristic(ghsControlPointCharacteristic, GHS_CONTROL_POINT_DESCRIPTION)
        initCharacteristic(observationScheduleCharacteristic, OBSERVATION_SCHEDULE_DESCRIPTION)
        initCharacteristic(securityLevelsCharacteristic, LE_GATT_SECURITY_LEVELS_DESCRIPTION)
        observationScheduleHandler.initObservationScheduleDescriptors()
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
    if (!specializations.isEmpty()) {
        result += byteArrayOf(specializations.size.toByte())
        specializations.forEach { result += it.asByteArray() }
    }
    return result
}

private fun List<ObservationType>.deviceSpecializations(): List<DeviceSpecialization> {
    // Yeah, we could do it this way to be "cool" but is it really better?
    // return map { it.deviceSpecialization() }.toSet().toList()
    val result = mutableSetOf<DeviceSpecialization>()
    forEach { result.add(it.deviceSpecialization()) }
    return result.toList()
}

private fun ObservationType.deviceSpecialization(): DeviceSpecialization {
    return when (this) {
        ObservationType.MDC_TEMP_BODY -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_TEMP
        ObservationType.MDC_PULS_OXIM_SAT_O2 -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_PULS_OXIM
        ObservationType.MDC_PPG_TIME_PD_PP -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_ECG
        ObservationType.MDC_PRESS_BLD_NONINV -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_BP
        ObservationType.MDC_PRESS_BLD_NONINV_SYS -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_BP
        ObservationType.MDC_PRESS_BLD_NONINV_DIA -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_BP
        else -> DeviceSpecialization.MDC_DEV_SPEC_PROFILE_HYDRA
    }
}
