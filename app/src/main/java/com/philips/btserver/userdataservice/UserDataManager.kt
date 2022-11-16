package com.philips.btserver.userdataservice

import android.content.Context
import com.philips.btserver.BluetoothServer
import com.philips.btserver.observations.Observation
import com.philips.btserver.observations.ObservationStore

class UserDataManager {

    private val users = mutableListOf(0x01, 0x02)
    private val consentCodes = mutableListOf(0x0, 0x0)
    var currentUserIndex = UNDEFINED_USER_INDEX

    fun hasUserIndex(index: Int): Boolean { return users.contains(index) || (index == UNDEFINED_USER_INDEX) }

    fun createUserWithConsentCode(consentCode: Int): Int {
        val userIndex = (users.maxOrNull() ?: 0) + 1
        users.add(userIndex)
        consentCodes.add(consentCode)
        return userIndex
    }

    fun setCurrentUser(userIndex: Int) {
        currentUserIndex = userIndex
    }

    fun setUserConsent(userIndex: Int, consentCode: Int): Boolean {
        val listIndex = users.indexOf(userIndex)
        return if (listIndex < 0) false else {
            if (consentCode == consentCodes[listIndex]) {
                setCurrentUser(listIndex)
                true
            } else false
        }
    }

    fun deleteUser(userIndex: Int): Boolean {
        return if(userIndex == UNDEFINED_USER_INDEX) deleteAllUsers() else {
            val listIndex = users.indexOf(userIndex)
            if (listIndex < 0) {
                false
            } else {
                users.removeAt(listIndex)
                consentCodes.removeAt(listIndex)
                ObservationStore.clearUserData(userIndex)
                true
            }
        }
    }

    fun deleteAllUsers(): Boolean {
        setCurrentUser(UNDEFINED_USER_INDEX)
        users.clear()
        consentCodes.clear()
        ObservationStore.clear()
        return true
    }

    companion object {
        const val UNDEFINED_USER_INDEX = 0xFF

        private val instance: UserDataManager = UserDataManager()

        fun getInstance(): UserDataManager { return instance }

        val currentUserIndex: Int get() { return getInstance().currentUserIndex }

    }


}