package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattDescriptor
import com.philips.btserver.observations.ObservationEmitter
import com.philips.btserver.observations.ObservationType
import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.GattStatus
import timber.log.Timber

class GhsObservationScheduleHandler(val service: GenericHealthSensorService) {

    val observationScheduleCharacteristic get() = service.observationScheduleCharacteristic

    // Since we cannot use the value directly in characteristics or descriptors anymore
    var observationScheduleByteArray = byteArrayOf()
        set(value) {
            field = value
            observationScheduleCharacteristic.value = value
        }
    private var observationScheduleDescriptorValues = mutableMapOf<BluetoothGattDescriptor, ByteArray>()


    internal fun getObservationDescriptionValue(descriptor: BluetoothGattDescriptor): ByteArray {
        return observationScheduleDescriptorValues[descriptor] ?: byteArrayOf()
    }

    internal fun setObservationDescriptorValue(descriptor: BluetoothGattDescriptor, value: ByteArray) {
        observationScheduleDescriptorValues[descriptor] = value
        descriptor.value = value
    }

    internal fun configureObservationScheduleDescriptor(descriptor: BluetoothGattDescriptor,
                                                       central: BluetoothCentral,
                                                       value: ByteArray): GattStatus {
        // Validate the Observation Type
        val parser = BluetoothBytesParser(value)
        // Check that the observation type matches the descriptors observation type
        val observationType = ObservationType.fromValue(parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32))
        val descriptorBytes = getObservationDescriptionValue(descriptor)

        if (descriptorBytes.size > 3) {
            val descriptorObsType = ObservationType.fromValue(
                BluetoothBytesParser(descriptorBytes).getIntValue(
                    BluetoothBytesParser.FORMAT_UINT32))
            if (observationType != descriptorObsType) return GattStatus.VALUE_OUT_OF_RANGE
        }

        val measurementPeriod = parser.getFloatValue(BluetoothBytesParser.FORMAT_FLOAT)
        if (measurementPeriod < MIN_MEASUREMENT_PERIOD) return GattStatus.VALUE_OUT_OF_RANGE
        if (measurementPeriod > MAX_MEASUREMENT_PERIOD) return GattStatus.VALUE_OUT_OF_RANGE
        val updateInterval = parser.getFloatValue(BluetoothBytesParser.FORMAT_FLOAT)
        if (updateInterval < MIN_UPDATE_INVERVAL) return GattStatus.VALUE_OUT_OF_RANGE
        if (updateInterval > MAX_UPDATE_INVERVAL) return GattStatus.VALUE_OUT_OF_RANGE

        Timber.i("Observation schedule descriptor write for type: $observationType measurement period: $measurementPeriod update interval $updateInterval ")
        setObservationScheduleDescriptorValue(descriptor, central, value)
        // TODO Make this a broadcast or notify listeners to remove direct reference to ObservationEmitter (also get rid of double notifies and reason for that boolean)
        ObservationEmitter.setObservationSchedule(observationType, updateInterval, measurementPeriod, false)
        observationScheduleByteArray = value
        return GattStatus.SUCCESS
    }

    internal fun setObservationScheduleDescriptorValue(descriptor: BluetoothGattDescriptor, central: BluetoothCentral?, value: ByteArray) {
        Timber.i("setObservationScheduleDescriptorValue from central: ${central?.address}")
        setObservationDescriptorValue(descriptor, value)
        observationScheduleByteArray = value
        service.broadcastObservationScheduleValueNotifyToCentral(central, value)
    }

    internal fun getObservationScheduleDescriptor(observationType: ObservationType): BluetoothGattDescriptor {

        return service.featuresCharacteristic.descriptors
            .filter { it.uuid == GenericHealthSensorService.OBSERVATION_SCHEDULE_DESCRIPTOR_UUID }
            .firstOrNull { it.observationType() == observationType }
            ?: createObservationScheduleDescriptor(observationType)
    }

    private fun createObservationScheduleDescriptor(observationType: ObservationType): BluetoothGattDescriptor {
        val newDescriptor = BluetoothGattDescriptor(
            GenericHealthSensorService.OBSERVATION_SCHEDULE_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        service.featuresCharacteristic.addDescriptor(newDescriptor)
        return newDescriptor
    }


    internal fun initObservationScheduleDescriptors() {
//        ObservationEmitter.allObservationTypes.first {
        // FYI: MDC_ECG_HEART_RATE Hex value is 0x00024182
        listOf(ObservationType.MDC_ECG_HEART_RATE).forEach {
            val descriptor = createObservationScheduleDescriptor(it)
            val parser = BluetoothBytesParser()
            parser.setIntValue(it.value, BluetoothBytesParser.FORMAT_UINT32)
            parser.setFloatValue(1f, 3)
            parser.setFloatValue(1f, 3)
            setObservationDescriptorValue(descriptor, parser.value)
        }
    }

    fun setObservationSchedule(observationType: ObservationType, updateInterval: Float, measurementPeriod: Float) {
        val scheduleDesc = getObservationScheduleDescriptor(observationType)
        val parser = BluetoothBytesParser()
        parser.setIntValue(observationType.value, BluetoothBytesParser.FORMAT_UINT32)
        parser.setFloatValue(measurementPeriod, 3)
        parser.setFloatValue(updateInterval, 3)
        setObservationScheduleDescriptorValue(scheduleDesc, null, parser.value)
    }

    companion object {
        private const val MIN_MEASUREMENT_PERIOD = 0f
        private const val MAX_MEASUREMENT_PERIOD = 60f
        private const val MIN_UPDATE_INVERVAL = 1f
        private const val MAX_UPDATE_INVERVAL = 60f
    }


    fun BluetoothGattDescriptor.observationType(): ObservationType {
        val type = getObservationDescriptionValue(this).getObservationType()
        return if (type == null)
            ObservationType.UNKNOWN_TYPE
        else type
    }


    fun ByteArray.getObservationType(): ObservationType? {
        return if (size > 3)
            ObservationType.fromValue(BluetoothBytesParser(this).getIntValue(BluetoothBytesParser.FORMAT_UINT32))
        else null
    }

}
