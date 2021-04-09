package com.philips.btserver.gatt

import android.os.Build
import com.philips.btserver.gatt.DeviceInformationService.Companion.MANUFACTURER_NAME_CHARACTERISTIC_UUID
import com.philips.btserver.gatt.DeviceInformationService.Companion.MODEL_NUMBER_CHARACTERISTIC_UUID
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

    private lateinit var serviceHandler: DeviceInformationService

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        serviceHandler = DeviceInformationService(peripheralManager)
    }

    @Test
    fun `When the service is created, all characteristics are there`() {
        assertEquals(2, serviceHandler.service.characteristics.size)
        assertNotNull(serviceHandler.service.getCharacteristic(MODEL_NUMBER_CHARACTERISTIC_UUID))
        assertNotNull(serviceHandler.service.getCharacteristic(MANUFACTURER_NAME_CHARACTERISTIC_UUID))
    }

    @Test
    fun `Given an initalized service, when the model is set, then is can be retrieved`() {
        val modelNumber = "123"
        serviceHandler.setModelNumber(modelNumber)
        assertEquals(modelNumber, serviceHandler.getModelNumber())
        assertEquals(modelNumber, serviceHandler.service.getCharacteristic(MODEL_NUMBER_CHARACTERISTIC_UUID).getStringValue(0))
    }

    @Test
    fun `Given an initalized service, when the manufacturer is set, then is can be retrieved`() {
        val manufacturer = "Philips"
        serviceHandler.setManufacturer(manufacturer)
        assertEquals(manufacturer, serviceHandler.getManufacturer())
        assertEquals(manufacturer, serviceHandler.service.getCharacteristic(MANUFACTURER_NAME_CHARACTERISTIC_UUID).getStringValue(0))
    }
}