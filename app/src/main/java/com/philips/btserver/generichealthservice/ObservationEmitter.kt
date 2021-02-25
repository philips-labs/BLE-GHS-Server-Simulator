package com.philips.btserver.generichealthservice


import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.util.*

object ObservationEmitter {

    /*
     * ObservationEmitter public property configuration options
     */

    /*
     * A "short" type code is to experiment with using 2-byte (16-bit) observation type
     * codes rather than 4-byte full MDC codes... however, this implies the receiver can
     * map them back to MDC codes, which potentially dilutes the saving of 2 bytes from each
     * type code element. However if
     */
    var useShortTypeCodes: Boolean = false

    /*
     * Omitting length on TLVs where the "Ts" (types) are of known, fixed length.
     * The types that have a known length are:
     *      Handle
     *      Unit Code
     *
     * Notes:
     *
     * Multiple time lengths (4, 6, 8 bytes) are being investigated/supported. However,
     * it is assumed that timestamps (and timezone) will end up being a fixed length type.
     *
     * As more of the ACOM model is implemented, more types will be included and any
     * that are fixed length are assumed to be included in this list
     */
    var omitFixedLengthTypes = false

    /*
     * Although specified as required the handle is only used if the object is referenced
     * in another object. This is not the case in a vast majority of use cases, so allow
     * the handle element to not be included, unless it will be used in another reference.
     *
     * This also implies that handle should (can?) not be the first element.
     *
     */
    var omitHandleTLV = false

    /*
     * For many observation types (heart rate, blood pressure, spO2, etc) the units are
     * known and standard, with alternative units being an exception/edge case.
     *
     * This allows the omission ot the unit code in which case the receiver can assume
     * the "standard" unit (e.g. for heart rate bpm, for temperatures degrees centigrade...
     * etc... though for measurues like weight there would need to be agreement on grams or
     * kilograms)
     */
    var omitUnitCode = false

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
     * false means send each observation as a sepeate ACOM Object
     *
     * Currently given there is no actual ACOM wrapper what this means from a data package
     * standpoint is that all observations are bundled as an array and sent in the same
     * sequence of packets (packets start on send of first, packets end at send of last)
     */
    var mergeObservations = true

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

fun ByteArray.fillWith(action: (Int) -> Byte) {
    for (i in 0..size - 1) { this[i] = action(i) }
}