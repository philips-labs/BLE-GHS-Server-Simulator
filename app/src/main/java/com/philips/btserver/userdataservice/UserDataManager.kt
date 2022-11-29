package com.philips.btserver.userdataservice

import android.content.Context
import com.philips.btserver.BluetoothServer
import com.philips.btserver.observations.Observation
import com.philips.btserver.observations.ObservationStore

class UserDataManager {

    private val users = mutableListOf<Int>()
    private val consentCodes = mutableListOf<Int>()

    /* This is the index for the server... each connection also maintains a current user for observation access */
    var currentUserIndex = UNDEFINED_USER_INDEX

    val usersList: List<Int> get() = users + listOf(0xFF)

    fun hasUserIndex(index: Int): Boolean { return users.contains(index) || (index == UNDEFINED_USER_INDEX) }

    fun usersInfo(): String {
        var result = ""
        users.forEachIndexed {index, user -> result += "User $user\tConsent: ${consentCodes[index]} obs: ${ObservationStore.observationsForUser(user).count()}\n"}
        return result
    }

    fun createUserWithConsentCode(consentCode: Int): Int {
        val userIndex = (users.maxOrNull() ?: 0) + 1
        users.add(userIndex)
        consentCodes.add(consentCode)
        return userIndex
    }

    fun setCurrentUser(userIndex: Int) {
        val listIndex = users.indexOf(userIndex)
        if (listIndex >= 0) {
            setUserConsent(userIndex, consentCodes[listIndex])
        }
    }

    fun setUserConsent(userIndex: Int, consentCode: Int): Boolean {
        return if(checkUserConsent(userIndex, consentCode)) {
            // Suboptimal as redoing users.indexOf(userIndex), but avoiding DRY in code
            setCurrentUser(users.indexOf(userIndex))
            true
        } else false
    }

    fun checkUserConsent(userIndex: Int, consentCode: Int): Boolean {
        val listIndex = users.indexOf(userIndex)
        return if (listIndex < 0) false else consentCode == consentCodes[listIndex]
    }

    fun deleteUser(userIndex: Int): Boolean {
        return if(userIndex == UNDEFINED_USER_INDEX) deleteAllUsers() else {
            val listIndex = users.indexOf(userIndex)
            if (listIndex < 0) {
                false
            } else {
                setCurrentUser(UNDEFINED_USER_INDEX)
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