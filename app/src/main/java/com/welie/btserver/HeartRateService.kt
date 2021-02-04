package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.util.*

internal class HeartRateService(peripheralManager: PeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(HRS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    private val measurement = BluetoothGattCharacteristic(HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ)

    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnable = Runnable { notifyHeartRate() }
    private var currentHR = 80

    override fun onCentralDisconnected(central: Central) {
        if (noCentralsConnected()) {
            stopNotifying()
        }
    }

    override fun onNotifyingEnabled(central: Central, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID) {
            notifyHeartRate()
        }
    }

    override fun onNotifyingDisabled(central: Central, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID) {
            stopNotifying()
        }
    }

    private fun notifyHeartRate() {
        currentHR += (Math.random() * 10 - 5).toInt()
        measurement.value = byteArrayOf(0x00, currentHR.toByte())
        notifyCharacteristicChanged(measurement.value, measurement)
        Timber.i("new hr: %d", currentHR)
        handler.postDelayed(notifyRunnable, 1000)
    }

    private fun stopNotifying() {
        handler.removeCallbacks(notifyRunnable)
        measurement.getDescriptor(PeripheralManager.CCC_DESCRIPTOR_UUID).value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
    }

    companion object {
        private val HRS_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        private val HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
    }

    init {
        service.addCharacteristic(measurement)
        measurement.value = byteArrayOf(0x00, 0x40)
        measurement.addDescriptor(getCccDescriptor())
    }
}