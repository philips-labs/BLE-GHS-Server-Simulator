package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.asBLEDataSegments
import com.philips.btserver.extensions.asHexString
import com.philips.btserver.extensions.merge
import timber.log.Timber
import java.util.*

internal class GenericHealthSensorService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {
    private val handler = Handler(Looper.getMainLooper())

    override val service = BluetoothGattService(GHS_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
    private val observationCharacteristic = BluetoothGattCharacteristic(OBSERVATION_CHARACTERISTIC_UUID,
            PROPERTY_NOTIFY,
            0)
    private val controlCharacteristic = BluetoothGattCharacteristic(
            CONTROL_POINT_CHARACTERISTIC_UUID,
            PROPERTY_WRITE or PROPERTY_INDICATE,
            PERMISSION_WRITE)

    override fun onCentralDisconnected(central: BluetoothCentral) {
        super.onCentralDisconnected(central)
        if (noCentralsConnected()) {
            stopNotifying()
        }
    }

    override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        super.onNotifyingDisabled(central, characteristic)
        if (characteristic.uuid == OBSERVATION_CHARACTERISTIC_UUID) {
            stopNotifying()
        }
    }

    private fun stopNotifying() {
        observationCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID).value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
    }

    fun sendObservation(observation: Observation) {
        sendBytesInSegments(observation.serialize())
    }

    fun sendObservations(observations: Collection<Observation>) {
        sendBytesInSegments(observations.map { it.serialize() }.merge())
    }

    private fun sendBytesInSegments(bytes: ByteArray) {
        bytes.asBLEDataSegments(minimalMTU - 4).forEach { it.sendSegment() }
    }

    companion object {
        val GHS_SERVICE_UUID = UUID.fromString("0000183D-0000-1000-8000-00805f9b34fb")
        val OBSERVATION_CHARACTERISTIC_UUID = UUID.fromString("00002AC4-0000-1000-8000-00805f9b34fb")
        val CONTROL_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002AC6-0000-1000-8000-00805f9b34fb")
        private const val OBSERVATION_DESCRIPTION = "Characteristic for ACOM Observation segments."
        private const val CONTROL_POINT_DESCRIPTION = "Control point for generic health sensor."

        // If the BluetoothService has a running GHS service then return it
        fun getInstance(): GenericHealthSensorService? {
            val bleServer = BluetoothServer.getInstance()
            val ghs = bleServer?.getServiceWithUUID(GHS_SERVICE_UUID)
            return  ghs?.let {it as GenericHealthSensorService }
        }
    }

    init {
        service.addCharacteristic(observationCharacteristic)
        service.addCharacteristic(controlCharacteristic)
        observationCharacteristic.addDescriptor(getCccDescriptor())
        observationCharacteristic.addDescriptor(getCudDescriptor(OBSERVATION_DESCRIPTION))
        observationCharacteristic.value = byteArrayOf(0x00)
        controlCharacteristic.addDescriptor(getCccDescriptor())
        controlCharacteristic.addDescriptor(getCudDescriptor(CONTROL_POINT_DESCRIPTION))
        controlCharacteristic.value = byteArrayOf(0x00)
    }

    fun ByteArray.sendSegment() {
        Timber.i("Sending <%s>", this.asHexString())
        observationCharacteristic.value = this
        notifyCharacteristicChanged(this, observationCharacteristic)
    }

}