package com.philips.btserver.generichealthservice

import com.philips.btserver.extensions.asLittleEndianArray
import com.philips.btserver.extensions.merge
import com.philips.btserver.extensions.uInt16At
import com.welie.blessed.BluetoothBytesParser
import java.lang.Integer.max
import java.lang.Integer.min
import java.nio.ByteOrder

class GhsRacpHandler(val service: GenericHealthSensorService) {

    private val racpCharacteristic get() = service.racpCharacteristic
    private val storedRecords get() = ObservationEmitter.storedObservations
    private val numberStoredRecords get() = storedRecords.size

    fun reset() {}

    fun handleReceivedBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val opCode = bytes.racpOpCode()
        when(opCode) {
            OP_CODE_COMBINED_REPORT -> reportCombinedStoredRecords(bytes)
            OP_CODE_NUMBER_STORED_RECORDS -> reportNumberStoredRecords(bytes)
            else -> service.sendBytesAndNotify(responseCodeBytes(
                OP_CODE_RESPONSE_CODE,
                opCode,
                RESPONSE_CODE_OP_CODE_UNSUPPOERTED), racpCharacteristic)
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
                sendNumberCombinedStoredRecords(it.size)
            }
        }
    }

    private fun reportNumberStoredRecords(bytes: ByteArray) {
        val numberOfRecords = queryNumberStoredRecords(bytes)
        // If number of records is null then something happened and a response has already been sent
        numberOfRecords?.let { sendNumberStoredRecords(it) }
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
                else -> {
                    sendInvalidOperator(bytes.racpOperator())
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
                else -> {
                    sendInvalidOperator(bytes.racpOperator())
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
            sendInvalidFilterType(bytes.racpOpCode())
            null
        } else {
            val minValue = getQueryRecordNumber(bytes)
            max(0, numberStoredRecords - minValue + 1)
        }

    }

    // Right now query is on record number which is sequential from 0 to number of records
    private fun queryRecordsGreaterOrEqual(bytes: ByteArray): List<Observation>? {
        return if (bytes.size < 7) {
            sendInvalidOperand(bytes.racpOpCode())
            null
        } else if (!isValidFilterType(bytes)) {
            sendInvalidFilterType(bytes.racpOpCode())
            null
        } else {
            val minValue = getQueryRecordNumber(bytes)
            storedRecords.subList(minValue, storedRecords.size)
        }
    }

    private fun getQueryRecordNumber(bytes: ByteArray): Int {
        val parser = BluetoothBytesParser(bytes.copyOfRange(3,7))
        return parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32, ByteOrder.LITTLE_ENDIAN)
    }

    private fun isValidFilterType(bytes: ByteArray): Boolean {
        return (bytes.size > 3) && (bytes[2] == OP_FILTER_TYPE_VALUE_REC_NUM)
    }

    private fun sendInvalidOperator(requestOpCode: Byte) {
        service.sendBytesAndNotify(responseCodeBytes(
            OP_CODE_RESPONSE_CODE,
            requestOpCode,
            RESPONSE_CODE_INVALID_OPERATOR), racpCharacteristic)
    }

    private fun sendInvalidOperand(requestOpCode: Byte) {
        service.sendBytesAndNotify(responseCodeBytes(
            OP_CODE_RESPONSE_CODE,
            requestOpCode,
            RESPONSE_CODE_INVALID_OPERAND), racpCharacteristic)
    }

    private fun sendInvalidFilterType(requestOpCode: Byte) {
        service.sendBytesAndNotify(
            responseCodeBytes(OP_CODE_RESPONSE_CODE,
                requestOpCode,
                RESPONSE_CODE_OPERAND_UNSUPPORTED), racpCharacteristic)
    }

    private fun sendNoRecordsFound(requestOpCode: Byte) {
        service.sendBytesAndNotify(
            responseCodeBytes(OP_CODE_RESPONSE_CODE,
                requestOpCode,
                RESPONSE_CODE_NO_RECORDS), racpCharacteristic)
    }

    private fun sendNumberStoredRecords(numberOfRecords: Int) {
        val response = listOf(
            byteArrayOf(OP_CODE_RESPONSE_NUMBER_STORED_RECORDS, OP_NULL),
            numberOfRecords.asLittleEndianArray()
        ).merge()
        // TODO: Ask Martijn about Blessed BluetoothPeripheralManager notifyCharacteristicChanged and indicate
        service.sendBytesAndNotify(response, racpCharacteristic)
    }

    private fun sendNumberCombinedStoredRecords(numberOfRecords: Int) {
        val response = listOf(
            byteArrayOf(OP_CODE_RESPONSE_COMBINED_REPORT, OP_NULL),
            numberOfRecords.asLittleEndianArray()
        ).merge()
        // TODO: Ask Martijn about Blessed BluetoothPeripheralManager notifyCharacteristicChanged and indicate
        service.sendBytesAndNotify(response, racpCharacteristic)
    }

    private fun responseCodeBytes(responseCode: Byte, requestOpCode: Byte, responseCodeValue: Byte): ByteArray {
        return byteArrayOf(responseCode, 0X0.toByte(), requestOpCode, responseCodeValue)
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
        private const val RESPONSE_CODE_OPERATOR_UNSUPPORTED = 0x04.toByte()
        private const val RESPONSE_CODE_INVALID_OPERAND = 0x05.toByte()
        private const val RESPONSE_CODE_NO_RECORDS = 0x06.toByte()
        private const val RESPONSE_CODE_ABORT_UNSUCCESSFUL = 0x07.toByte()
        private const val RESPONSE_CODE_PROCEDURE_NOT_COMPLETED = 0x08.toByte()
        private const val RESPONSE_CODE_OPERAND_UNSUPPORTED = 0x09.toByte()
    }

    private fun ByteArray.racpOpCode(): Byte = this[0]
    private fun ByteArray.racpOperator(): Byte = this[1]

}