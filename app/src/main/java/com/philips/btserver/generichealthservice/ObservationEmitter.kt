/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.util.*

object ObservationEmitter {

    /*
     * If mergeObservations true observations are sent as one ACOM byte array.
     * false means send each observation as a separate ACOM Object
     *
     * Currently given there is no actual ACOM wrapper what this means from a data package
     * standpoint is that all observations are bundled as an array and sent in the same
     * sequence of packets (packets start on send of first, packets end at send of last)
     */
    var mergeObservations = false


    /*
     * If bundleObservations true heart rate and SpO2 observations are sent as one bundle observation.
     * False means send each observation as a separate observation.
     *
     * These are only bundled if both heart rate and SpO2 are included in observation types sent
     */
    var bundleObservations = false

    // Interval to emit observations (in seconds)
    var emitterPeriod = 10

    /*
     * ObservationEmitter private properties
     */

    private val observations = mutableListOf<Observation>()
    private val handler = Handler(Looper.myLooper() ?: Looper.getMainLooper())
    private val notifyRunnable = Runnable { sendObservations(false) }

    private var lastHandle = 1

    private val typesToEmit = mutableSetOf<ObservationType>()

    // Made public for unit testing
    private val ghsService: GenericHealthSensorService?
        get() = GenericHealthSensorService.getInstance()

    /*
     * Public methods
     */

    fun addObservationType(type: ObservationType) {
        typesToEmit.add(type)
    }

    fun removeObservationType(type: ObservationType) {
        typesToEmit.remove(type)
        observations.removeAll { it.type == type }
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

    fun reset() {
        typesToEmit.clear()
        observations.clear()
        lastHandle = 1
        mergeObservations = true
    }

    /*
     * Private methods
     */

    private fun generateObservationsToSend() {
        observations.clear()
        val obsList = typesToEmit.mapNotNull { randomObservationOfType(it) }
        if (bundleObservations) {
            observations.add(BundledObservation(1, obsList, Date()))
        } else {
            observations.addAll(obsList)
        }
    }

    private fun randomObservationOfType(type: ObservationType): Observation? {
        val obs = when(type.valueType()) {
            ObservationValueType.MDC_ATTR_NU_VAL_OBS_SIMP -> randomSimpleNumericObservation(type)
            ObservationValueType.MDC_ATTR_SA_VAL_OBS -> randomSampleArrayObservation(type)
            else -> null
        }
        return obs
    }

    private fun randomSimpleNumericObservation(type: ObservationType): Observation {
        return SimpleNumericObservation(lastHandle++.toShort(),
                type,
                type.randomNumericValue(),
                type.numericPrecision(),
                type.unitCode(),
                Date())
    }

    private fun randomSampleArrayObservation(type: ObservationType): Observation {
        return SampleArrayObservation(lastHandle++.toShort(),
                type,
                type.randomSampleArray(),
                type.unitCode(),
                Date())
    }

    private fun sendObservations(singleShot: Boolean) {
        generateObservationsToSend()
        Timber.i("Emitting ${observations.size} observations")
        observations.forEach { ghsService?.sendObservation(it) }
        if (!singleShot) handler.postDelayed(notifyRunnable, (emitterPeriod * 1000).toLong())
    }

}

fun ObservationType.randomNumericValue(): Float {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  kotlin.random.Random.nextInt(60, 70).toFloat()
        ObservationType.MDC_TEMP_BODY ->  kotlin.random.Random.nextInt(358, 370).toFloat() / 10f
        ObservationType.MDC_PULS_OXIM_SAT_O2 ->  kotlin.random.Random.nextInt(970, 990).toFloat() / 10f
        else -> Float.NaN
    }
}

// For now regardless of type the sample array is just totally random and alway a 255 element byte array (thus observation type is unused)
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
            ObservationType.MDC_PULS_OXIM_SAT_O2->  1
        else -> 0
    }
}

fun ObservationType.unitCode(): UnitCode {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  UnitCode.MDC_DIM_BEAT_PER_MIN
        ObservationType.MDC_TEMP_BODY ->  UnitCode.MDC_DIM_DEGC
        ObservationType.MDC_PULS_OXIM_SAT_O2 -> UnitCode.MDC_DIM_PERCENT
        ObservationType.MDC_PPG_TIME_PD_PP ->  UnitCode.MDC_DIM_INTL_UNIT
        else -> UnitCode.MDC_DIM_INTL_UNIT
    }
}

fun ByteArray.fillWith(action: (Int) -> Byte) {
    for (i in 0 until size) { this[i] = action(i) }
}