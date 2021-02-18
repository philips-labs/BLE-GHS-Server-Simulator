package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Handler
import android.os.Looper
import com.philips.btserver.ServiceListener
import timber.log.Timber
import java.util.*

object ObservationEmitter: ServiceListener {

    var emitterPeriod = 10
    /*
     * If mergeObservations true observations are sent as one ACOM byte array.
     * false means send each observation as a sepeate ACOM Object
     *
     * Currently given there is no actual ACOM wrapper what this means from a data package
     * standpoint is that all observations are bundled as an array and sent in the same
     * sequence of packets (packets start on send of first, packets end at send of last)
     */
    var mergeObservations = true

    val observations = mutableListOf<Observation>()
    private val handler = Handler(Looper.myLooper() ?: Looper.getMainLooper())
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

    /*
     * ObservationEmitter options
     */

    fun shortTypeCodes(enable: Boolean) {
    }

    fun omitFixedLengthTypes(omit: Boolean) {
    }

    fun omitHandleTLV(omit: Boolean) {
    }

    fun omitUnitCode(omit: Boolean) {
    }

    fun enableObservationArrayType(enable: Boolean) {
    }

    /*
     * Private methods
     */
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
        if (mergeObservations) {
            ghsService?.sendObservations(observations)
        } else {
            observations.forEach { ghsService?.sendObservation(it) }
        }
        if (!singleShot) handler.postDelayed(notifyRunnable, (emitterPeriod * 1000).toLong())
    }

    // BEGIN - ServiceListener interface methods

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
            // For now don't start automatically... uncomment if behavior desired
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

    // END - ServiceListener interface methods

}

fun ObservationType.randomNumericValue(): Float {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  kotlin.random.Random.nextInt(60, 70).toFloat()
        ObservationType.MDC_TEMP_BODY ->  kotlin.random.Random.nextInt(358, 370).toFloat() / 10f
        ObservationType.MDC_SPO2_OXYGENATION_RATIO ->  kotlin.random.Random.nextInt(970, 990).toFloat() / 10f
        else -> Float.NaN
    }
}

// TODO: For now regardless of type the sample array is just totally random and alway a 255 element byte array
fun ObservationType.randomSampleArray(): ByteArray {
    val numberOfCycles = 5
    val samplesPerSecond = kotlin.random.Random.nextInt(40, 70)
    val sampleSeconds = 5
    val buffer = ByteArray(samplesPerSecond * sampleSeconds)
    buffer.fillWith { i -> (Math.sin(numberOfCycles * (2 * Math.PI) * i / samplesPerSecond) * 200).toInt().toByte() }
    return buffer
}

fun ObservationType.numericPrecision(): Int {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  0
        ObservationType.MDC_TEMP_BODY,
        ObservationType.MDC_SPO2_OXYGENATION_RATIO ->  1
        else -> 0
    }
}

fun ObservationType.unitCode(): UnitCode {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE -> UnitCode.MDC_DIM_BEAT_PER_MIN
        ObservationType.MDC_TEMP_BODY -> UnitCode.MDC_DIM_DEGC
        ObservationType.MDC_SPO2_OXYGENATION_RATIO -> UnitCode.MDC_DIM_PERCENT
        ObservationType.MDC_PPG_TIME_PD_PP -> UnitCode.MDC_DIM_INTL_UNIT
        else -> UnitCode.MDC_DIM_INTL_UNIT
    }
}

fun ByteArray.fillWith(action: (Int) -> Byte) {
    for (i in 0..size - 1) { this[i] = action(i) }
}