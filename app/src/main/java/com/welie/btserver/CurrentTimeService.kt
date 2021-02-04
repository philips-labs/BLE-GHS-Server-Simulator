package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Handler
import android.os.Looper
import com.welie.btserver.extensions.asHexString
import timber.log.Timber
import java.util.*

internal class CurrentTimeService(peripheralManager: PeripheralManager) : BaseService(peripheralManager) {
    override var service = BluetoothGattService(CTS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    var currentTime = BluetoothGattCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE)
    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnable = Runnable { notifyCurrentTime() }

    override fun onCharacteristicWrite(central: Central, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        Timber.i("onCharacteristicWrite <%s>", value.asHexString())
        return super.onCharacteristicWrite(central, characteristic, value)
    }

    override fun onNotifyingEnabled(central: Central, characteristic: BluetoothGattCharacteristic) {
        notifyCurrentTime()
    }

    override fun onNotifyingDisabled(central: Central, characteristic: BluetoothGattCharacteristic) {
        handler.removeCallbacks(notifyRunnable)
    }

    private fun notifyCurrentTime() {
        setCurrentTime()
        notifyCharacteristicChanged(currentTime.value, currentTime)
        handler.postDelayed(notifyRunnable, 1000)
    }

    private fun setCurrentTime() {
        val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
        parser.setCurrentTime(Calendar.getInstance())
        currentTime.value = parser.bytes
    }

    companion object {
        private val CTS_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
        private val CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")
    }

    init {
        service.addCharacteristic(currentTime)
        currentTime.addDescriptor(getCccDescriptor())
        setCurrentTime()
    }
}