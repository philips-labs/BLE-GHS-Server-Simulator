package com.philips.btserver.elapsedTime

import android.os.Build
import com.philips.btserver.BluetoothServer
import com.philips.btserver.generichealthservice.ElapsedTimeService
import com.philips.btserver.generichealthservice.GenericHealthSensorService
import com.philips.btserver.observations.ObservationEmitter
import com.philips.btserver.observations.UnitCode
import com.philips.btserver.observations.numericPrecision
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class ElapsedTimeServiceTest {
    @MockK
    private lateinit var peripheralManager: BluetoothPeripheralManager

    @MockK
    private lateinit var central: BluetoothCentral

    private lateinit var etsHandler: ElapsedTimeService


    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        etsHandler = ElapsedTimeService(peripheralManager)
        mockkObject(ObservationEmitter)
    }

    @After
    fun tearDown() {
        unmockkObject(ObservationEmitter)
    }


    @Test
    fun `When 0x22A66A002C000007000101 is writted to the CurrentElapsedTime characteristic then all properties are set correctly`() {

        val bytes : ByteArray = stringToByteArray("22A66A002C000007000101")
        val etc = etsHandler.service.getCharacteristic(ElapsedTimeService.ELASPED_TIME_CHARACTERISTIC_UUID)
        Assert.assertNotNull(etsHandler.service.getCharacteristic(ElapsedTimeService.ELASPED_TIME_CHARACTERISTIC_UUID))
        etsHandler.writeETSBytes(bytes)
        Assert.assertNotNull(etsHandler.currentClockBytes())

    }

    private fun stringToByteArray( string: String): ByteArray {
        var bytes = byteArrayOf()
        for (i in 0 until string.length/2) {
            val byte : Byte = java.lang.Integer.parseUnsignedInt(string.subSequence(2*i, 2*i+2).toString(),16).toByte()
            bytes += byte
        }
        return bytes
    }
}