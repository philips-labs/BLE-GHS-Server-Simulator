package com.philips.btserver.generichealthservice

import com.philips.btserver.extensions.merge
import com.welie.blessed.BluetoothBytesParser
import org.junit.Assert
import org.junit.Test
import java.nio.ByteOrder
import java.util.*

class ObservationTest {

    val observationType = ObservationType.MDC_ECG_HEART_RATE
    val randomValue = ObservationType.MDC_ECG_HEART_RATE.randomNumericValue()
    val now = Date()

    fun create_simple_numeric_observation(): SimpleNumericObservation {
        return SimpleNumericObservation(
                id = 100,
                type = observationType,
                value = randomValue,
                valuePrecision = observationType.numericPrecision(),
                unitCode = UnitCode.MDC_DIM_BEAT_PER_MIN,
                timestamp = now)
    }

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

    private fun encodeTLV(type: Int, length: Int, value: Number, precision: Int = 2): ByteArray {
        val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
        parser.setIntValue(type, BluetoothBytesParser.FORMAT_UINT32)
        parser.setIntValue(length, BluetoothBytesParser.FORMAT_UINT16)
        when (value) {
            is Int -> parser.setIntValue(value, BluetoothBytesParser.FORMAT_UINT32)
            is Short -> parser.setIntValue(value.toInt(), BluetoothBytesParser.FORMAT_UINT16)
            is Long -> parser.setLong(value)
            is Float -> parser.setFloatValue(value, precision)
            else -> error("Unsupported value type sent to encodeTLV()")
        }
        return parser.value
    }

    fun handle_byte_array_for(id: Short): ByteArray {
        return encodeTLV(Observation.handleCode, Observation.handleLength, id)
    }

    @Test
    fun observation_serialize_handleByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(obs.handleByteArray, handle_byte_array_for(obs.id))
    }

    fun type_byte_array_for(type: ObservationType): ByteArray {
        return encodeTLV(Observation.typeCode, Observation.typeLength, type.value)
    }

    @Test
    fun observation_serialize_typeByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(obs.typeByteArray, type_byte_array_for(obs.type))
    }

    fun unit_byte_array_for(unitCode: UnitCode): ByteArray {
        return encodeTLV(Observation.unitCodeId, Observation.unitLength, unitCode.value)
    }

    @Test
    fun observation_serialize_unitByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(obs.unitByteArray, unit_byte_array_for(obs.unitCode))
    }

    fun timestamp_byte_array_for(timestamp: Date): ByteArray {
        return encodeTLV(Observation.timestampCode, Observation.timestampLength, timestamp.time)
    }

    @Test
    fun observation_serialize_timestampByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(obs.timestampByteArray, timestamp_byte_array_for(obs.timestamp))
    }

    fun simple_numeric_value_byte_array_for(value: Float, precision: Int): ByteArray {
        return encodeTLV(SimpleNumericObservation.valueCode, SimpleNumericObservation.valueLength, value, precision)
    }

    @Test
    fun simple_numeric_observation_serialize_valueByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(obs.valueByteArray, simple_numeric_value_byte_array_for(obs.value, obs.valuePrecision))
    }




//    fun merged_observation_byte_array(): ByteArray {
//        val obs = create_simple_numeric_observation()
//        return listOf(
//                type_byte_array_for(obs.type),
//                handle_byte_array_for(obs.id),
//                simple_numeric_value_byte_array_for(obs.value, obs.valuePrecision),
//                unit_byte_array_for(obs.unitCode),
//                timestampByteArray).merge()
//
//    }
}