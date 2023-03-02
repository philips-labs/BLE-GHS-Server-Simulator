package com.philips.btserver.userdataservice

import com.philips.btserver.extensions.merge
import com.philips.btserver.generichealthservice.GhsRacpHandler
import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.GattStatus

enum class UserDataControlPointOpCode(val value: Byte) {
    RegisterNewUser(1),
    UserConsent(2),
    DeleteUserData(3),
    ListAllUsers(4),
    DeleteUser(5),
    Unknown(0xFF.toByte());

    override fun toString(): String {
        return when (value) {
            RegisterNewUser.value -> "Unknown"
            UserConsent.value -> "User Consent"
            DeleteUserData.value -> "Delete User Data"
            ListAllUsers.value -> "List All Users"
            DeleteUser.value -> "Delete User"
            else -> "Undefined"
        }
    }

    companion object {
        fun value(value: Byte): UserDataControlPointOpCode {
            return when (value) {
                RegisterNewUser.value -> RegisterNewUser
                UserConsent.value -> UserConsent
                DeleteUserData.value -> DeleteUserData
                ListAllUsers.value -> ListAllUsers
                DeleteUser.value -> DeleteUser
                else -> Unknown
            }
        }

    }
}

class UserDataControlPointHandler(val service: UserDataService) {

    private val controlPointCharacteristic get() = service.controlPointCharacteristic

    fun writeGattStatusFor(value: ByteArray): GattStatus {
        return if (isWriteValid(value)) GattStatus.SUCCESS else GattStatus.ILLEGAL_PARAMETER
    }

    private fun isWriteValid(bytes: ByteArray): Boolean {
        return bytes.isNotEmpty() && isValidOpCode(bytes.first())
    }

    private fun isValidOpCode(opCode: Byte): Boolean {
        return byteArrayOf(
            UserDataControlPointOpCode.RegisterNewUser.value,
            UserDataControlPointOpCode.UserConsent.value,
            UserDataControlPointOpCode.DeleteUserData.value,
            UserDataControlPointOpCode.DeleteUser.value,
            0x20).indexOf(opCode) > -1
    }

    private fun getOpCode(bytes: ByteArray): UserDataControlPointOpCode {
        return if (bytes.isEmpty())
            UserDataControlPointOpCode.Unknown
        else UserDataControlPointOpCode.value(bytes.first())
    }

    fun handleReceivedBytes(bluetoothCentral: BluetoothCentral, bytes: ByteArray) {
        when(getOpCode(bytes)) {
            UserDataControlPointOpCode.RegisterNewUser -> registerNewUser(bytes)
            UserDataControlPointOpCode.UserConsent -> requestUserConsent(bluetoothCentral, bytes)
            UserDataControlPointOpCode.DeleteUserData -> deleteUserData(bluetoothCentral, bytes)
            UserDataControlPointOpCode.ListAllUsers -> listAllUsers()
            UserDataControlPointOpCode.DeleteUser -> deleteUser(bytes)
            else -> return
        }
    }

    private val registeredUsersSendHandler get() = service.registeredUsersSendHandler

    private fun listAllUsers() {
        if (registeredUsersSendHandler.isSendingUsers) {
            sendServerBusy(UserDataControlPointOpCode.ListAllUsers)
        } else {
            registeredUsersSendHandler.sendAllUsers()
            sendResponseCodeBytes(UserDataControlPointOpCode.ListAllUsers,
                OP_CODE_RESPONSE_VALUE_SUCCESS,
                byteArrayOf())
        }
    }

    private fun sendServerBusy(requestOpCode: UserDataControlPointOpCode) {
        sendResponseCodeBytes(requestOpCode, RESPONSE_CODE_SERVER_BUSY)
    }

    private fun registerNewUser(bytes: ByteArray) {
        val parser = BluetoothBytesParser(bytes)
        val opCode = parser.getUInt8().toByte()
        val consentCode = parser.getUInt16()
        if (consentCode > MAX_CONSENT_CODE) {
            sendInvalidParameterResponse(UserDataControlPointOpCode.value(opCode))
        }
        val userIndex = UserDataManager.getInstance().createUserWithConsentCode(consentCode)
        sendResponseCodeBytes(UserDataControlPointOpCode.RegisterNewUser,
            OP_CODE_RESPONSE_VALUE_SUCCESS,
            byteArrayOf(userIndex.toByte()))
    }


