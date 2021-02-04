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

    var emitterPeriod = 10
    /*
     * If mergeObservations true observations are sent as one ACOM byte array.
     * false means send each observation as a sepeate ACOM Object
     */
    // If false, e
    var mergeObservations = true

    val observations = mutableListOf<Observation>()
    private val handler = Handler(Looper.myLooper())
    private val notifyRunnable = Runnable { sendObservations() }

    private var lastHandle = 1

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

    fun removeObservationType(type: ObservationType) {
        observations.removeAll { it.type == type }
    }

    // Observation helper methods
    fun addHRObservation(value: Float) {
        addObservation(lastHandle, ObservationType.MDC_ECG_HEART_RATE, value, 0, Unit.MDC_DIM_BEAT_PER_MIN)
        lastHandle++
    }

    fun addBodyTempObservation(value: Float) {
        addObservation(lastHandle, ObservationType.MDC_TEMP_BODY, value, 1, Unit.MDC_DIM_DEGC)
        lastHandle++
    }

    fun addPPGObservation(value: ByteArray) {
        observations.add(
                SampleArrayObservation(lastHandle.toShort(),
                        ObservationType.MDC_PPG_TIME_PD_PP,
                        value,
                        Unit.MDC_DIM_INTL_UNIT,
                        Calendar.getInstance().time))
        lastHandle++
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
        Timber.i("Emitting ${observations.size} observations")
        kotlin.random.Random.nextInt(0, 100)
        if (mergeObservations) {
            ghsService?.sendObservations(observations)
        } else {
            observations.forEach { ghsService?.sendObservation(it) }
        }
        handler.postDelayed(notifyRunnable, (emitterPeriod * 1000).toLong())
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