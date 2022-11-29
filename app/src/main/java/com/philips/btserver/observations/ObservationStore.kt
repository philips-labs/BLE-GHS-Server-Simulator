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
        get() {
            if (!userObservations.containsKey(currentUserIndex)) {
                userObservations[currentUserIndex] = mutableMapOf()
            }
            return userObservations[currentUserIndex]!!
        }

    private val currentUserIndex = UserDataManager.currentUserIndex
    private val userLastRecordNumber = mutableMapOf<Int, Int>()
    private var lastRecordNumber: Int
        get() {
            return userLastRecordNumber.getOrPut(UserDataManager.currentUserIndex) { 0 }
        }
        set(value) {
            userLastRecordNumber[UserDataManager.currentUserIndex] = value
        }
    private val listeners = mutableListOf<ObservationStoreListener>()
    val storedObservations = observations.values.toList()

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

    fun numberOfObservationsGreaterThanOrEqualRecordNumber(recordNumber: Int): Int =
        observations.count { it.key >= recordNumber }

    fun observationsGreaterThanOrEqualRecordNumber(recordNumber: Int): List<Observation> =
        observations.filter { it.key >= recordNumber }.map { it.value }

    fun removeObservationsGreaterThanOrEqualRecordNumber(recordNumber: Int): Int {
        var numberRemoved = 0
        synchronized(userObservations) {
            val obsToDelete = observationRecordNumbersInRange(Range(0, recordNumber))
            numberRemoved = obsToDelete.size
            removeAll(obsToDelete)
        }
        return numberRemoved
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
