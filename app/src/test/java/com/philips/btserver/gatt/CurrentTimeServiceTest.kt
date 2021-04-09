/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.gatt

import android.os.Build
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        Assert.assertNotNull(serviceHandler.service.getCharacteristic(CurrentTimeService.CURRENT_TIME_CHARACTERISTIC_UUID))
    }

    @Test
    fun when_a_central_enables_notifications_the_current_time_is_notified() {
        // Given
        every { peripheralManager.notifyCharacteristicChanged(any(), any()) } returns true

        // When
        serviceHandler.onNotifyingEnabled(central, serviceHandler.service.getCharacteristic(CurrentTimeService.CURRENT_TIME_CHARACTERISTIC_UUID))

        // Then
        verify(exactly = 1) {
            serviceHandler.notifyCurrentTime()
        }
    }

}