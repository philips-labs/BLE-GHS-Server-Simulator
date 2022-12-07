package com.philips.btserver.userdataservice

import android.content.Context
import com.philips.btserver.BluetoothServer
import com.philips.btserver.observations.Observation
import com.philips.btserver.observations.ObservationStore

interface UserDataManagerListener {
    fun currentUserIndexChanged(userIndex: Int) {}
    fun createdUser(userIndex: Int) {}
    fun deletedUser(userIndex: Int) {}
    fun deletedAllUsers() {}
}

class UserDataManager {

    private val users = mutableListOf<Int>()
    private val consentCodes = mutableListOf<Int>()

    private val listeners = mutableSetOf<UserDataManagerListener>()

    /* This is the index for the server... each connection also maintains a current user for observation access */
    var currentUserIndex = UNDEFINED_USER_INDEX

    val usersList: List<Int> get() = users + listOf(0xFF)

    fun addListener(listener: UserDataManagerListener) { listeners.add(listener) }
    fun removeListener(listener: UserDataManagerListener) { listeners.remove(listener) }

    fun hasUserIndex(index: Int): Boolean { return users.contains(index) || (index == UNDEFINED_USER_INDEX) }

    fun usersInfo(): String {
        var result = ""
        users.forEachIndexed {index, user -> result += "User $user\tConsent: ${consentCodes[index]} obs: ${ObservationStore.observationsForUser(user).count()}\n"}
        result += "Unknown User (255) obs: ${ObservationStore.observationsForUser(255).count()}\n"
        return result
    }

    fun createUserWithConsentCode(consentCode: Int): Int {
        val userIndex = (users.maxOrNull() ?: 0) + 1
        users.add(userIndex)
        consentCodes.add(consentCode)
        listeners.forEach { it.createdUser(userIndex) }
        return userIndex
    }

    fun setCurrentUser(userIndex: Int) {
        if (hasUserIndex(userIndex)) {
            currentUserIndex = userIndex
            listeners.forEach { it.currentUserIndexChanged(userIndex) }
        }
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
                listeners.forEach { it.deletedUser(userIndex) }
                true
            }
        }
    }

    fun deleteAllUsers(): Boolean {
        setCurrentUser(UNDEFINED_USER_INDEX)
        users.clear()
        consentCodes.clear()
        ObservationStore.clear()
        listeners.forEach { it.deletedAllUsers() }
        return true
    }

    companion object {
        const val UNDEFINED_USER_INDEX = 0xFF

        private val instance: UserDataManager = UserDataManager()

        fun getInstance(): UserDataManager { return instance }

        val currentUserIndex: Int get() { return getInstance().currentUserIndex }

    }


}