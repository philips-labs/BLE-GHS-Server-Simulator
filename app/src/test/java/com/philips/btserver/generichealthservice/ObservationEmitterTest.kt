package com.philips.btserver.generichealthservice

import android.os.Looper
import android.os.Looper.myLooper
import io.mockk.*
import org.junit.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

public class ObservationEmitterTest {

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
//        every { peripheral.address } returns DEFAULT_DEVICE_ADDRESS
//        every { peripheral.name } returns DEFAULT_DEVICE_NAME

        mockkObject(ObservationEmitter)
//        every { DateTimeTz.Companion.nowLocal() } returns mockNow

//        handler.addListener(currentTimeServiceHandlerListener)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(ObservationEmitter)
    }

    /*
     * Experimental options settings
     */
    @Test
    fun observation_emitter_experimental_options() {
        ObservationEmitter.omitFixedLengthTypes = true
        ObservationEmitter.omitHandleTLV = true
        ObservationEmitter.omitUnitCode = true
        ObservationEmitter.useShortTypeCodes = true

        Assert.assertTrue(ObservationEmitter.omitFixedLengthTypes)
        Assert.assertTrue(ObservationEmitter.omitHandleTLV)
        Assert.assertTrue(ObservationEmitter.omitUnitCode)
        Assert.assertTrue(ObservationEmitter.useShortTypeCodes)

        ObservationEmitter.omitFixedLengthTypes = false
        ObservationEmitter.omitHandleTLV = false
        ObservationEmitter.omitUnitCode = false
        ObservationEmitter.useShortTypeCodes = false

        Assert.assertFalse(ObservationEmitter.omitFixedLengthTypes)
        Assert.assertFalse(ObservationEmitter.omitHandleTLV)
        Assert.assertFalse(ObservationEmitter.omitUnitCode)
        Assert.assertFalse(ObservationEmitter.useShortTypeCodes)
    }

}