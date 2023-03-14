/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.gatt

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Build
import com.welie.blessed.BluetoothPeripheralManager
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.*
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import java.util.*

enum class UDIFlags(override val bit: Long) : Flags {
    labelPresent((1 shl 0).toLong()),
    deviceIdentifierPresent((1 shl 1).toLong()),
    issuerPresent((1 shl 2).toLong()),
    authorityPresent((1 shl 3).toLong())
}

internal class DeviceInformationService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(DIS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    private val manufacturerChar = createReadCharacteristic(MANUFACTURER_NAME_CHARACTERISTIC_UUID)
    private val modelNumberChar = createReadCharacteristic(MODEL_NUMBER_CHARACTERISTIC_UUID)
    private val uniqueDeviceIdentifierChar = BluetoothGattCharacteristic(UDI_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM)

    var manufacturer = Build.MANUFACTURER
    var modelNumber = Build.MODEL
    var udiValue = udiBytes(UDI_LABEL, UDI_DEVICE_ID, UDI_ISSUER, UDI_AUTHORITY)

    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ): ReadResponse {
        return when(characteristic.uuid) {
            MANUFACTURER_NAME_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, manufacturer.toByteArray())
            MODEL_NUMBER_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, modelNumber.toByteArray())
            UDI_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, udiValue)
            else -> super.onCharacteristicRead(central, characteristic)
        }
    }

    private fun createReadCharacteristic(charUUID: UUID): BluetoothGattCharacteristic {
        return BluetoothGattCharacteristic(charUUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
    }

    private fun udiBytes(label : String? = null, deviceIdentifier : String? = null, issuer : String? = null, authority: String? = null ) : ByteArray {
        var bytes = byteArrayOf()
        var udiFlags = BitMask(0)

        label?.let {
            udiFlags = udiFlags.set(UDIFlags.labelPresent)
            bytes += udiBytesFor(it)
        }

        deviceIdentifier?.let {
            udiFlags = udiFlags.set(UDIFlags.deviceIdentifierPresent)
            bytes += udiBytesFor(it)
        }

        issuer?.let {
            udiFlags = udiFlags.set(UDIFlags.issuerPresent)
            bytes += udiBytesFor(it)
        }

        authority?.let {
            udiFlags = udiFlags.set(UDIFlags.authorityPresent)
            bytes += udiBytesFor(it)
        }

        bytes = byteArrayOf(udiFlags.value.toByte()) + bytes
        return bytes
    }

    private fun udiBytesFor(udiString: String): ByteArray {
        return  udiString.toByteArray(Charsets.UTF_8) + 0x0.toByte()
    }

    companion object {
        val DIS_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")
        val UDI_CHARACTERISTIC_UUID: UUID = UUID.fromString("00007F3A-0000-1000-8000-00805f9b34fb")

        const val UDI_LABEL = "{01}00844588003288{17}141120{10}7654321D{21}10987654d321"
        const val UDI_DEVICE_ID = "00844588003288"
        const val UDI_ISSUER = "2.51"
        const val UDI_AUTHORITY = "2.16.840.1.113883.3.24"

        // If the BluetoothService has a running DeviceInformationService then return it
        fun getInstance(): DeviceInformationService? {
            val bleServer = BluetoothServer.getInstance()
            val dis = bleServer?.getServiceWithUUID(DIS_SERVICE_UUID)
            return dis?.let { it as DeviceInformationService }
        }
    }

    init {
        service.addCharacteristic(manufacturerChar)
        service.addCharacteristic(modelNumberChar)
        service.addCharacteristic(uniqueDeviceIdentifierChar)
        timber.log.Timber.i("UDI value as hex: ${udiValue.asHexString()}")
    }

}