    private fun deleteUser(bytes: ByteArray) {
        val parser = BluetoothBytesParser(bytes)
        val opCode = parser.getUInt8().toByte()
        val userIndex = parser.getUInt8()
        val success = UserDataManager.getInstance().deleteUser(userIndex)
        if (success) {
            sendResponseCodeBytes(UserDataControlPointOpCode.DeleteUser, OP_CODE_RESPONSE_VALUE_SUCCESS, byteArrayOf(userIndex.toByte()))
        } else {
            //TODO Could also be Operation Failed, User Not Authorized..
            sendInvalidParameterResponse(UserDataControlPointOpCode.value(opCode))
        }
    }

    private fun requestUserConsent(bluetoothCentral: BluetoothCentral, bytes: ByteArray) {
        val parser = BluetoothBytesParser(bytes)
        val opCode = parser.getUInt8().toByte()
        val userIndex = parser.getUInt8().toByte()
        val consentCode = parser.getUInt16()
        val consentSuccess = UserDataManager.getInstance().checkUserConsent(userIndex.toInt(), consentCode)
        if (consentSuccess) {
            service.setUserIndexForCentral(bluetoothCentral, userIndex.toInt())
            sendResponseCodeBytes(UserDataControlPointOpCode.UserConsent, OP_CODE_RESPONSE_VALUE_SUCCESS)
        } else {
            // TODO Could also be Operation Failed, User Not Authorized..
            sendInvalidParameterResponse(UserDataControlPointOpCode.value(opCode))
        }
    }

    private fun deleteUserData(bluetoothCentral: BluetoothCentral, bytes: ByteArray) {
        val parser = BluetoothBytesParser(bytes)
        val opCode = parser.getUInt8().toByte()
        val userIndex = service.getCurrentUserIndexForCentral(bluetoothCentral)//parser.getUInt8().toByte()
        val success = UserDataManager.getInstance().deleteUser(userIndex.toInt())
        service.setUserIndexForCentral(bluetoothCentral,UserDataManager.UNDEFINED_USER_INDEX)
        sendResponseCodeBytes(UserDataControlPointOpCode.DeleteUserData, OP_CODE_RESPONSE_VALUE_SUCCESS)
    }

    private fun unsupportedOperation(requestOpCode: UserDataControlPointOpCode) {
        sendResponseCodeBytes(requestOpCode, OP_CODE_RESPONSE_VALUE_OPCODE_UNSUPPORTED)
    }

    private fun sendInvalidParameterResponse(requestOpCode: UserDataControlPointOpCode) {
        sendResponseCodeBytes(requestOpCode, OP_CODE_RESPONSE_VALUE_INVALID_PARAMETER)
    }

    private fun sendSuccessResponse(requestOpCode: UserDataControlPointOpCode) {
        sendResponseCodeBytes(requestOpCode, OP_CODE_RESPONSE_VALUE_SUCCESS)
    }

    private fun sendResponseCodeBytes(requestOpCode: UserDataControlPointOpCode, responseCodeValue: Byte, responseParameter: ByteArray = byteArrayOf()) {
        val response = listOf(byteArrayOf(OP_CODE_RESPONSE_CODE, requestOpCode.value, responseCodeValue), responseParameter).merge()
        sendBytesAndNotify(response)
    }

    private fun sendBytesAndNotify(bytes: ByteArray) {
        service.sendBytesAndNotify(bytes, controlPointCharacteristic)
    }

    companion object {
        /*
         * GHS Control Point commands and status values
         */
        const val REGISTER_NEW_USER = 0x01.toByte()
        const val USER_CONSENT = 0x02.toByte()
        const val DELETE_USER_DATA = 0x03.toByte()
        const val LIST_ALL_USERS = 0x04.toByte()
        const val DELETE_USER = 0x05.toByte()

        const val MAX_CONSENT_CODE = 0x270F
        /*
         * Operator Response Code Values
         */
        private const val OP_CODE_RESPONSE_CODE = 0x20.toByte()

        private const val OP_CODE_RESPONSE_VALUE_SUCCESS = 0x01.toByte()
        private const val OP_CODE_RESPONSE_VALUE_OPCODE_UNSUPPORTED = 0x02.toByte()
        private const val OP_CODE_RESPONSE_VALUE_INVALID_PARAMETER = 0x03.toByte()
        private const val OP_CODE_RESPONSE_VALUE_OPERATION_FAILED = 0x04.toByte()
        private const val OP_CODE_RESPONSE_VALUE_NOT_AUTHORIZED = 0x05.toByte()

        private const val RESPONSE_CODE_SERVER_BUSY = 0x0A.toByte()

    }

}