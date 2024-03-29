package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import com.philips.btserver.extensions.asBLEDataSegments
import com.philips.btserver.extensions.asFormattedHexString
import com.philips.btserver.extensions.merge
import com.philips.btserver.observations.Observation
import com.philips.btserver.observations.ObservationStore
import com.philips.btserver.userdataservice.currentUserIndex
import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.GattStatus
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executors

class GhsObservationSendHandler(val service: GenericHealthSensorService, val observationCharacteristic: BluetoothGattCharacteristic) {
    var delayBetweenObservationSends = false

    private var currentSegmentNumber: Int = 0
    private val segmentSize get() = service.minimalMTU - 5
    private var sendingObservations = false

    /**
     * Serialize an [observation] into a byte array transmit the bytes in one or more segments.
     */
    // TODO CHECK FOR VALID CENTRALS TO SEND TO
    fun sendObservation(observation: Observation, isStored: Boolean = false) {
        val bytes = if (isStored) {
            val parser = BluetoothBytesParser()
            val recordId = ObservationStore.recordIdFor(observation)
            parser.setIntValue(recordId, BluetoothBytesParser.FORMAT_UINT32)
            Timber.i("Send Stored Observation record id: $recordId")
            parser.value + observation.ghsByteArray
        } else {
            observation.ghsByteArray
        }
        sendBytesInSegments(bytes, centralsForObservation(observation))
    }

    /**
     * Serialize and merge the [observations] into a byte array transmit the bytes in one or more segments.
     */
    fun sendObservations(observations: Collection<Observation>, isStored: Boolean = false) {
        Executors.newSingleThreadExecutor().execute {
            sendingObservationsComplete(observations, interruptableSendObservations(observations, isStored))
        }
    }

    /*
     * Send the observations sent in, allowing for interruption (via the sendingObservations property)
     *
     * @param observations  Observations to be sent
     * @return true if all observations were sent, false if the sending was interrupted
     */
    private fun interruptableSendObservations(observations: Collection<Observation>,  isStored: Boolean): Boolean {
        sendingObservations = true
        observations.forEach {
            if (!sendingObservations) {
                Timber.i("Aborting sendObservations")
                return false
            }
            sendObservation(it, isStored)
            if (delayBetweenObservationSends) { Thread.sleep(1000) }
        }
        return true
    }

    private fun sendingObservationsComplete(observations: Collection<Observation>, complete: Boolean) {
        // TODO do we want to let listners know if the xfer was aborted??
        if (isStoredObservationHandler()) {
            if (complete) service.listeners.forEach { it.onStoredObservationsSent(observations) }
        } else {
            if (complete) service.listeners.forEach { it.onObservationsSent(observations) }
        }
        sendingObservations = false
    }

    fun reset() {
        currentSegmentNumber = 0
    }

    fun abortSendStoredObservations() {
        observationsToSend.clear()
        sendingObservations = false
    }

    private fun isStoredObservationHandler(): Boolean {
        return service.isStoredObservationCharacteristic(observationCharacteristic)
    }

    /**
     * Private ByteArray extension to break up the receiver into segments that fit in the MTU and
     * send each segment in sequence over BLE
     */
    private fun sendBytesInSegments(bytes: ByteArray, centrals: List<BluetoothCentral>) {
        if (centrals.isNotEmpty()) {
            // asBLEDataSegments returns Pair<List<ByteArray>, Int> with the segments and next segment number
            val segments = bytes.asBLEDataSegments(segmentSize, currentSegmentNumber)
            Timber.i("Sending ${bytes.size} bytes in ${segments.first.size} segments")
            Timber.i("Raw ${bytes.size} bytes: [ ${bytes.asFormattedHexString()} ]")
            segments.first.forEach {
                Timber.i("Sending segment bytes: <${it.asFormattedHexString()}>")
                service.sendBytesAndNotify(it, observationCharacteristic, centrals)
            }
            currentSegmentNumber = segments.second
        }
    }

    var observationsToSend = mutableListOf<Observation>()
    var segmentsToSend = mutableListOf<ByteArray>()

//    fun sendObservationsUnqueued(observations: Collection<Observation>) {
//        if (!sendingObservations) {
//            sendingObservations = true
//            observationsToSend.addAll(observations)
//            Executors.newSingleThreadExecutor().execute {
//                sendNextObservation()
//                observations.forEach {
//                    sendBytesInSegmentsUnqueued(it.ghsByteArray)
//                }
//                sendingObservations = false
//            }
//        }
//    }

    /**
     * Private ByteArray extension to break up the receiver into segments that fit in the MTU and
     * send each segment in sequence over BLE
     */
    private fun sendBytesInSegmentsUnqueued(bytes: ByteArray) {
        // asBLEDataSegments returns Pair<List<ByteArray>, Int> with the segments and next segment number
        val segments = bytes.asBLEDataSegments(segmentSize, currentSegmentNumber)
        Timber.i("Sending ${bytes.size} bytes in ${segments.first.size} segments")
        Timber.i("Raw ${bytes.size} bytes: [ ${bytes.asFormattedHexString()} ]")
        segmentsToSend = segments.first.toMutableList()
        sendNextSegment()
        currentSegmentNumber = segments.second
    }

    private fun sendNextObservation() {
        observationsToSend.removeFirstOrNull()?.let { sendBytesInSegmentsUnqueued(it.ghsByteArray) }
    }

    private fun sendNextSegment() {
        if (segmentsToSend.isNotEmpty()) {
            val segment = segmentsToSend.removeFirst()
            Timber.i("Sending segment bytes: <${segment.asFormattedHexString()}>")
            service.sendBytesAndNotify(segment, observationCharacteristic)
        } else {
            sendNextObservation()
        }
    }

    private fun centralsForObservation(observation: Observation): List<BluetoothCentral> {
        return if(observation.patientId == 0xFF)
            service.getConnectedCentrals().toList()
        else
            service.getConnectedCentrals().filter {
                val centralUser = it.currentUserIndex()
                (centralUser == 0xff) || (centralUser == observation.patientId)
            }
    }

    /**
     * A segment has been sent (with notification)
     *
     * @param segment the value of the notification
     * @param status the status of the operation
     */
    fun onSegmentSent(value: ByteArray, status: GattStatus) {
        if (status != GattStatus.SUCCESS) {
            Timber.i("ERROR: Sending observation segment: ${value.asFormattedHexString()}")
        }
        sendNextSegment()
    }
}