package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import com.philips.btserver.extensions.asBLEDataSegments
import com.philips.btserver.extensions.asFormattedHexString
import timber.log.Timber

class GhsObservationSendHandler(val service: GenericHealthSensorService) {
    private val observationCharacteristic get() = service.observationCharacteristic

    private var currentSegmentNumber: Int = 0
    private val segmentSize get() = service.minimalMTU - 5

    /**
     * Serialize an [observation] into a byte array transmit the bytes in one or more segments.
     */
    fun sendObservation(observation: Observation) {
        val bytes = observation.ghsByteArray
        sendBytesInSegments(bytes)
    }

    /**
     * Serialize and merge the [observations] into a byte array transmit the bytes in one or more segments.
     */
    fun sendObservations(observations: Collection<Observation>) {
        observations.forEach { sendObservation(it) }
    }

    fun reset() {
        currentSegmentNumber = 0
    }

    /**
     * Private ByteArray extension to break up the receiver into segments that fit in the MTU and
     * send each segment in sequence over BLE
     */
    private fun sendBytesInSegments(bytes: ByteArray) {
        // asBLEDataSegments returns Pair<List<ByteArray>, Int> with the segments and next segment number
        val segments = bytes.asBLEDataSegments(segmentSize, currentSegmentNumber)
        Timber.i("Sending ${bytes.size} bytes in ${segments.first.size} segments")
        segments.first.forEach {
            Timber.i("Sending segment bytes: <${it.asFormattedHexString()}>")
            service.sendBytesAndNotify(it, observationCharacteristic)
        }
        currentSegmentNumber = segments.second
    }
}