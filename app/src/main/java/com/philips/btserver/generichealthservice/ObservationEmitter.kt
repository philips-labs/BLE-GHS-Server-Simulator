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
     * Experimental configuration options for observation data format
     */

    var experimentalObservationOptions = BitSet()

    var omitFixedLengthTypes: Boolean
        get() { return experimentalObservationOptions.get(Observation.ExperimentalFeature.OmitFixedLengthTypes.bit) }
        set(bool) { experimentalObservationOptions.set(Observation.ExperimentalFeature.OmitFixedLengthTypes.bit, bool)}

    var omitHandleTLV: Boolean
        get() { return experimentalObservationOptions.get(Observation.ExperimentalFeature.OmitHandleTLV.bit) }
        set(bool) { experimentalObservationOptions.set(Observation.ExperimentalFeature.OmitHandleTLV.bit, bool)}

    var omitUnitCode: Boolean
        get() { return experimentalObservationOptions.get(Observation.ExperimentalFeature.OmitUnitCode.bit) }
        set(bool) { experimentalObservationOptions.set(Observation.ExperimentalFeature.OmitUnitCode.bit, bool)}

    var useShortTypeCodes: Boolean
        get() { return experimentalObservationOptions.get(Observation.ExperimentalFeature.UseShortTypeCodes.bit) }
        set(bool) { experimentalObservationOptions.set(Observation.ExperimentalFeature.UseShortTypeCodes.bit, bool)}

    /*
     * Experiment with a type for an observation array observation (or compound observation observation)
     * This is an "enhanced" concept of the Compound Numeric Observation as it allows for an array
     * of full observations. From this a number of issues to explore arise such as timestamps being
     * duplicated in each observation in the array.
     *
     * Why this is important vs. simply using a Compound Numeric Observation can be demonstrated with the
     * following example:
     *
     * A fetal heart monitor reading for the maternal and fetal HRs could be expressed with a
     * Compound Numeric Observation (with the typeList [maternalHRType, fetalHRType] and the
     * valueList [maternalHR, fetalHR]. However for each HR there is other meta information that
     * needs to be associated (e.g. signal quality). These could be aggregated and included in
     * the supplemental information for the compound measurement. However, this would require
     * subtypes for aggregation (e..g a term for maternalSignalQuality, fetalSignalQuality).
     *
     * With the maternal and fetal heart rates for a given moment stored as a observation array each
     * could maintain their own supplemental terms for signal quality.
     */
    var enableObservationArrayType = false

    /*
     * If mergeObservations true observations are sent as one ACOM byte array.
     * false means send each observation as a separate ACOM Object
     *
     * Currently given there is no actual ACOM wrapper what this means from a data package
     * standpoint is that all observations are bundled as an array and sent in the same
     * sequence of packets (packets start on send of first, packets end at send of last)
     */
    var mergeObservations = false

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
        experimentalObservationOptions = BitSet()
        mergeObservations = true
    }

    /*
     * Private methods
     */

    private fun generateObservationsToSend() {
        observations.clear()
        observations.addAll(typesToEmit.mapNotNull { randomObservationOfType(it) })
    }

    private fun randomObservationOfType(type: ObservationType): Observation? {
        val obs = when(type.valueType()) {
            ObservationValueType.MDC_ATTR_NU_VAL_OBS_SIMP -> randomSimpleNumericObservation(type)
            ObservationValueType.MDC_ATTR_SA_VAL_OBS -> randomSampleArrayObservation(type)
            else -> null
        }
        obs?.experimentalOptions = experimentalObservationOptions
        return obs
    }

    private fun randomSimpleNumericObservation(type: ObservationType): Observation {
        return SimpleNumericObservation(lastHandle++.toShort(),
                type,
                type.randomNumericValue(),
                type.numericPrecision(),
                if (useShortTypeCodes) type.shortUnitCode() else type.unitCode(),
                Calendar.getInstance().time)
    }

    private fun randomSampleArrayObservation(type: ObservationType): Observation {
        return SampleArrayObservation(lastHandle++.toShort(),
                type,
                type.randomSampleArray(),
                type.emitterUnitCode(),
                Calendar.getInstance().time)
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

}

fun ObservationType.emitterUnitCode(): UnitCode {
    return if (ObservationEmitter.useShortTypeCodes) shortUnitCode() else unitCode()
}

fun ObservationType.randomNumericValue(): Float {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  kotlin.random.Random.nextInt(60, 70).toFloat()
        ObservationType.MDC_TEMP_BODY ->  kotlin.random.Random.nextInt(358, 370).toFloat() / 10f
        ObservationType.MDC_SPO2_OXYGENATION_RATIO ->  kotlin.random.Random.nextInt(970, 990).toFloat() / 10f
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
            ObservationType.MDC_SPO2_OXYGENATION_RATIO->  1
        else -> 0
    }
}

fun ObservationType.unitCode(): UnitCode {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  UnitCode.MDC_DIM_BEAT_PER_MIN
        ObservationType.MDC_TEMP_BODY ->  UnitCode.MDC_DIM_DEGC
        ObservationType.MDC_SPO2_OXYGENATION_RATIO -> UnitCode.MDC_DIM_PERCENT
        ObservationType.MDC_PPG_TIME_PD_PP ->  UnitCode.MDC_DIM_INTL_UNIT
        else -> UnitCode.MDC_DIM_INTL_UNIT
    }
}

fun ObservationType.shortUnitCode(): UnitCode {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE ->  UnitCode.MDC_DIM_BEAT_PER_MIN
        ObservationType.MDC_TEMP_BODY ->  UnitCode.MDC_DIM_DEGC
        ObservationType.MDC_SPO2_OXYGENATION_RATIO -> UnitCode.MDC_DIM_PERCENT
        ObservationType.MDC_PPG_TIME_PD_PP ->  UnitCode.MDC_DIM_INTL_UNIT
        else -> unitCode()
    }
}

fun ByteArray.fillWith(action: (Int) -> Byte) {
    for (i in 0 until size) { this[i] = action(i) }
}