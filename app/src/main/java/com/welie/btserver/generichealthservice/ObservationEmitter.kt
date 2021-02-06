package com.welie.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Handler
import android.os.Looper
import com.welie.btserver.*
import com.welie.btserver.GenericHealthSensorService
import com.welie.btserver.UnitCode
import timber.log.Timber
import java.util.*

object ObservationEmitter: ServiceListener {

    var emitterPeriod = 10
    /*
     * If mergeObservations true observations are sent as one ACOM byte array.
     * false means send each observation as a sepeate ACOM Object
     */
    var mergeObservations = true

    val observations = mutableListOf<Observation>()
    private val handler = Handler(Looper.myLooper())
    private val notifyRunnable = Runnable { sendObservations(false) }

    private var lastHandle = 1

    private val typesToEmit = mutableSetOf<ObservationType>()

    private val ghsService: GenericHealthSensorService?
        get() = GenericHealthSensorService.getInstance()


    init {
        ghsService?.addListener(this)
    }

    fun addObservationType(type: ObservationType) {
        typesToEmit.add(type)
    }

    fun removeObservationType(type: ObservationType) {
        typesToEmit.remove(type)
        observations.removeAll { it.type == type }
    }

    private fun generateObservationsToSend() {
        observations.clear()
        observations.addAll(typesToEmit.mapNotNull { randomObservationOfType(it) })
    }

    private fun randomObservationOfType(type: ObservationType): Observation? {
        return when(type.valueType()) {
            ObservationValueType.MDC_ATTR_NU_VAL_OBS_SIMP -> randomSimpleNumericObservation(type)
            ObservationValueType.MDC_ATTR_SA_VAL_OBS -> randomSampleArrayObservation(type)
            else -> null
        }
    }

    private fun randomSimpleNumericObservation(type: ObservationType): Observation {
        return SimpleNumericObservation(lastHandle++.toShort(),
                type,
                type.randomNumericValue(),
                type.numericPrecision(),
                type.unitCode(),
                Calendar.getInstance().time)
    }

    private fun randomSampleArrayObservation(type: ObservationType): Observation {
        return SampleArrayObservation(lastHandle++.toShort(),
                type,
                type.randomSampleArray(),
                type.unitCode(),
                Calendar.getInstance().time)
    }

    fun ObservationType.randomNumericValue(): Float {
        return when(this) {
            ObservationType.MDC_ECG_HEART_RATE ->  kotlin.random.Random.nextInt(60, 70).toFloat()
            ObservationType.MDC_TEMP_BODY ->  kotlin.random.Random.nextInt(358, 370).toFloat() / 10f
            else -> Float.NaN
        }
    }


    fun startEmitter() {
        Timber.i("Starting GHS Observtaion Emitter")
        handler.post(notifyRunnable)
    }

    fun stopEmitter() {
        Timber.i("Stopping GHS Observtaion Emitter")
        handler.removeCallbacks(notifyRunnable)
    }

    fun singleShotEmit() {
        sendObservations(true)
    }

    private fun sendObservations(singleShot: Boolean) {
        Timber.i("Emitting ${observations.size} observations")
        generateObservationsToSend()
        kotlin.random.Random.nextInt(0, 100)
        if (mergeObservations) {
            ghsService?.sendObservations(observations)
        } else {
            observations.forEach { ghsService?.sendObservation(it) }
        }
        if (!singleShot) handler.postDelayed(notifyRunnable, (emitterPeriod * 1000).toLong())
    }

    // ServiceListener interface methods

    // Someone has connected so we need to listen to the GHS service for events
    override fun onConnected(numberOfConnections: Int) {
        ghsService?.addListener(this)
    }

    // If no connections then we no longer need to listen for events
    override fun onDisconnected(numberOfConnections: Int) {
        if (numberOfConnections == 0) {
            // Since stopping is under UI control, don't as this gets UI out of sync and should be under user control (or broadcast state)
            // stopEmitter()
            ghsService?.removeListener(this)
        }
    }

    // If someone is listening for observation notifies then start emitting them
    override fun onNotifyingEnabled(characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == GenericHealthSensorService.OBSERVATION_CHARACTERISTIC_UUID) {
            // For now don't start automatically...
            //startEmitter()
        }
    }

    // If stopped listening for observation notifies then stop emitting them
    override fun onNotifyingDisabled(ccharacteristic: BluetoothGattCharacteristic) {
        stopEmitter()
    }

    override fun onCharacteristicRead(characteristic: BluetoothGattCharacteristic) {}

    override fun onCharacteristicWrite(characteristic: BluetoothGattCharacteristic, value: ByteArray) {}

    override fun onDescriptorRead(descriptor: BluetoothGattDescriptor) {}

    override fun onDescriptorWrite(descriptor: BluetoothGattDescriptor, value: ByteArray) {}

}


fun ObservationType.randomSampleArray(): ByteArray {
    val numberOfCycles = 5
    val samplesPerSecond = kotlin.random.Random.nextInt(40, 70)
    val sampleSeconds = 5
    val buffer = ByteArray(samplesPerSecond * sampleSeconds)
    for (i in 0..buffer.size - 1) {
        // Straight sine function means one cycle every 2*pi samples:
        // buffer[i] = sin(i);
        // Multiply by 2*pi--now it's one cycle per sample:
        // buffer[i] = sin((2 * pi) * i);
        // Multiply by 1,000 samples per second--now it's 1,000 cycles per second:
        // buffer[i] = sin(1000 * (2 * pi) * i);
        buffer[i] = (Math.sin(numberOfCycles * (2 * Math.PI) * i / samplesPerSecond) * 200).toInt().toByte()
    }
    return buffer
}

fun ObservationType.numericPrecision(): Int {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  0
        ObservationType.MDC_TEMP_BODY ->  1
        else -> 0
    }
}

fun ObservationType.unitCode(): UnitCode {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  UnitCode.MDC_DIM_BEAT_PER_MIN
        ObservationType.MDC_TEMP_BODY ->  UnitCode.MDC_DIM_DEGC
        ObservationType.MDC_PPG_TIME_PD_PP ->  UnitCode.MDC_DIM_INTL_UNIT
        else -> UnitCode.MDC_DIM_INTL_UNIT
    }
}
