package com.philips.btserver.generichealthservice

import com.philips.btserver.extensions.asLittleEndianArray
import com.philips.btserver.extensions.merge
import com.philips.btserver.extensions.uInt16At
import java.lang.Integer.max
import java.lang.Integer.min

class GhsRacpHandler(val service: GenericHealthSensorService) {

    private val observationCharacteristic get() = service.storedObservationCharacteristic
    private val racpCharacteristic get() = service.racpCharacteristic

    private val storedRecords  get() = ObservationEmitter.storedObservations
    private val numberStoredRecords  get() = storedRecords.size

    fun reset() {}

    fun handleReceivedBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        when(bytes[0]) {
            OP_CODE_REPORT_STORED_RECORDS -> reportStoredRecords(bytes)
            OP_CODE_NUMBER_STORED_RECORDS -> reportNumberStoredRecords(bytes)
        }
    }

    private fun reportStoredRecords(bytes: ByteArray) {

    }

    private fun reportNumberStoredRecords(bytes: ByteArray) {
        val numRecords = queryNumberStoredRecords(bytes)
        val response = numRecords?.let {
            listOf(
                byteArrayOf(OP_CODE_RESPONSE_NUMBER_STORED_RECORDS, OP_NULL),
                it.asLittleEndianArray()
            ).merge()
        } ?: byteArrayOf(OP_CODE_RESPONSE_CODE, 0x03) // TODO: Determine proper error code

        // TODO: Ask Martijn about Blessed BluetoothPeripheralManager notifyCharacteristicChanged and indicate
        service.sendBytesAndNotify(response, racpCharacteristic)
    }

    private fun queryNumberStoredRecords(bytes: ByteArray): Int? {
        if (bytes.size < 2) return null
        if (numberStoredRecords == 0) return 0
        return when(bytes[1]) {
            OP_NULL, OP_ALL_RECORDS -> numberStoredRecords
            OP_FIRST_RECORD, OP_LAST_RECORD -> 1
            OP_LESS_THAN_OR_EQUAL -> queryNumberLessOrEqual(bytes)
            OP_GREATER_THAN_OR_EQUAL -> queryNumberGreaterOrEqual(bytes)
            else -> null
        }
    }

    // Right now query is on record number which is sequential from 0 to number of records
    private fun queryNumberLessOrEqual(bytes: ByteArray): Int? {
        if (bytes.size < 4) return null
        val maxValue = bytes.uInt16At(2)
        return min(numberStoredRecords, maxValue)
    }

    // Right now query is on record number which is sequential from 0 to number of records
    private fun queryNumberGreaterOrEqual(bytes: ByteArray): Int? {
        if (bytes.size < 4) return null
        val minValue = bytes.uInt16At(2)
        return max(0, numberStoredRecords - minValue + 1)
    }

    companion object {
        /*
         * RACP Operator Code Values
         */
        private const val OP_CODE_REPORT_STORED_RECORDS = 0x01.toByte()
        private const val OP_CODE_DELETE_STORED_RECORDS = 0x02.toByte()
        private const val OP_CODE_ABORT = 0x03.toByte()
        private const val OP_CODE_NUMBER_STORED_RECORDS = 0x04.toByte()
        private const val OP_CODE_RESPONSE_NUMBER_STORED_RECORDS = 0x05.toByte()
        private const val OP_CODE_RESPONSE_CODE = 0x06.toByte()
        private const val OP_CODE_COMBINED_REPORT = 0x07.toByte()
        private const val OP_CODE_RESPONSE_COMBINED_REPORT = 0x08.toByte()

        /*
         * RACP Operator Values
         */
        private const val OP_NULL = 0x0.toByte()
        private const val OP_ALL_RECORDS = 0x01.toByte()
        private const val OP_LESS_THAN_OR_EQUAL = 0x02.toByte()
        private const val OP_GREATER_THAN_OR_EQUAL = 0x03.toByte()
        private const val OP_WITHIN_RANGE = 0x04.toByte()
        private const val OP_FIRST_RECORD = 0x05.toByte()
        private const val OP_LAST_RECORD = 0x06.toByte()

    }

}