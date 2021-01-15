package com.welie.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Handler
import android.os.Looper
import com.welie.btserver.*
import com.welie.btserver.GenericHealthSensorService
import com.welie.btserver.Unit
import com.welie.btserver.extensions.asHexString
import timber.log.Timber
import java.util.*

class ObservationEmitter: ServiceListener {

    val observations = listOf(SimpleNumericObservation(1.toShort(),
            ObservationType.ORAL_TEMPERATURE,
            38.7f,
            1,
            Unit.PERCENT,
            Calendar.getInstance().time))
    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnable = Runnable { sendObservations() }
    private val ghsService = GenericHealthSensorService.getInstance()

    init {
        ghsService?.addListener(this)
    }

    fun startEmitter() {
        Timber.i("Starting GHS Observtaion Emitter")
        handler.post(notifyRunnable)
    }

    fun stopEmitter() {
        Timber.i("Stopping GHS Observtaion Emitter")
        handler.removeCallbacks(notifyRunnable)
    }

    private fun sendObservations() {
        observations.forEach {
            Timber.i("Emitting Value ${it.serialize().asHexString()}")
            if (ghsService != null) {
                ghsService.sendObservation(it)
            }
            handler.postDelayed(notifyRunnable, 5000)
        }
    }

    // ServiceListener interface methods

    override fun onConnected(numberOfConnections: Int) {
        ghsService?.addListener(this)
    }

    override fun onDisconnected(numberOfConnections: Int) {
        if (numberOfConnections == 0) {
            ghsService?.removeListener(this)
        }
    }

    override fun onNotifyingEnabled(characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == GenericHealthSensorService.OBSERVATION_CHARACTERISTIC_UUID) {
            startEmitter()
        }
    }

    override fun onNotifyingDisabled(ccharacteristic: BluetoothGattCharacteristic) {
        stopEmitter()
    }

    override fun onCharacteristicRead(characteristic: BluetoothGattCharacteristic) {}

    override fun onCharacteristicWrite(characteristic: BluetoothGattCharacteristic, value: ByteArray) {}

    override fun onDescriptorRead(descriptor: BluetoothGattDescriptor) {}

    override fun onDescriptorWrite(descriptor: BluetoothGattDescriptor, value: ByteArray) {}

}