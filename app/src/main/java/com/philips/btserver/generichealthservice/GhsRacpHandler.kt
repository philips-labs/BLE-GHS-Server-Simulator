package com.philips.btserver.generichealthservice

import android.os.Handler
import com.philips.btserver.extensions.asFormattedHexString
import com.philips.btserver.extensions.asLittleEndianUint32Array
import com.philips.btserver.extensions.merge
import com.philips.btserver.observations.Observation
import com.philips.btserver.observations.ObservationEmitter
import com.philips.btserver.observations.ObservationStore
import com.welie.blessed.BluetoothBytesParser
import timber.log.Timber
import java.nio.ByteOrder

class GhsRacpHandler(val service: GenericHealthSensorService): GenericHealthSensorServiceListener {

    private val racpCharacteristic get() = service.racpCharacteristic
    private val storedRecords get() = ObservationStore.storedObservations.toList()
    private val numberStoredRecords get() = storedRecords.size

    init {
        // TODO can't do this because this is null as we're initializing... check this out..
        // service.addListener(this)
    }

    fun startup() : GhsRacpHandler {
        service.addListener(this)
        return this
    }

    fun reset() {}

    fun isWriteValid(bytes: ByteArray): Boolean {
        return true
    }


    fun handleReceivedBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return sendInvalidOperator(OP_NULL)
        val opCode = bytes.racpOpCode()
        if(!service.canHandleRACP) return sendServerBusy(opCode)
        when(opCode) {
            OP_CODE_ABORT -> abortGetRecords(bytes)
            OP_CODE_COMBINED_REPORT -> reportCombinedStoredRecords(bytes)
            OP_CODE_NUMBER_STORED_RECORDS -> reportNumberStoredRecords(bytes)
            OP_CODE_DELETE_STORED_RECORDS -> deleteStoredRecords(bytes)
            else -> sendUnsupportedOpCode(opCode)
        }
    }

    override fun onStoredObservationsSent(observations: Collection<Observation>) {
        sendNumberCombinedStoredRecords(observations.size)
    }

    private fun abortGetRecords(bytes: ByteArray)   {
        if (bytes.size == 2 && bytes[1] == OP_NULL) {
            service.abortSendStoredObservations()
            // TODO Cheap way to make sure the Bluetooth buffers are empty
            Handler().postDelayed({
                sendSuccessResponse(bytes.racpOpCode())
            }, 1500)
        } else {
            sendInvalidOperatorResponse(bytes.racpOpCode())
        }
    }

    private fun reportCombinedStoredRecords(bytes: ByteArray) {
        val records = queryStoredRecords(bytes)
        // If records is null then something happened and a response has already been sent
        records?.let {
            if (it.isEmpty()) {
                sendNoRecordsFound(bytes.racpOpCode())
            } else {
                service.sendStoredObservations(it)
            }
        }
    }

    private fun reportNumberStoredRecords(bytes: ByteArray) {
        val numberOfRecords = queryNumberStoredRecords(bytes)
        // If number of records is null then something happened and a response has already been sent
        numberOfRecords?.let {
            if (it == 0) {
                sendNoRecordsFound(bytes.racpOpCode())
            } else {
                sendNumberStoredRecords(it)
            }
        }
    }

    private fun deleteStoredRecords(bytes: ByteArray) {
        if (bytes.size < 2) {
            sendInvalidOperator(OP_NULL)
        } else {
            val operator = bytes.racpOperator()

            when(operator) {
                OP_ALL_RECORDS -> {
                    ObservationStore.clear()
                    sendSuccessResponse(bytes.racpOpCode())
                }
                in listOf(OP_WITHIN_RANGE, OP_FIRST_RECORD, OP_LAST_RECORD, OP_LESS_THAN_OR_EQUAL, OP_GREATER_THAN_OR_EQUAL) -> {
                    sendUnsupportedOperator(bytes.racpOpCode())
                }
                else -> {
                    sendInvalidOperator(operator)
                }
            }
        }
    }

    private fun queryStoredRecords(bytes: ByteArray): List<Observation>? {
        return if (bytes.size < 2) {
            // TODO Confirm the code to send when there is no operator byte sent (using NULL now)
            sendInvalidOperator(OP_NULL)
            null
        } else {
            when(bytes.racpOperator()) {
                OP_ALL_RECORDS -> storedRecords
                OP_GREATER_THAN_OR_EQUAL -> queryRecordsGreaterOrEqual(bytes)
                in listOf(OP_WITHIN_RANGE, OP_FIRST_RECORD, OP_LAST_RECORD, OP_LESS_THAN_OR_EQUAL) -> {
                    sendUnsupportedOperator(bytes.racpOpCode())
                    null
                }
                else -> {
                    sendInvalidOperator(bytes.racpOpCode())
                    null
                }
            }
        }
    }

    private fun queryNumberStoredRecords(bytes: ByteArray): Int? {
        return if (bytes.size < 2) {
            // TODO Confirm the code to send when there is no operator byte sent (using NULL now)
            sendInvalidOperator(OP_NULL)
            null
        } else {
            when(bytes.racpOperator()) {
                OP_ALL_RECORDS -> numberStoredRecords
                OP_GREATER_THAN_OR_EQUAL -> queryNumberGreaterOrEqual(bytes)
                in listOf(OP_WITHIN_RANGE, OP_FIRST_RECORD, OP_LAST_RECORD, OP_LESS_THAN_OR_EQUAL) -> {
                    sendUnsupportedOperator(bytes.racpOpCode())
                    null
                }
                else -> {
                    sendInvalidOperator(bytes.racpOpCode())
                    null
                }
            }
        }
    }

    // Right now query is on record number which is sequential from 0 to number of records
    private fun queryNumberGreaterOrEqual(bytes: ByteArray): Int? {
        return if (bytes.size < 7) {
            sendInvalidOperand(bytes.racpOpCode())
            null
        } else if (!isValidFilterType(bytes)) {
            sendUnsupportedOperand(bytes.racpOpCode())
            null
        } else {
            return if (isSupportedFilterType(bytes)) {
                val minValue = getQueryRecordNumber(bytes)
                ObservationStore.numberOfObservationsEqualOrGreaterThanRecordNumber(minValue)
//            max(0, numberStoredRecords - minValue + 1)
            } else {
                sendUnsupportedOperand(bytes.racpOpCode())
                null
            }
        }
    }

    // Right now query is on record number which is sequential from 0 to number of records
    private fun queryRecordsGreaterOrEqual(bytes: ByteArray): List<Observation>? {
        return if (bytes.size < 7) {
            sendInvalidOperand(bytes.racpOpCode())
            null
        } else if (!isValidFilterType(bytes)) {
            sendUnsupportedOperand(bytes.racpOpCode())
            null
        } else {
            recordsGreaterOrEqual(bytes)
        }
    }

    private fun recordsGreaterOrEqual(bytes: ByteArray): List<Observation>? {
        return if (isSupportedFilterType(bytes)) {
            val minValue = getQueryRecordNumber(bytes)
            ObservationStore.observationsEqualOrGreaterThanRecordNumber(minValue)
//            max(0, numberStoredRecords - minValue + 1)
        } else {
            sendUnsupportedOperand(bytes.racpOpCode())
            null
        }
    }

    private fun getQueryRecordNumber(bytes: ByteArray): Int {
        val parser = BluetoothBytesParser(bytes.copyOfRange(3,7))
        return parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32, ByteOrder.LITTLE_ENDIAN)
    }

    private fun isValidFilterType(bytes: ByteArray): Boolean {
        return (bytes.size > 3) && ((bytes[2] == OP_FILTER_TYPE_VALUE_REC_NUM) || (bytes[2] == OP_FILTER_TYPE_VALUE_TIME))
    }

    private fun isSupportedFilterType(bytes: ByteArray): Boolean {
        return (bytes.size > 3) && (bytes[2] == OP_FILTER_TYPE_VALUE_REC_NUM)
    }

    private fun sendInvalidOperatorResponse(requestOpCode: Byte) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_INVALID_OPERATOR)
    }

    private fun sendSuccessResponse(requestOpCode: Byte) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_SUCCESS)
    }

    private fun sendInvalidOperator(requestOpCode: Byte) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_INVALID_OPERATOR)
    }

    private fun sendUnsupportedOpCode(requestOpCode: Byte) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_OP_CODE_UNSUPPOERTED)
    }

    private fun sendUnsupportedOperator(requestOpCode: Byte) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_UNSUPPORTED_OPERATOR)
    }

    private fun sendInvalidOperand(requestOpCode: Byte) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_INVALID_OPERAND)
    }

    private fun sendUnsupportedOperand(requestOpCode: Byte) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_OPERAND_UNSUPPORTED)
    }

    private fun sendServerBusy(requestOpCode: Byte) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_SERVER_BUSY)
    }

    private fun sendNoRecordsFound(requestOpCode: Byte) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_NO_RECORDS)
    }

    private fun sendNumberStoredRecords(numberOfRecords: Int) {
        val response = listOf(
            byteArrayOf(OP_CODE_RESPONSE_NUMBER_STORED_RECORDS, OP_NULL),
            numberOfRecords.asLittleEndianUint32Array()
        ).merge()
        // TODO: Ask Martijn about Blessed BluetoothPeripheralManager notifyCharacteristicChanged and indicate
        Timber.i("sendNumberStoredRecords response bytes: ${response.asFormattedHexString()}")
        service.sendBytesAndNotify(response, racpCharacteristic)
    }

    private fun sendNumberCombinedStoredRecords(numberOfRecords: Int) {
        val response = listOf(
            byteArrayOf(OP_CODE_RESPONSE_COMBINED_REPORT, OP_NULL),
            numberOfRecords.asLittleEndianUint32Array()
        ).merge()
        Timber.i("sendNumberCombinedStoredRecords response bytes: ${response.asFormattedHexString()}")
        sendBytesAndNotify(response)
    }

    private fun sendResponseCodeBytes(requestOpCode: Byte, responseCodeValue: Byte) {
        val response = byteArrayOf(OP_CODE_RESPONSE_CODE, 0X0.toByte(), requestOpCode, responseCodeValue)
        sendBytesAndNotify(response)
    }

    private fun sendBytesAndNotify(bytes: ByteArray) {
        service.sendBytesAndNotify(bytes, racpCharacteristic)
    }

    companion object {
        /*
         * RACP Operator Code Values
         */
        private const val OP_CODE_REPORT_STORED_RECORDS = 0x01.toByte()
        private const val OP_CODE_DELETE_STORED_RECORDS = 0x02.toByte()
        private const val OP_CODE_ABORT = 0x03.toByte()
        private const val OP_CODE_NUMBER_STORED_RECORDS = 0x04.toByte()
        private const val OP_CODE_COMBINED_REPORT = 0x07.toByte()

        private const val OP_CODE_RESPONSE_NUMBER_STORED_RECORDS = 0x05.toByte()
        private const val OP_CODE_RESPONSE_CODE = 0x06.toByte()
        private const val OP_CODE_RESPONSE_COMBINED_REPORT = 0x08.toByte()

        private const val OP_FILTER_TYPE_VALUE_REC_NUM = 0x01.toByte()
        private const val OP_FILTER_TYPE_VALUE_TIME = 0x02.toByte()

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

        /*
         * Response Code values associated with Op Code 0x06
         */
        private const val RESPONSE_CODE_SUCCESS = 0x01.toByte()
        private const val RESPONSE_CODE_OP_CODE_UNSUPPOERTED = 0x02.toByte()
        private const val RESPONSE_CODE_INVALID_OPERATOR = 0x03.toByte()
        private const val RESPONSE_CODE_UNSUPPORTED_OPERATOR = 0x04.toByte()
        private const val RESPONSE_CODE_INVALID_OPERAND = 0x05.toByte()
        private const val RESPONSE_CODE_NO_RECORDS = 0x06.toByte()
        private const val RESPONSE_CODE_ABORT_UNSUCCESSFUL = 0x07.toByte()
        private const val RESPONSE_CODE_PROCEDURE_NOT_COMPLETED = 0x08.toByte()
        private const val RESPONSE_CODE_OPERAND_UNSUPPORTED = 0x09.toByte()
        private const val RESPONSE_CODE_SERVER_BUSY = 0x0A.toByte()
    }

    private fun ByteArray.racpOpCode(): Byte = this[0]
    private fun ByteArray.racpOperator(): Byte = this[1]
}