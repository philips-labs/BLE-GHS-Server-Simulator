/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import com.philips.btserver.extensions.findFirst
import com.philips.btserver.extensions.merge
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
    fun `When a SimpleNumericObservation is instantiated, then the handle byte array representation is correct`() {
        val obs = create_simple_numeric_observation()
        assertArrayEquals(handle_byte_array_for(obs.id), obs.handleByteArray)
    }

    @Test
    fun `When a SimpleNumericObservation is instantiated, then the type byte array representation is correct`() {
        val obs = create_simple_numeric_observation()
        assertArrayEquals(type_byte_array_for(obs.type), obs.typeByteArray)
    }

    @Test
    fun `When a SimpleNumericObservation is instantiated, then the unit code byte array representation is correct`() {
        val obs = create_simple_numeric_observation()
        assertArrayEquals(unit_byte_array_for(obs.unitCode), obs.unitByteArray)
    }

    @Test
    fun `When a SimpleNumericObservation is instantiated, then the timestamp byte array representation is correct`() {
        val obs = create_simple_numeric_observation()
        assertArrayEquals(timestamp_byte_array_for(obs.timestamp), obs.timestampByteArray)
    }

    @Test
    fun `When a SimpleNumericObservation is instantiated, then the value byte array representation is correct`() {
        val obs = create_simple_numeric_observation()
        assertArrayEquals(simple_numeric_value_byte_array_for(obs.value, obs.valuePrecision), obs.valueByteArray)
    }

    @Test
    fun `When a SimpleNumericObservation is instantiated, then the fully merged byte array representation is correct`() {
        val obs = create_simple_numeric_observation()
        assertArrayEquals(this.simple_numeric_observation_serialize_byte_array(obs), obs.serialize())
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

    @Test
    fun `When the experimental option experimental properties are set individually, then ensure the properties are actually set`() {
        // setting via accessors
        val obs = create_simple_numeric_observation()
        obs.omitFixedLengthTypes = true
        obs.omitHandleTLV = true
        obs.omitUnitCode = true
        obs.useShortTypeCodes = true

        assertTrue(obs.omitFixedLengthTypes)
        assertTrue(obs.omitHandleTLV)
        assertTrue(obs.omitUnitCode)
        assertTrue(obs.useShortTypeCodes)
    }

    @Test
    fun `When the experimental option experimental properties are set in a group BitSet, then ensure the properties are actually set`() {
        // Setting via bit set ("in bulk"... used by ObservationEmitter)
        val obs = create_simple_numeric_observation()
        val optionsBitSet = BitSet()

        optionsBitSet.set(Observation.ExperimentalFeature.OmitFixedLengthTypes.bit, true)
        optionsBitSet.set(Observation.ExperimentalFeature.OmitHandleTLV.bit, true)
        optionsBitSet.set(Observation.ExperimentalFeature.OmitUnitCode.bit, true)
        optionsBitSet.set(Observation.ExperimentalFeature.UseShortTypeCodes.bit, true)
        obs.experimentalOptions = optionsBitSet

        assertTrue(obs.omitFixedLengthTypes)
        assertTrue(obs.omitHandleTLV)
        assertTrue(obs.omitUnitCode)
        assertTrue(obs.useShortTypeCodes)
    }

    @Test
    // Note: validating based on length skipped, vs. contents of byte array
    fun `When the experimental option omit fixed lengths is set, then the length values are skipped in the byte array`() {
        val obs = create_simple_numeric_observation()

        obs.omitFixedLengthTypes = false
        var tlvBytes = obs.handleByteArray
        obs.omitFixedLengthTypes = true
        var tvBytes = obs.handleByteArray
        assertEquals(tlvBytes.size - 2, tvBytes.size)

        obs.omitFixedLengthTypes = false
        tlvBytes = obs.typeByteArray
        obs.omitFixedLengthTypes = true
        tvBytes = obs.typeByteArray
        assertEquals(tlvBytes.size - 2, tvBytes.size)

        obs.omitFixedLengthTypes = false
        tlvBytes = obs.unitByteArray
        obs.omitFixedLengthTypes = true
        tvBytes = obs.unitByteArray
        assertEquals(tlvBytes.size - 2, tvBytes.size)

        obs.omitFixedLengthTypes = false
        tlvBytes = obs.timestampByteArray
        obs.omitFixedLengthTypes = true
        tvBytes = obs.timestampByteArray
        assertEquals(tlvBytes.size - 2, tvBytes.size)

        obs.omitFixedLengthTypes = false
        tlvBytes = obs.valueByteArray
        obs.omitFixedLengthTypes = true
        tvBytes = obs.valueByteArray
        assertEquals(tlvBytes.size - 2, tvBytes.size)

        // Ensure all the lengths are gone (all fixed length)
        obs.omitFixedLengthTypes = false
        tlvBytes = obs.serialize()
        obs.omitFixedLengthTypes = true
        tvBytes = obs.serializeWithExperimentalOptions()
        assertEquals(tlvBytes.size - 10, tvBytes.size)
    }

    @Test
    fun `When the experimental option omit handle TLV is set, then the handle TLV is skipped in the byte array`() {
        val obs = create_simple_numeric_observation()

        obs.omitHandleTLV = true
        val tlvBytes = obs.serialize()
        val tvBytes = obs.serializeWithExperimentalOptions()
        assertEquals(tlvBytes.size - 8, tvBytes.size)
        // If handle was skipped, handle tlv type will not exist
        assertEquals(-1, tvBytes.findFirst(byteArrayOf(0x00, 0x01, 0x09, 0x21)))
    }

    @Test
    fun `When the experimental option omit known unit code TLV is set, then the unit code TLV is skipped in the byte array for known observation types`() {
        // test observation is heart rate which is a known unit code of BPM
        val obs = create_simple_numeric_observation()

        obs.omitUnitCode = true
        val tlvBytes = obs.serialize()
        val tvBytes = obs.serializeWithExperimentalOptions()
        assertEquals(tlvBytes.size - 10, tvBytes.size)
        // If handle was skipped, unit code tlv type will not exist
        assertEquals(-1, tvBytes.findFirst(byteArrayOf(0x00, 0x01, 0x09, 0x96.toByte())))
    }

    @Test
    fun `When the experimental option use short type code TLV is set, then the 16-bit (omit MDC partition) is used in the byte array for observation type`() {
        // test observation is heart rate which is a known unit code of BPM
        val obs = create_simple_numeric_observation()

        obs.useShortTypeCodes = true
        val shortTypeBytes = obs.experimentalTypeByteArray
        val typeBytes = obs.typeByteArray
        assertEquals(typeBytes.size - 2, shortTypeBytes.size)

        // Test that type code is least sig. 2 bytes of the full type code in TLV
        val typeByteTest = listOf(typeBytes.copyOfRange(0, 4), byteArrayOf(0x0, 0x2), typeBytes.copyOfRange(8, 10)).merge()
        assertArrayEquals(typeByteTest, shortTypeBytes)
    }

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
        return BluetoothBytesParser(ByteOrder.BIG_ENDIAN).encodeTLV(type, length, value, precision)
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
        return encodeTLV(SimpleNumericObservation.valueCode, SimpleNumericObservation.valueLength, value, precision)
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
        return BluetoothBytesParser(ByteOrder.BIG_ENDIAN).encodeTLV(sampleArray)
    }
}

fun BluetoothBytesParser.encodeTLV(type: Int, length: Int, value: Number, precision: Int = 2): ByteArray {
    setIntValue(type, BluetoothBytesParser.FORMAT_UINT32)
    setIntValue(length, BluetoothBytesParser.FORMAT_UINT16)
    when (value) {
        is Int -> setIntValue(value, BluetoothBytesParser.FORMAT_UINT32)
        is Short -> setIntValue(value.toInt(), BluetoothBytesParser.FORMAT_UINT16)
        is Long -> setLong(value)
        is Float -> setFloatValue(value, precision)
        else -> error("Unsupported value type sent to encodeTLV()")
    }
    return this.value
}

fun BluetoothBytesParser.encodeTLV(value: ByteArray): ByteArray {
    setIntValue(SampleArrayObservation.valueCode, BluetoothBytesParser.FORMAT_UINT32)
    setIntValue(value.size, BluetoothBytesParser.FORMAT_UINT16)
    return this.value + value
}
