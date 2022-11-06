package com.philips.btserver.observations

import com.welie.blessed.BluetoothBytesParser
import java.nio.ByteOrder
import java.util.*

/**
 * Observtion is the abstract class representing various measured observations.
 * The varaints of observations are based on value (numeric, compound, sample arrays, strings, enums)
 * This abstract class provides the common implementation for representing common properites and
 * encoding observations into serialized byte arrays. Concrete classes of Observation simply need
 * to implement the value serialization to bytes (along with providing the value unit code and length).
 */
data class BundledObservation(
    override val id: Short,
    override val value: List<Observation>,
    override val timestamp: Date
) : Observation() {
    // BundledObservations have no observation type
    override var type: ObservationType = ObservationType.UNKNOWN_TYPE

    // BundledObservations have no unit code (each observation does)
    override val unitCode: UnitCode
        get() = UnitCode.UNKNOWN_CODE

    override val classByte: ObservationClass = ObservationClass.ObservationBundle   // Bundled observation

    /*
     * For bundled observations value bytes are a byte with number of observations
     * followed by bytes for each observation
     */
    override val valueByteArray: ByteArray
        get() {
            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
            parser.setIntValue(value.size, BluetoothBytesParser.FORMAT_UINT8)
            value.forEach {
                val bytesToSend = it.ghsBundledObservationByteArray
                parser.setByteArray(bytesToSend)
            }
            return parser.value
        }
}


val Observation.ghsBundledObservationByteArray: ByteArray
    get() { return ghsByteArray(isBundled = true) }
