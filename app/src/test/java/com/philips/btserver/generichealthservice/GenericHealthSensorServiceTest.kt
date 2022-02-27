/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.generichealthservice

import android.os.Build
import com.philips.btserver.BaseService
import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class GenericHealthSensorServiceTest {
    @MockK
    private lateinit var peripheralManager: BluetoothPeripheralManager

    @MockK
    private lateinit var central: BluetoothCentral

    private lateinit var serviceHandler: GenericHealthSensorService

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        serviceHandler = GenericHealthSensorService(peripheralManager)
        mockkObject(ObservationEmitter)
    }

    @After
    fun tearDown() {
        unmockkObject(ObservationEmitter)
    }

    @Test
    fun `When the service is created, all characteristics and descriptors are there`() {
        assertEquals(2, serviceHandler.service.characteristics.size)
        assertNotNull(serviceHandler.service.getCharacteristic(GenericHealthSensorService.OBSERVATION_CHARACTERISTIC_UUID))
        assertNotNull(serviceHandler.service.getCharacteristic(GenericHealthSensorService.OBSERVATION_CHARACTERISTIC_UUID).getDescriptor(BaseService.CCC_DESCRIPTOR_UUID))
    }

    @Test
    fun `When the last central disconnects, the emitter is stopped`() {
        // Given
        every { peripheralManager.connectedCentrals } returns emptySet<BluetoothCentral>()

        // When
        serviceHandler.onCentralDisconnected(central)

        // Then
        verify {
            ObservationEmitter.stopEmitter()
        }
    }

    @Test
    fun `When a central disconnects while other centrals are still connected, the emitter is not stopped`() {
        // Given
        every { peripheralManager.connectedCentrals } returns setOf(central)

        // When
        serviceHandler.onCentralDisconnected(central)

        // Then
        verify(exactly = 0) {
            ObservationEmitter.stopEmitter()
        }
    }

    @Test
    fun `When a central enables notifications, the emitter is started`() {
        // When
        serviceHandler.onNotifyingEnabled(central, serviceHandler.service.getCharacteristic(GenericHealthSensorService.OBSERVATION_CHARACTERISTIC_UUID))

        // Then
        verify(exactly = 1) {
            ObservationEmitter.startEmitter()
        }
    }

    @Test
    fun `When a central disables notifications, the emitter is stopped`() {
        // When
        serviceHandler.onNotifyingDisabled(central, serviceHandler.service.getCharacteristic(GenericHealthSensorService.OBSERVATION_CHARACTERISTIC_UUID))

        // Then
        verify(exactly = 1) {
            ObservationEmitter.stopEmitter()
        }
    }

    @Test
    fun `When an observation is to be sent, then it is sent in chunks`() {
        // Given
        val bytesSlot = mutableListOf<ByteArray>()
        every { peripheralManager.connectedCentrals } returns setOf(central)
        every { central.currentMtu } returns 23
        every { peripheralManager.notifyCharacteristicChanged(capture(bytesSlot), serviceHandler.service.getCharacteristic(GenericHealthSensorService.OBSERVATION_CHARACTERISTIC_UUID)) } returns true
        val observation = SimpleNumericObservation(39, ObservationType.MDC_TEMP_BODY, 35.8f, 1, UnitCode.MDC_DIM_DEGC, Date(1614960708472))

        // When
        serviceHandler.sendObservation(observation)

        // Then
        verify(exactly = 3) {
            peripheralManager.notifyCharacteristicChanged(any(), any())
        }

        assertEquals(3, bytesSlot.size)

        val part1 = BluetoothBytesParser.string2bytes("050001092F000400024B5C000109210002002700")
        val part2 = BluetoothBytesParser.string2bytes("08010A560004FF000166000109960004000417A0")
        val part3 = BluetoothBytesParser.string2bytes("0E000109900008000001780328CB78")

        assertTrue(part1.contentEquals(bytesSlot[0]))
        assertTrue(part2.contentEquals(bytesSlot[1]))
        assertTrue(part3.contentEquals(bytesSlot[2]))
    }
}