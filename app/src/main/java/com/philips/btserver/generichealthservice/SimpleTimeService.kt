package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.asFixedFormatByteArray
import com.philips.btserver.extensions.asSimpleTimeByteArray
import com.philips.btserver.extensions.merge
import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import java.nio.ByteOrder
import java.util.*

internal class SimpleTimeService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(SIMPLE_TIME_SERVICE_UUID, SERVICE_TYPE_PRIMARY)

    private val simpleTimeCharacteristic = BluetoothGattCharacteristic(
        SIMPLE_TIME_CHARACTERISTIC_UUID,
        PROPERTY_READ or PROPERTY_WRITE or PROPERTY_INDICATE,
        PERMISSION_READ or PERMISSION_WRITE
    )

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
        sendClockBytes()
    }

    /**
     * Notification from [central] that [characteristic] has notification enabled. Implies that
     * there is a connection so start emitting observations.
     */
    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        sendClockBytes()
    }

    /*
     * onCharacteristicRead is a non-abstract method with an empty body to have a default behavior to do nothing
     */
    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (characteristic.uuid == SIMPLE_TIME_CHARACTERISTIC_UUID) {
            sendClockBytes()
        }
    }

    /*
     * send the current clock in the GHS byte format based on current flags
     */
    private fun sendClockBytes() {
        val bytes = Date().asFixedFormatByteArray()
        // TODO Add the Clock status and clock capailities flags for real
        simpleTimeCharacteristic.value = listOf(bytes, byteArrayOf(0, 0)).merge()
        notifyCharacteristicChanged(bytes, simpleTimeCharacteristic)
    }

    init {
        initCharacteristic(simpleTimeCharacteristic, SIMPLE_TIME_DESCRIPTION)
    }

    // TODO This is a dupe of the method in SimpleTimeService... let's make this an extension!
    private fun initCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        description: String
    ) {
        service.addCharacteristic(characteristic)
        characteristic.addDescriptor(getCccDescriptor())
        characteristic.addDescriptor(getCudDescriptor(description))
        characteristic.value = byteArrayOf(0x00)
    }

    companion object {
        val SIMPLE_TIME_SERVICE_UUID = UUID.fromString("00007f3E-0000-1000-8000-00805f9b34fb")
        val SIMPLE_TIME_CHARACTERISTIC_UUID =
            UUID.fromString("00007f3d-0000-1000-8000-00805f9b34fb")
        private const val SIMPLE_TIME_DESCRIPTION = "Characteristic for GHS simple time."

        /**
         * If the [BluetoothServer] singleton has an instance of a GenericHealthSensorService return it (otherwise null)
         */
        fun getInstance(): SimpleTimeService? {
            val bleServer = BluetoothServer.getInstance()
            val ghs = bleServer?.getServiceWithUUID(SIMPLE_TIME_SERVICE_UUID)
            return ghs?.let { it as SimpleTimeService }
        }
    }
}
