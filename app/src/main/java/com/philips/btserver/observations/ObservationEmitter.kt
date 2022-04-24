/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.observations

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.philips.btserver.generichealthservice.GenericHealthSensorService
import timber.log.Timber
import java.util.*

object ObservationEmitter {

    /*
     * This property enables/disables observation emissions
     */
    var transmitEnabled = false


    /*
     * This property enables/disables observation emissions
     */
    var isEmitting = false
        private set

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

    val observationTypes = mutableSetOf<ObservationType>()

    // Made public for unit testing
    private val ghsService: GenericHealthSensorService?
        get() = GenericHealthSensorService.getInstance()

    /*
     * Public methods
     */

    fun addObservationType(type: ObservationType) {
        observationTypes.add(type)
        resetStoredObservations()
        setFeatureCharacteristicTypes()
    }

    fun removeObservationType(type: ObservationType) {
        observationTypes.remove(type)
        observations.removeAll { it.type == type }
        resetStoredObservations()
        setFeatureCharacteristicTypes()
    }

    fun startEmitter() {
        Timber.i("Starting GHS Observtaion Emitter")
        isEmitting = true
        handler.post(notifyRunnable)
    }

    fun stopEmitter() {
        Timber.i("Stopping GHS Observtaion Emitter")
        isEmitting = false
        handler.removeCallbacks(notifyRunnable)
    }

    fun singleShotEmit() {
        sendObservations(true)
    }

    fun reset() {
        observationTypes.clear()
        setFeatureCharacteristicTypes()
        observations.clear()
        lastHandle = 1
        mergeObservations = true
    }

    /*
     * Private methods
     */

    private fun generateObservationsToSend() {
        observations.clear()
        val obsList = observationTypes.mapNotNull { randomObservationOfType(it, Date()) }
        if (bundleObservations) {
            observations.add(BundledObservation(1, obsList, Date()))
        } else {
            observations.addAll(obsList)
        }
    }

    private fun randomObservationOfType(type: ObservationType, timestamp: Date): Observation? {
        val obs = when(type.valueType()) {
            ObservationValueType.MDC_ATTR_NU_VAL_OBS_SIMP -> randomSimpleNumericObservation(type, timestamp)
            ObservationValueType.MDC_ATTR_SA_VAL_OBS -> randomSampleArrayObservation(type, timestamp)
            ObservationValueType.MDC_ATTR_NU_CMPD_VAL_OBS -> randomCompoundNumericObservation(type, timestamp)
            else -> null
        }
        return obs
    }

    private fun randomSimpleNumericObservation(type: ObservationType, timestamp: Date): Observation {
        return SimpleNumericObservation(lastHandle++.toShort(),
                type,
                type.randomNumericValue(),
                type.numericPrecision(),
                type.unitCode(),
                timestamp)
    }

    private fun randomSampleArrayObservation(type: ObservationType, timestamp: Date): Observation {
        return SampleArrayObservation(lastHandle++.toShort(),
            type,
            type.randomSampleArray(),
            type.unitCode(),
            timestamp)
    }

    private fun randomCompoundNumericObservation(type: ObservationType, timestamp: Date): Observation {
        // Right now only compound is BP
        val systolicValue = SimpleNumericValue(
            ObservationType.MDC_PRESS_BLD_NONINV_SYS,
            ObservationType.MDC_PRESS_BLD_NONINV_SYS.randomNumericValue(),
            ObservationType.MDC_PRESS_BLD_NONINV_SYS.unitCode(),
        )
        val diastolicValue = SimpleNumericValue(
            ObservationType.MDC_PRESS_BLD_NONINV_DIA,
            ObservationType.MDC_PRESS_BLD_NONINV_DIA.randomNumericValue(),
            ObservationType.MDC_PRESS_BLD_NONINV_DIA.unitCode(),
        )
        return CompoundNumericObservation(
            lastHandle++.toShort(),
            type,
            arrayOf(systolicValue, diastolicValue),
            UnitCode.UNKNOWN_CODE,
            timestamp
        )
    }

    private fun setFeatureCharacteristicTypes() {
        ghsService?.setFeatureCharacteristicTypes(observationTypes.toList())
    }

    private fun sendObservations(singleShot: Boolean) {
        generateObservationsToSend()
        Timber.i("Emitting ${observations.size} observations")
        if (ghsService?.noCentralsConnected() ?: true) {
            observations.forEach { ObservationStore.addObservation(it) }
        } else {
            if (transmitEnabled) {
                observations.forEach { ghsService?.sendObservation(it) }
            }
        }
        if (!singleShot) handler.postDelayed(notifyRunnable, (emitterPeriod * 1000).toLong())
    }

    private fun resetStoredObservations() { ObservationStore.clear() }

}

fun ObservationType.randomNumericValue(): Float {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  kotlin.random.Random.nextInt(60, 70).toFloat()
        ObservationType.MDC_TEMP_BODY ->  kotlin.random.Random.nextInt(358, 370).toFloat() / 10f
        ObservationType.MDC_PULS_OXIM_SAT_O2 ->  kotlin.random.Random.nextInt(970, 990).toFloat() / 10f
        ObservationType.MDC_PRESS_BLD_NONINV_SYS ->  kotlin.random.Random.nextInt(120, 130).toFloat()
        ObservationType.MDC_PRESS_BLD_NONINV_DIA ->  kotlin.random.Random.nextInt(70, 80).toFloat()
        else -> Float.NaN
    }
}

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
        ObservationType.MDC_ECG_HEART_RATE,
        ObservationType.MDC_TEMP_BODY,
        ObservationType.MDC_PULS_OXIM_SAT_O2 ->  1
        else -> 0
    }
}

fun ObservationType.unitCode(): UnitCode {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE -> UnitCode.MDC_DIM_BEAT_PER_MIN
        ObservationType.MDC_TEMP_BODY -> UnitCode.MDC_DIM_DEGC
        ObservationType.MDC_PULS_OXIM_SAT_O2 -> UnitCode.MDC_DIM_PERCENT
        ObservationType.MDC_PPG_TIME_PD_PP -> UnitCode.MDC_DIM_INTL_UNIT
        ObservationType.MDC_PRESS_BLD_NONINV -> UnitCode.MDC_DIM_MMHG
        ObservationType.MDC_PRESS_BLD_NONINV_SYS -> UnitCode.MDC_DIM_MMHG
        ObservationType.MDC_PRESS_BLD_NONINV_DIA -> UnitCode.MDC_DIM_MMHG
        else -> UnitCode.MDC_DIM_INTL_UNIT
    }
}

fun ByteArray.fillWith(action: (Int) -> Byte) {
    for (i in 0 until size) { this[i] = action(i) }
}