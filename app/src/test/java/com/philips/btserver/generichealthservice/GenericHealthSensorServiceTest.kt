package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

private const val DEFAULT_DEVICE_ADDRESS = "12:34:56:65:43:21"
private const val DEFAULT_DEVICE_NAME = "A&D 651BLE"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class GenericHealthSensorServiceTest {
    @MockK
    private lateinit var peripheralManager: BluetoothPeripheralManager

    @MockK
    private lateinit var central: BluetoothCentral

    private lateinit var service: GenericHealthSensorService

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        service = GenericHealthSensorService(peripheralManager)
        mockkObject(ObservationEmitter)
    }

    @After
    fun tearDown() {
        unmockkObject(ObservationEmitter)
    }

    @Test
    fun `When the last central disconnects, the emittor is stopped`() {
        // Given
        every { peripheralManager.connectedCentrals } returns emptySet<BluetoothCentral>()

        // When
        service.onCentralDisconnected(central)

        // Then
        verify {
            ObservationEmitter.stopEmitter()
        }
    }

    @Test
    fun Abc() {
        val service = BluetoothGattService(UUID.fromString("00001809-0000-1000-8000-00805f9b34fb"), 0)
        val characteristic = BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), BluetoothGattCharacteristic.PROPERTY_INDICATE, 0)
        val descriptor = BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), 0)
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(descriptor)

        assertEquals(1, service.characteristics.size)
    }
}