package com.philips.btserver.gatt

import android.os.Build
import com.philips.btserver.generichealthservice.ObservationEmitter
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class DeviceInformationServiceTest {
    @MockK
    private lateinit var peripheralManager: BluetoothPeripheralManager

    @MockK
    private lateinit var central: BluetoothCentral

    private lateinit var serviceHandler: DeviceInformationService

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        serviceHandler = DeviceInformationService(peripheralManager)
        mockkObject(ObservationEmitter)
    }

    @Test
    fun `When the service is created, all characteristics and descriptors are there`() {
        assertEquals(2, serviceHandler.service.characteristics.size)
    }

    @Test
    fun `Given an initalized service, when the model is set, then is can be retrieved`() {
        val modelNumber = "123"
        serviceHandler.setModelNumber(modelNumber)
        assertEquals(modelNumber, serviceHandler.getModelNumber())
    }

    @Test
    fun `Given an initalized service, when the manufacturer is set, then is can be retrieved`() {
        val manufacturer = "Philips"
        serviceHandler.setManufacturer(manufacturer)
        assertEquals(manufacturer, serviceHandler.getManufacturer())
    }
}