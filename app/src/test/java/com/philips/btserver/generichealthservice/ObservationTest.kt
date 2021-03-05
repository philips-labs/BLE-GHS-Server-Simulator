package com.philips.btserver.generichealthservice

import com.philips.btserver.extensions.findFirst
import com.philips.btserver.extensions.merge
import com.welie.blessed.BluetoothBytesParser
import org.junit.Assert
import org.junit.Test
import java.nio.ByteOrder
import java.util.*

class ObservationTest {

    private val observationType = ObservationType.MDC_ECG_HEART_RATE
    private val randomValue = ObservationType.MDC_ECG_HEART_RATE.randomNumericValue()
    private val now = Date()

    @Test
    fun simple_numeric_observation_instantiation() {
        val obs = create_simple_numeric_observation()
        Assert.assertEquals(100.toShort(), obs.id)
        Assert.assertEquals(observationType, obs.type)
        Assert.assertEquals(randomValue, obs.value)
        Assert.assertEquals(observationType.numericPrecision(), obs.valuePrecision)
        Assert.assertEquals(UnitCode.MDC_DIM_BEAT_PER_MIN, obs.unitCode)
        Assert.assertEquals(now, obs.timestamp)
    }

    /*
     * Tests for full (no serialization options) SimpleNumericObservation serialization
     */

    @Test
    fun observation_serialize_handleByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(handle_byte_array_for(obs.id), obs.handleByteArray)
    }

    @Test
    fun observation_serialize_typeByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(type_byte_array_for(obs.type), obs.typeByteArray)
    }

    @Test
    fun observation_serialize_unitByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(unit_byte_array_for(obs.unitCode), obs.unitByteArray)
    }

    @Test
    fun observation_serialize_timestampByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(timestamp_byte_array_for(obs.timestamp), obs.timestampByteArray)
    }

    @Test
    fun simple_numeric_observation_serialize_valueByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(simple_numeric_value_byte_array_for(obs.value, obs.valuePrecision), obs.valueByteArray )
    }

    @Test
    fun simple_numeric_observation_serialize_mergedByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(this.simple_numeric_observation_serialize_byte_array(obs), obs.serialize())
    }

    /*
     * Tests for SimpleNumericObservation serialization with experimental options
     */

    @Test
    fun simple_numeric_obs_experimental_accessor_setting() {
        // setting via accessors
        val obs = create_simple_numeric_observation()
        obs.omitFixedLengthTypes = true
        obs.omitHandleTLV = true
        obs.omitUnitCode = true
        obs.useShortTypeCodes = true

        Assert.assertTrue(obs.omitFixedLengthTypes)
        Assert.assertTrue(obs.omitHandleTLV)
        Assert.assertTrue(obs.omitUnitCode)
        Assert.assertTrue(obs.useShortTypeCodes)
    }

    @Test
    fun simple_numeric_obs_experimental_bits_setting() {
        // Setting via bit set ("in bulk"... used by ObservationEmitter)
        val obs = create_simple_numeric_observation()
        val optionsBitSet = BitSet()

        optionsBitSet.set(Observation.ExperimentalFeature.omitFixedLengthTypes.bit, true)
        optionsBitSet.set(Observation.ExperimentalFeature.omitHandleTLV.bit, true)
        optionsBitSet.set(Observation.ExperimentalFeature.omitUnitCode.bit, true)
        optionsBitSet.set(Observation.ExperimentalFeature.useShortTypeCodes.bit, true)
        obs.experimentalOptions = optionsBitSet

        Assert.assertTrue(obs.omitFixedLengthTypes)
        Assert.assertTrue(obs.omitHandleTLV)
        Assert.assertTrue(obs.omitUnitCode)
        Assert.assertTrue(obs.useShortTypeCodes)
    }

    @Test
    // Note: validating based on length skipped, vs. contents of byte array
    fun simple_numeric_obs_experimental_omit_fixed_length_types() {
        val obs = create_simple_numeric_observation()

        obs.omitFixedLengthTypes = false
        var tlvBytes = obs.handleByteArray
        obs.omitFixedLengthTypes = true
        var tvBytes = obs.handleByteArray
        Assert.assertEquals(tlvBytes.size - 2, tvBytes.size)

        obs.omitFixedLengthTypes = false
        tlvBytes = obs.typeByteArray
        obs.omitFixedLengthTypes = true
        tvBytes = obs.typeByteArray
        Assert.assertEquals(tlvBytes.size - 2, tvBytes.size)

        obs.omitFixedLengthTypes = false
        tlvBytes = obs.unitByteArray
        obs.omitFixedLengthTypes = true
        tvBytes = obs.unitByteArray
        Assert.assertEquals(tlvBytes.size - 2, tvBytes.size)

        obs.omitFixedLengthTypes = false
        tlvBytes = obs.timestampByteArray
        obs.omitFixedLengthTypes = true
        tvBytes = obs.timestampByteArray
        Assert.assertEquals(tlvBytes.size - 2, tvBytes.size)

        obs.omitFixedLengthTypes = false
        tlvBytes = obs.valueByteArray
        obs.omitFixedLengthTypes = true
        tvBytes = obs.valueByteArray
        Assert.assertEquals(tlvBytes.size - 2, tvBytes.size)

        // Ensure all the lengths are gone (all fixed length)
        obs.omitFixedLengthTypes = false
        tlvBytes = obs.serialize()
        obs.omitFixedLengthTypes = true
        tvBytes = obs.serializeWithExperimentalOptions()
        Assert.assertEquals(tlvBytes.size - 10, tvBytes.size)
    }

    @Test
    fun simple_numeric_obs_experimental_omit_handle_tlv() {
        val obs = create_simple_numeric_observation()

        obs.omitHandleTLV = true
        val tlvBytes = obs.serialize()
        val tvBytes = obs.serializeWithExperimentalOptions()
        Assert.assertEquals(tlvBytes.size - 8, tvBytes.size)
        // If handle was skipped, handle tlv type will not exist
        Assert.assertEquals(-1, tvBytes.findFirst(byteArrayOf(0x00, 0x01, 0x09, 0x21)) )
    }

    @Test
    fun simple_numeric_obs_experimental_omit_known_unit_codes_tlv() {
        // test observation is heart rate which is a known unit code of BPM
        val obs = create_simple_numeric_observation()

        obs.omitUnitCode = true
        val tlvBytes = obs.serialize()
        val tvBytes = obs.serializeWithExperimentalOptions()
        Assert.assertEquals(tlvBytes.size - 10, tvBytes.size)
        // If handle was skipped, unit code tlv type will not exist
        Assert.assertEquals(-1, tvBytes.findFirst(byteArrayOf(0x00, 0x01, 0x09, 0x96.toByte())) )
    }

    @Test
    fun simple_numeric_obs_experimental_use_short_type_codes_tlv() {
        // test observation is heart rate which is a known unit code of BPM
        val obs = create_simple_numeric_observation()

        obs.useShortTypeCodes = true
        val shortTypeBytes = obs.experimentalTypeByteArray
        val typeBytes = obs.typeByteArray
        Assert.assertEquals(typeBytes.size - 2, shortTypeBytes.size)

        // Test that type code is least sig. 2 bytes of the full type code in TLV
        val typeByteTest = listOf(typeBytes.copyOfRange(0, 4), byteArrayOf(0x0, 0x2), typeBytes.copyOfRange(8, 10)).merge()
        Assert.assertArrayEquals(typeByteTest, shortTypeBytes)
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

    // Do not use the Observation encodeTLV() method in order to ensure it hasn't been broken
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