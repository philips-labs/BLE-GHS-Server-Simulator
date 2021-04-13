/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import android.os.Build
import com.philips.btserver.generichealthservice.GenericHealthSensorService.Companion.getInstance
import com.welie.blessed.BluetoothPeripheralManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class ObservationEmitterTest {

    @MockK
    private lateinit var peripheralManager: BluetoothPeripheralManager
    private lateinit var serviceHandler: GenericHealthSensorService
    private lateinit var observationEmitter: ObservationEmitter

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        serviceHandler = spyk(GenericHealthSensorService(peripheralManager))
        observationEmitter = spyk(ObservationEmitter)

        mockkObject(GenericHealthSensorService.Companion)
        every { getInstance() } returns serviceHandler
    }

    @After
    fun tearDown() {
        unmockkObject(GenericHealthSensorService.Companion)
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
        ObservationEmitter.enableObservationArrayType = true

        Assert.assertTrue(ObservationEmitter.omitFixedLengthTypes)
        Assert.assertTrue(ObservationEmitter.omitHandleTLV)
        Assert.assertTrue(ObservationEmitter.omitUnitCode)
        Assert.assertTrue(ObservationEmitter.useShortTypeCodes)
        Assert.assertTrue(ObservationEmitter.enableObservationArrayType)

        ObservationEmitter.omitFixedLengthTypes = false
        ObservationEmitter.omitHandleTLV = false
        ObservationEmitter.omitUnitCode = false
        ObservationEmitter.useShortTypeCodes = false
        ObservationEmitter.enableObservationArrayType = false

        Assert.assertFalse(ObservationEmitter.omitFixedLengthTypes)
        Assert.assertFalse(ObservationEmitter.omitHandleTLV)
        Assert.assertFalse(ObservationEmitter.omitUnitCode)
        Assert.assertFalse(ObservationEmitter.useShortTypeCodes)
        Assert.assertFalse(ObservationEmitter.enableObservationArrayType)

    }

    /*
     * Observation Emitting
     */

    @Test
    fun emitter_single_shot() {
        ObservationEmitter.reset()
        ObservationEmitter.addObservationType(ObservationType.MDC_ECG_HEART_RATE)
        val obsSlot = slot<List<Observation>>()
        every { serviceHandler.sendObservations(capture(obsSlot)) } answers { println(obsSlot.captured) }

        // When
        ObservationEmitter.singleShotEmit()

        // Then
        assert(checkCapturedForHeartRateObservation(obsSlot.captured))
    }

    @Test
    fun emitter_single_shot_sample_array() {
        ObservationEmitter.reset()
        ObservationEmitter.addObservationType(ObservationType.MDC_PPG_TIME_PD_PP)
        val obsSlot = slot<List<Observation>>()
        every { serviceHandler.sendObservations(capture(obsSlot)) } answers { println(obsSlot.captured) }

        // When
        ObservationEmitter.singleShotEmit()

        // Then
        assert(checkCapturedForSampleObservation(obsSlot.captured))
    }

    @Test
    fun emitter_single_shot_hr_and_spo2() {
        ObservationEmitter.reset()
        ObservationEmitter.addObservationType(ObservationType.MDC_ECG_HEART_RATE)
        ObservationEmitter.addObservationType(ObservationType.MDC_SPO2_OXYGENATION_RATIO)
        val obsSlot = slot<List<Observation>>()
        every { serviceHandler.sendObservations(capture(obsSlot)) } answers { println(obsSlot.captured) }

        // When
        ObservationEmitter.singleShotEmit()

        // Then
        assert(checkCapturedForObservations(obsSlot.captured))
    }

    @Test
    fun emitter_single_shot_hr_and_spo2_unmerged() {
        ObservationEmitter.reset()
        ObservationEmitter.mergeObservations = false
        ObservationEmitter.addObservationType(ObservationType.MDC_ECG_HEART_RATE)
        ObservationEmitter.addObservationType(ObservationType.MDC_SPO2_OXYGENATION_RATIO)
        val list = mutableListOf<Observation>()
        every { serviceHandler.sendObservation(capture(list)) } returns Unit

        // When
        ObservationEmitter.singleShotEmit()

        // Then
        verify(exactly = 2) {
            serviceHandler.sendObservation(any())
        }
        assert(checkCapturedForObservations(list))
    }

    @Test
    fun emitter_single_shot_add_remove_spo2() {
        ObservationEmitter.reset()
        ObservationEmitter.addObservationType(ObservationType.MDC_ECG_HEART_RATE)
        ObservationEmitter.addObservationType(ObservationType.MDC_TEMP_BODY)
        ObservationEmitter.removeObservationType(ObservationType.MDC_TEMP_BODY)
        val obsSlot = slot<List<Observation>>()
        every { serviceHandler.sendObservations(capture(obsSlot)) } answers { println(obsSlot.captured) }

        // When
        ObservationEmitter.singleShotEmit()

        // Then
        assert(checkCapturedForHeartRateObservation(obsSlot.captured))
    }

    /*
     * Private helper methods
     */

    private fun checkCapturedForHeartRateObservation(observations: List<Observation>): Boolean {
        return observations.size == 1 && observations.first().type == ObservationType.MDC_ECG_HEART_RATE
    }

    private fun checkCapturedForSampleObservation(observations: List<Observation>): Boolean {
        return observations.size == 1 &&
                observations.first().type == ObservationType.MDC_PPG_TIME_PD_PP &&
                observations.first().valueByteArray.size > 0
    }

    private fun checkCapturedForObservations(observations: List<Observation>): Boolean {
        return observations.size == 2 &&
                observations.first().type == ObservationType.MDC_ECG_HEART_RATE &&
                observations.last().type == ObservationType.MDC_SPO2_OXYGENATION_RATIO
    }
}