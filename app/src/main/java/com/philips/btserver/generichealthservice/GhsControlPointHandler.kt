package com.philips.btserver.generichealthservice

import com.philips.btserver.observations.ObservationEmitter
import com.welie.blessed.GattStatus

class GhsControlPointHandler(val service: GenericHealthSensorService) {

    private val observationCharacteristic get() = service.observationCharacteristic
    private val ghsControlPointCharacteristic get() = service.ghsControlPointCharacteristic

    fun isWriteValid(bytes: ByteArray): Boolean {
        return (bytes.size == 1) && ((bytes[0] == START_SEND_LIVE_OBSERVATIONS) || (bytes[0] == STOP_SEND_LIVE_OBSERVATIONS))
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

    private fun startSendingLiveObservations(): GattStatus {
        var result = byteArrayOf(CONTROL_POINT_SUCCESS)
        // TODO: isIndicateEnabled and isNotifyEnabled is always returning false!
//        if (observationCharacteristic.isIndicateEnabled() or observationCharacteristic.isNotifyEnabled()) {
//            ObservationEmitter.transmitEnabled = true
//        } else {
//            ObservationEmitter.transmitEnabled = false
//            result = byteArrayOf(CONTROL_POINT_ERROR_LIVE_OBSERVATIONS)
//            return GattStatus.CCCD_CFG_ERROR
//        }
        ObservationEmitter.transmitEnabled = true
        service.setCharacteristicValueAndNotify(result, ghsControlPointCharacteristic)
        return GattStatus.SUCCESS
    }

    private fun stopSendingLiveObservations() {
        ObservationEmitter.transmitEnabled = false
        val result = byteArrayOf(CONTROL_POINT_SUCCESS)
        service.setCharacteristicValueAndNotify(result, ghsControlPointCharacteristic)
    }

    companion object {
        /*
         * GHS Control Point commands and status values
         */
        const val START_SEND_LIVE_OBSERVATIONS = 0x01.toByte()
        const val STOP_SEND_LIVE_OBSERVATIONS = 0x02.toByte()
        private const val CONTROL_POINT_SUCCESS = 0x80.toByte()
//        private const val CONTROL_POINT_SERVER_BUSY = 0x81.toByte()
//        private const val CONTROL_POINT_ERROR_LIVE_OBSERVATIONS = 0x82.toByte()
    }
}