/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.gatt

import android.os.Build
import com.philips.btserver.gatt.CurrentTimeService.Companion.CURRENT_TIME_CHARACTERISTIC_UUID
import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])

class CurrentTimeServiceTest {

    @MockK
    private lateinit var peripheralManager: BluetoothPeripheralManager

    @MockK
    private lateinit var central: BluetoothCentral

    private lateinit var serviceHandler: CurrentTimeService

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        serviceHandler = CurrentTimeService(peripheralManager)
    }

    @Test
    fun when_the_service_is_created_all_characteristics_and_descriptors_are_there() {
        Assert.assertEquals(1, serviceHandler.service.characteristics.size)
        Assert.assertNotNull(serviceHandler.service.getCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID))
    }

    @Test
    fun when_a_central_enables_notifications_the_current_time_is_notified() {
        // Given
        val bytesSlot = slot<ByteArray>()
        every { peripheralManager.notifyCharacteristicChanged(capture(bytesSlot), any()) } returns true

        // When
        val parser = BluetoothBytesParser()
        parser.setCurrentTime(Calendar.getInstance())
        val originalTime = parser.dateTime.time
        serviceHandler.onNotifyingEnabled(central, serviceHandler.service.getCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID))

        // Then
        verify(exactly = 1) {
            peripheralManager.notifyCharacteristicChanged(any(), serviceHandler.service.getCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID))
        }

        val time = BluetoothBytesParser(bytesSlot.captured).dateTime.time
        assertTrue(time-originalTime < 1000)
    }

    @Test
    fun given_notifications_are_enabled_when_a_central_disables_notifications_the_current_time_is_not_notified() {
        // Given
        every { peripheralManager.notifyCharacteristicChanged(any(), any()) } returns true
        serviceHandler.onNotifyingEnabled(central, serviceHandler.service.getCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID))

        verify(exactly = 1) {
            peripheralManager.notifyCharacteristicChanged(any(), serviceHandler.service.getCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID))
        }

        // When
        serviceHandler.onNotifyingDisabled(central, serviceHandler.service.getCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID))

        // Then
        verify(exactly = 1) {
            peripheralManager.notifyCharacteristicChanged(any(), serviceHandler.service.getCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID))
        }
    }
}