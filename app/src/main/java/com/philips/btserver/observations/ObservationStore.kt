package com.philips.btserver.observations

import android.content.res.Resources
import android.util.Range
import com.philips.btserver.userdataservice.UserDataManager

interface ObservationStoreListener {
    fun observationStoreChanged()
}

object ObservationStore {
    private val userObservations = mutableMapOf<Int, MutableMap<Int, Observation>>()
    private val observations: MutableMap<Int, Observation> get() {
        return userObservations.getOrPut(UserDataManager.currentUserIndex) { mutableMapOf() }
    }
    private val userLastRecordNumber = mutableMapOf<Int, Int>()
    private var lastRecordNumber: Int get() {
        return userLastRecordNumber.getOrPut(UserDataManager.currentUserIndex) { 0 }
    } set(value) {
        userLastRecordNumber[UserDataManager.currentUserIndex] = value
    }
    private val listeners = mutableListOf<ObservationStoreListener>()
    val storedObservations = observations.values

    fun addListener(listener: ObservationStoreListener) = listeners.add(listener)
    fun removeListener(listener: ObservationStoreListener) = listeners.remove(listener)

    fun clear() {
        observations.clear()
        lastRecordNumber = 0
        broadcastChange()
    }

    val numberOfStoredObservations get() = observations.size

    fun addObservation(observation: Observation){
        observations.put(lastRecordNumber, observation)
        lastRecordNumber += 1
        broadcastChange()
    }

    fun remove(recordNumber: Int) {
        observations.remove(recordNumber)
        broadcastChange()
    }

    fun removeAll(recordNumbers: List<Int>) {
        recordNumbers.forEach { observations.remove(it) }
        broadcastChange()
    }

    fun removeObservation(observation: Observation) = remove(observations.indexOfValue(observation))

    fun recordIdFor(observation: Observation): Int = observations.indexOfValue(observation)

    fun numberOfObservationsGreaterThanOrEqualRecordNumber(recordNumber: Int): Int = observations.count { it.key >= recordNumber }

    fun observationsGreaterThanOrEqualRecordNumber(recordNumber: Int): List<Observation> = observations.filter { it.key >= recordNumber }.map { it.value }

    fun removeObservationsGreaterThanOrEqualRecordNumber(recordNumber: Int): Int  {
        val obsToDelete = observationRecordNumbersInRange(Range(0, recordNumber))
        removeAll(obsToDelete)
        return obsToDelete.size
    }

    fun observationRecordNumbersInRange(range: Range<Int>): List<Int> = observations.filter { range.contains(it.key) }.map { it.key }

    fun observationsInRange(range: Range<Int>): List<Observation> = observations.filter { range.contains(it.key) }.map { it.value }

    private fun broadcastChange() = listeners.forEach { it.observationStoreChanged() }

}

fun MutableMap<Int, Observation>.indexOfValue(observation: Observation): Int {
    var index: Int? = null
    forEach { idx, obs ->
        if(obs == observation) {
            index = idx
            return@forEach
        }
    }
    return index ?: throw Resources.NotFoundException()
}
