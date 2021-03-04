package com.philips.btserver.generichealthservice

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

    private fun encodeTLV(type: Int, length: Int, value: Int, isValueShort: Boolean = false): ByteArray {
        val parser = BluetoothBytesParser(ByteOrder.BIG_ENDIAN)
        parser.setIntValue(type, BluetoothBytesParser.FORMAT_UINT32)
        parser.setIntValue(length, BluetoothBytesParser.FORMAT_UINT16)
        parser.setIntValue(value,
                if (isValueShort)
                    BluetoothBytesParser.FORMAT_UINT16
                else
                    BluetoothBytesParser.FORMAT_UINT32 )
        return parser.value
    }


    fun handle_byte_array_for(id: Short): ByteArray {
        return encodeTLV(Observation.handleCode, Observation.handleLength, id.toInt())
    }

    @Test
    fun simple_numeric_observation_serialize_handleByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(obs.handleByteArray, handle_byte_array_for(obs.id))
    }

    fun type_byte_array_for(type: ObservationType): ByteArray {
        return encodeTLV(Observation.typeCode, Observation.typeLength, type.value, false)
    }

    @Test
    fun simple_numeric_observation_serialize_typeByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(obs.typeByteArray, type_byte_array_for(obs.type))
    }

    fun unit_byte_array_for(unitCode: UnitCode): ByteArray {
        return encodeTLV(Observation.unitCodeId, Observation.unitLength, unitCode.value)
    }

    @Test
    fun simple_numeric_observation_serialize_unitByteArray() {
        val obs = create_simple_numeric_observation()
        Assert.assertArrayEquals(obs.unitByteArray, unit_byte_array_for(obs.unitCode))
    }

}