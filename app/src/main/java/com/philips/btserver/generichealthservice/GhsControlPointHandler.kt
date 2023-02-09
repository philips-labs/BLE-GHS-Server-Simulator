package com.philips.btserver.generichealthservice

import com.philips.btserver.extensions.isIndicateEnabled
import com.philips.btserver.extensions.isNotifyEnabled
import com.philips.btserver.observations.ObservationEmitter
import com.welie.blessed.GattStatus
import timber.log.Timber

class GhsControlPointHandler(val service: GenericHealthSensorService) {

    private val observationCharacteristic get() = service.observationCharacteristic
    private val ghsControlPointCharacteristic get() = service.ghsControlPointCharacteristic

    fun isWriteValid(bytes: ByteArray): Boolean {
        return (bytes.size == 1) && ((bytes[0] == START_SEND_LIVE_OBSERVATIONS) || (bytes[0] == STOP_SEND_LIVE_OBSERVATIONS))
    }

    fun writeGattStatusFor(value: ByteArray): GattStatus {
        return if (isWriteValid(value))
                if (service.isLiveObservationNotifyEnabled) GattStatus.SUCCESS else GattStatus.CCCD_CFG_ERROR
            else GattStatus.fromValue(COMMAND_NOT_SUPPORTED)
        // GattStatus.INTERNAL_ERROR is 0x81 which is COMMAND_NOT_SUPPORTED
    }

    fun handleReceivedBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        when(bytes[0]) {
            START_SEND_LIVE_OBSERVATIONS -> startSendingLiveObservations()
            STOP_SEND_LIVE_OBSERVATIONS -> stopSendingLiveObservations()
            else -> return
        }
    }

    fun reset() {}

    private fun startSendingLiveObservations() {
        var enableSend = false
        var result = byteArrayOf(CONTROL_POINT_SUCCESS)
        // TODO: isIndicateEnabled and isNotifyEnabled is always returning false!
        if (isLiveObservationNotifyEnabled() && service.canHandleRACP) {
            Timber.i("Sending ${this.javaClass} startSendingLiveObservations successful")
            enableSend = true
        } else {
            Timber.i("Sending ${this.javaClass} startSendingLiveObservations without notify or indicate enabled")
            result = byteArrayOf(CONTROL_POINT_ERROR_LIVE_OBSERVATIONS)
        }

        service.setCharacteristicValueAndNotify(ghsControlPointCharacteristic, result)
        service.isLiveObservationsStarted = enableSend
    }

    private fun isLiveObservationNotifyEnabled(): Boolean {
        return service.isLiveObservationNotifyEnabled
//        return observationCharacteristic.isIndicateEnabled() or observationCharacteristic.isNotifyEnabled()
    }

    private fun stopSendingLiveObservations() {
        service.isLiveObservationsStarted = false
        Timber.i("Sending ${this.javaClass} stopSendingLiveObservations successful")
        val result = byteArrayOf(CONTROL_POINT_SUCCESS)
        service.setCharacteristicValueAndNotify(ghsControlPointCharacteristic, result)
    }

    companion object {
        /*
         * GHS Control Point commands and status values
         */
        const val START_SEND_LIVE_OBSERVATIONS = 0x01.toByte()
        const val STOP_SEND_LIVE_OBSERVATIONS = 0x02.toByte()
        private const val CONTROL_POINT_SUCCESS = 0x80.toByte()
        private const val CONTROL_POINT_SERVER_BUSY = 0x81.toByte()
        private const val CONTROL_POINT_ERROR_LIVE_OBSERVATIONS = 0x82.toByte()
        const val COMMAND_NOT_SUPPORTED = 0x81
    }
}