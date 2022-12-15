package com.philips.btserver.observations

import android.content.res.Resources
import android.util.Range
import com.philips.btserver.userdataservice.UserDataManager

interface ObservationStoreListener {
    fun observationStoreChanged() {}
    fun observationStoreUserChanged() {}
}

object ObservationStore {
    var isTemporaryStore = false

    private val userObservations = mutableMapOf<Int, MutableMap<Int, Observation>>()

    private val observations: MutableMap<Int, Observation>
        get() = userObservations.getOrPut(currentUserIndex) { mutableMapOf() }

    private var currentUserIndex
        get() = UserDataManager.currentUserIndex
        set(value) = UserDataManager.getInstance().setCurrentUser(value)

    private val userLastRecordNumber = mutableMapOf<Int, Int>()
    private var lastRecordNumber: Int
        get() = userLastRecordNumber.getOrPut(UserDataManager.currentUserIndex) { 0 }
        set(value) {
            userLastRecordNumber[UserDataManager.currentUserIndex] = value
        }

    private val listeners = mutableListOf<ObservationStoreListener>()
    val storedObservations: List<Observation> get() = observations.values.toList()

    val usersWithTemporaryStoredObservations get() = userObservations.filter { it.value.size > 0 }.keys.toList()

    fun addListener(listener: ObservationStoreListener) = listeners.add(listener)
    fun removeListener(listener: ObservationStoreListener) = listeners.remove(listener)

    fun observationsForUser(userIndex: Int): List<Observation> {
        return userObservations.get(userIndex)?.values?.toList() ?: emptyList()
    }

    fun forEachUserTempObservation(userIndex: Int, block: (obs: Observation) -> Unit) {
        synchronized(userObservations) {
            observationsForUser(userIndex).forEach { block(it) }
        }
    }

    fun clear() {
        synchronized(userObservations) {
            observations.clear()
            userObservations.clear()
        }
        userLastRecordNumber.clear()
        broadcastUsersChanged()
        broadcastChange()
    }

    fun clearUserData(userIndex: Int) {
        clearObservationsForUser(userIndex)
        userLastRecordNumber.remove(userIndex)
        broadcastUsersChanged()
        broadcastChange()
    }

    fun clearObservationsForUser(userIndex: Int) {
        synchronized(userObservations) { userObservations.remove(userIndex) }
    }

    val numberOfStoredObservations get() = observations.size

    fun addObservation(observation: Observation) {
        synchronized(userObservations) {
            observations.put(lastRecordNumber, observation)
        }
        lastRecordNumber += 1
        broadcastChange()
        if (observations.size == 1) broadcastUsersChanged()
    }

    fun remove(recordNumber: Int) {
        synchronized(userObservations) {
            observations.remove(recordNumber)
        }
        checkUserObservationsEmpty()
        broadcastChange()
    }

    fun removeAll(recordNumbers: List<Int>) {
        synchronized(userObservations) {
            recordNumbers.forEach { observations.remove(it) }
        }
        checkUserObservationsEmpty()
        broadcastChange()
    }

    fun removeObservation(observation: Observation) {
        synchronized(userObservations) { remove(observations.indexOfValue(observation)) }
        checkUserObservationsEmpty()
    }

    fun recordIdFor(observation: Observation): Int = observations.indexOfValue(observation)

    fun numberOfObservationsGreaterThanOrEqualRecordNumber(recordNumber: Int, userIndex: Int = currentUserIndex): Int {
        return synchronized(userObservations) {
            val savedUserIndex = currentUserIndex
            currentUserIndex = userIndex
            val result = observations.count { it.key >= recordNumber }
            currentUserIndex = savedUserIndex
            result
        }

    }

    fun observationsGreaterThanOrEqualRecordNumber(recordNumber: Int, userIndex: Int = currentUserIndex): List<Observation>{
        return synchronized(userObservations) {
            val savedUserIndex = currentUserIndex
            currentUserIndex = userIndex
            val result = observations.filter { it.key >= recordNumber }.map { it.value }
            currentUserIndex = savedUserIndex
            result
        }
    }

    fun removeObservationsGreaterThanOrEqualRecordNumber(recordNumber: Int, userIndex: Int = currentUserIndex): Int {
        return synchronized(userObservations) {
            val savedUserIndex = currentUserIndex
            currentUserIndex = userIndex
            val obsToDelete = observationRecordNumbersInRange(Range(0, recordNumber))
            removeAll(obsToDelete)
            currentUserIndex = savedUserIndex
            obsToDelete.size
        }
    }

    fun observationRecordNumbersInRange(range: Range<Int>): List<Int> =
        observations.filter { range.contains(it.key) }.map { it.key }

    fun observationsInRange(range: Range<Int>): List<Observation> =
        observations.filter { range.contains(it.key) }.map { it.value }

    private fun checkUserObservationsEmpty() {
        if (userObservations[currentUserIndex]?.isEmpty() ?: true) {
            broadcastUsersChanged()
        }
    }

    private fun broadcastChange() = listeners.forEach { it.observationStoreChanged() }
    private fun broadcastUsersChanged() = listeners.forEach { it.observationStoreUserChanged() }

}

fun MutableMap<Int, Observation>.indexOfValue(observation: Observation): Int {
    var index: Int? = null
    forEach { idx, obs ->
        if (obs == observation) {
            index = idx
            return@forEach
        }
    }
    return index ?: throw Resources.NotFoundException()
}
