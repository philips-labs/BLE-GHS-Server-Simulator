package com.philips.btserver.userdataservice

import com.philips.btserver.extensions.asBLEDataSegments
import com.philips.btserver.extensions.asByteArray
import com.philips.btserver.extensions.merge

class RegisteredUsersSendHandler(val service: UserDataService) {
    private var currentSegmentNumber: Int = 0
    private val segmentSize get() = service.minimalMTU - 5
    private val registeredUserCharacteristic = service.registeredUserCharacteristic
    private val usersList get() = UserDataManager.getInstance().usersList.filter {it != 0xFF}

    var isSendingUsers = false

    fun sendAllUsers() {
        isSendingUsers = true
        usersList.forEach { sendUserIndex(it) }
        isSendingUsers = false
    }

    fun sendUserIndex(userIndex: Int) {
        val resultPair = segmentsAndNextSegmentNumberForUserIndex(userIndex)
        resultPair.first.forEach { sendSegment(it) }
        currentSegmentNumber = resultPair.second
    }

    private fun segmentsAndNextSegmentNumberForUserIndex(userIndex: Int): Pair<List<ByteArray>, Int> {
        return bytesForUserIndex(userIndex).asBLEDataSegments(segmentSize, currentSegmentNumber)
    }

    private fun bytesForUserIndex(userIndex: Int): ByteArray {
        return UserDataManager.getInstance().userDataForIndex(userIndex)?.let {
            listOf(
                byteArrayOf(FLAGS_USER_NAME_PRESENT, userIndex.toByte()),
                (it.firstName + " " + it.lastName).asByteArray()
            ).merge()
        } ?: byteArrayOf(FLAGS_USER_NAME_NOT_PRESENT, userIndex.toByte())
    }

    private fun sendSegment(bytes: ByteArray) {
        service.sendBytesAndNotify(bytes, registeredUserCharacteristic)
    }

    companion object {
        val FLAGS_USER_NAME_PRESENT = 0x1.toByte()
        val FLAGS_USER_NAME_NOT_PRESENT = 0x0.toByte()
    }
}