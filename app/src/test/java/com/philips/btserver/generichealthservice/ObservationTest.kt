/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

//import com.philips.btserver.extensions.asGHSByteArray
import com.philips.btserver.extensions.merge
import com.philips.btserver.observations.*
import com.philips.btserver.util.TimeSource
import com.welie.blessed.BluetoothBytesParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteOrder
import java.util.*

class ObservationTest {

    private val observationType = ObservationType.MDC_ECG_HEART_RATE
    private val randomValue = ObservationType.MDC_ECG_HEART_RATE.randomNumericValue()
    private val randomSampleArray = ObservationType.MDC_PPG_TIME_PD_PP.randomSampleArray()
    private val now = Date()

    @Test
    fun `When a SimpleNumericObservation is instantiated, then all properties are initialized correctly`() {
        val obs = create_simple_numeric_observation()
        assertEquals(100.toShort(), obs.id)
        assertEquals(observationType, obs.type)
        assertEquals(randomValue, obs.value)
        assertEquals(observationType.numericPrecision(), obs.valuePrecision)
        assertEquals(UnitCode.MDC_DIM_BEAT_PER_MIN, obs.unitCode)
        assertEquals(now, obs.timestamp)
    }

    @Test
    fun `When a SampleArrayObservation is instantiated, then all properties are initialized correctly`() {
        val obs = create_sample_array_observation()
        assertEquals(100.toShort(), obs.id)
        assertEquals(observationType, obs.type)
        assertArrayEquals(randomSampleArray, obs.value)
        assertEquals(UnitCode.MDC_DIM_BEAT_PER_MIN, obs.unitCode)
        assertEquals(now, obs.timestamp)
    }

    /*
     * Tests for full (no serialization options) SimpleNumericObservation serialization
     */

    @Test
    fun `When a SimpleNumericObservation is instantiated, then the timestamp byte array representation is correct`() {
        val obs = create_simple_numeric_observation()
        TimeSource.setTimeSourceWithETSBytes(timestamp_byte_array_for(obs.timestamp))
        assertArrayEquals(timestamp_byte_array_for(obs.timestamp), TimeSource.asGHSBytes())
    }

    @Test
    fun `When a SimpleNumericObservation is instantiated, then the value byte array representation is correct`() {
        val obs = create_simple_numeric_observation()
        assertArrayEquals(simple_numeric_value_byte_array_for(obs.value, obs.valuePrecision), obs.valueByteArray)
    }

    @Test
    fun `When a SimpleNumericObservation is instantiated, then the fully merged byte array representation is correct`() {
        val obs = create_simple_numeric_observation()
        assertArrayEquals(this.simple_numeric_observation_serialize_byte_array(obs), obs.ghsByteArray)
    }

    /*
     * Tests for full (no serialization options) SampleArrayObservation serialization
     */

    @Test
    fun `When a SampleArrayObservation is instantiated, then the value samples array byte array representation is correct`() {
        val obs = create_sample_array_observation()
        assertArrayEquals(sample_array_value_byte_array_for(obs.value), obs.valueByteArray)
    }

    /*
     * Tests for SimpleNumericObservation serialization with experimental options
     */

    /*
     * Private test support and utility methods
     */

    private fun create_simple_numeric_observation(): SimpleNumericObservation {
        return SimpleNumericObservation(
                id = 100,
                type = observationType,
                value = randomValue,
                valuePrecision = observationType.numericPrecision(),
                unitCode = UnitCode.MDC_DIM_BEAT_PER_MIN,
                timestamp = now)
    }

    private fun create_sample_array_observation(): SampleArrayObservation {
        return SampleArrayObservation(
                id = 100,
                type = observationType,
                value = randomSampleArray,
                unitCode = UnitCode.MDC_DIM_BEAT_PER_MIN,
                timestamp = now)
    }

    private fun encodeTLV(type: Int, length: Int, value: Number, precision: Int = 2): ByteArray {
        return BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN).encodeTLV(type, length, value, precision)
    }

    private fun handle_byte_array_for(id: Short): ByteArray {
        return encodeTLV(Observation.handleCode, Observation.handleLength, id)
    }

    private fun type_byte_array_for(type: ObservationType): ByteArray {
        return encodeTLV(Observation.typeCode, Observation.typeLength, type.value)
    }

    private fun unit_byte_array_for(unitCode: UnitCode): ByteArray {
        return encodeTLV(Observation.unitCodeId, Observation.unitLength, unitCode.value)
    }

    private fun timestamp_byte_array_for(timestamp: Date): ByteArray {
        return encodeTLV(Observation.timestampCode, Observation.timestampLength, timestamp.time)
    }

    private fun simple_numeric_value_byte_array_for(value: Float, precision: Int): ByteArray {
        return encodeTLV(ObservationValueType.MDC_ATTR_NU_VAL_OBS.value, ObservationValueType.valueByteLength, value, precision)
    }

    private fun simple_numeric_observation_serialize_byte_array(obs: SimpleNumericObservation): ByteArray {
        return listOf(
                type_byte_array_for(obs.type),
                handle_byte_array_for(obs.id),
                this.simple_numeric_value_byte_array_for(obs.value, obs.valuePrecision),
                unit_byte_array_for(obs.unitCode),
                this.timestamp_byte_array_for(obs.timestamp)).merge()
    }

    private fun sample_array_value_byte_array_for(sampleArray: ByteArray): ByteArray {
        return BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN).encodeTLV(sampleArray)
    }
}

fun BluetoothBytesParser.encodeTLV(type: Int, length: Int, value: Number, precision: Int = 2): ByteArray {
    setIntValue(type, BluetoothBytesParser.FORMAT_UINT32)
    setIntValue(length, BluetoothBytesParser.FORMAT_UINT16)
    when (value) {
        is Int -> setIntValue(value, BluetoothBytesParser.FORMAT_UINT32)
        is Short -> setIntValue(value.toInt(), BluetoothBytesParser.FORMAT_UINT16)
        is Long -> setLong(value, BluetoothBytesParser.FORMAT_UINT64)
        is Float -> setFloatValue(value, precision)
        else -> error("Unsupported value type sent to encodeTLV()")
    }
    return this.value
}

fun BluetoothBytesParser.encodeTLV(value: ByteArray): ByteArray {
    setIntValue(ObservationValueType.MDC_ATTR_SA_VAL_OBS.value, BluetoothBytesParser.FORMAT_UINT32)
    setIntValue(value.size, BluetoothBytesParser.FORMAT_UINT16)
    return this.value + value
}
