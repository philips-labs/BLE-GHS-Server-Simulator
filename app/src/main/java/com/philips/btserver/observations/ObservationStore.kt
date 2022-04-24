package com.philips.btserver.observations

import android.content.res.Resources
import android.util.Range

interface ObservationStoreListener {
    fun observationStoreChanged()
}

object ObservationStore {
    private val observations = mutableMapOf<Int, Observation>()
    private var lastRecordNumber = 0
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
        observations.put(lastRecordNumber++, observation)
        broadcastChange()
    }

    fun remove(recordNumber: Int) {
        observations.remove(recordNumber)
        broadcastChange()
    }

    fun removeObservation(observation: Observation) = remove(observations.indexOfValue(observation))

    fun recordIdFor(observation: Observation): Int = observations.indexOfValue(observation)

    fun numberOfObservationsEqualOrGreaterThanRecordNumber(recordNumber: Int): Int = observations.count { it.key >= recordNumber }

    fun observationsEqualOrGreaterThanRecordNumber(recordNumber: Int): List<Observation> = observations.filter { it.key >= recordNumber }.map { it.value }

    fun observationRecordNumbersInRange(range: Range<Int>): List<Observation> = observations.filter { range.contains(it.key) }.map { it.value }

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
