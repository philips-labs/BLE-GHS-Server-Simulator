package com.philips.btserver.gatt

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.asFormattedHexString
import com.philips.btserver.extensions.asHexString
import com.philips.btserver.generichealthservice.ElapsedTimeService
import com.philips.btserver.generichealthservice.ReconnectionConfigurationService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import timber.log.Timber
import java.util.*

internal class BatteryService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {
    override val service = BluetoothGattService(BATTERY_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    private val batteryLevelCharacteristic = BluetoothGattCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ)

    private var batteryLevel = 100


    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ): ReadResponse {
        return if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
            ReadResponse(GattStatus.SUCCESS, byteArrayOf(batteryLevel.toByte()))
        } else { super.onCharacteristicRead(central, characteristic)}
    }

    /*
     * update the battery level and notify it
     */
    fun updateAndNotifyBatteryLevel() {
        batteryLevel = batteryLevel -1;
        if (batteryLevel <0) batteryLevel = 100
        Timber.i("Notifying Battery Level: ${batteryLevel}")

        setCharacteristicValue(batteryLevelCharacteristic, byteArrayOf(batteryLevel.toByte()))

        if (!notifyCharacteristicChanged(byteArrayOf(batteryLevel.toByte()), batteryLevelCharacteristic)) {
            Timber.i("Notifying Battery Level failed...")
        }
    }

    // Notification from [central] that [characteristic] has notification enabled.
    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        when (characteristic.uuid) {
            BATTERY_LEVEL_CHARACTERISTIC_UUID -> {
                Timber.i("Battery Level notifying enabled")
                batteryLevelCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID)?.setValue(
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
            else -> { // weird??
            }
        }
        super.onNotifyingEnabled(central, characteristic)
    }

    override fun onNotifyingDisabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        when (characteristic.uuid) {
            BATTERY_LEVEL_CHARACTERISTIC_UUID -> {
                Timber.i("Battery Level notifying disabled")
                batteryLevelCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID)?.setValue(
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            }
            else -> {
            }
        }
        super.onNotifyingDisabled(central, characteristic)
    }



    companion object {
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

        // If the BluetoothService has a running BatteryService then return it
        fun getInstance(): BatteryService {
            val bleServer = BluetoothServer.getInstance()
            val dis = bleServer?.getServiceWithUUID(BATTERY_SERVICE_UUID)
            return dis.let { it as BatteryService }
        }
    }

    init {
        initCharacteristic(batteryLevelCharacteristic, "Battery Level")
    }


}