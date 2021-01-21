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

object ObservationEmitter: ServiceListener {

    val observations = mutableListOf(SimpleNumericObservation(1.toShort(),
            ObservationType.ORAL_TEMPERATURE,
            38.7f,
            1,
            Unit.PERCENT,
            Calendar.getInstance().time))
    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnable = Runnable { sendObservations() }

    private val ghsService: GenericHealthSensorService?
        get() { return GenericHealthSensorService.getInstance() }


    init {
        ghsService?.addListener(this)
    }

    fun addObservation(handle: Int, type: ObservationType, value: Float, precision: Int, unit: Unit) {
        observations.add(
                SimpleNumericObservation(handle.toShort(),
                        type,
                        value,
                        precision,
                        unit,
                        Calendar.getInstance().time))
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
            ghsService?.sendObservation(it)
            handler.postDelayed(notifyRunnable, 5000)
        }
    }

    // ServiceListener interface methods

    // Someone has connected so we need to listen to the GHS service for events
    override fun onConnected(numberOfConnections: Int) {
        ghsService?.addListener(this)
    }

    // If no connections then we no longer need to listen for events
    override fun onDisconnected(numberOfConnections: Int) {
        if (numberOfConnections == 0) {
            // Just to be extra safe do a stop
            stopEmitter()
            ghsService?.removeListener(this)
        }
    }

    // If someone is listening for observation notifies then start emitting them
    override fun onNotifyingEnabled(characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == GenericHealthSensorService.OBSERVATION_CHARACTERISTIC_UUID) {
            startEmitter()
        }
    }

    // If stopped listening for observation notifies then stop emitting them
    override fun onNotifyingDisabled(ccharacteristic: BluetoothGattCharacteristic) {
        stopEmitter()
    }

    override fun onCharacteristicRead(characteristic: BluetoothGattCharacteristic) {}

    override fun onCharacteristicWrite(characteristic: BluetoothGattCharacteristic, value: ByteArray) {}

    override fun onDescriptorRead(descriptor: BluetoothGattDescriptor) {}

    override fun onDescriptorWrite(descriptor: BluetoothGattDescriptor, value: ByteArray) {}

}