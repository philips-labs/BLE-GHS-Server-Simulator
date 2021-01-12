package com.welie.btserver

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Handler
import android.os.Looper
import com.welie.btserver.BluetoothBytesParser.Companion.FORMAT_FLOAT
import com.welie.btserver.ObservationType
import com.welie.btserver.extensions.asHexString
import com.welie.btserver.extensions.convertHexStringtoByteArray
import timber.log.Timber
import java.util.*

internal class GenericHealthSensorService(peripheralManager: PeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(GHS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    private val observationCharacteristic = BluetoothGattCharacteristic(OBSERVATION_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
    private val controlCharacteristic = BluetoothGattCharacteristic(CONTROL_POINT_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_WRITE)

    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnable = Runnable { sendObservations() }

    override fun onCentralDisconnected(central: Central) {
        if (noCentralsConnected()) {
            stopNotifying()
        }
    }

    override fun onNotifyingEnabled(central: Central, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == OBSERVATION_CHARACTERISTIC_UUID) {
            sendObservations()
        }
    }

    override fun onNotifyingDisabled(central: Central, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == OBSERVATION_CHARACTERISTIC_UUID) {
            stopNotifying()
        }
    }

    private fun stopNotifying() {
        handler.removeCallbacks(notifyRunnable)
        observationCharacteristic.getDescriptor(PeripheralManager.CCC_DESCRIPTOR_UUID).value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
    }

    // Right now not handling > 63 segment wrap around
    private fun sendBytes(bytes: ByteArray) {
        val segmentSize = minimalMTU - 4
        val numSegs = Math.ceil((bytes.size / segmentSize.toFloat()).toDouble()).toInt()
        for (i in 0 until numSegs) {

            // Compute the segment header byte (first/last seg, seg number)
            val segmentNumber = i + 1
            var segByte = segmentNumber shl 2
            segByte = segByte or if (segmentNumber == 1) 0x01 else 0x0
            segByte = segByte or if (segmentNumber == numSegs) 0x02 else 0x0

            // Get the next segment data
            val startIndex = i * segmentSize
            val endIndex = Math.min(startIndex + segmentSize, bytes.size - 1)
            val length = endIndex - startIndex
            val segment = ByteArray(length + 1)
            val segmentData = Arrays.copyOfRange(bytes, startIndex, endIndex)
            segment[0] = segByte.toByte()
            System.arraycopy(segmentData, 0, segment, 1, length)

            // Send segment
            Timber.i("Sending <%s>", segment.asHexString())
            observationCharacteristic.value = segment
            notifyCharacteristicChanged(observationCharacteristic)
        }
    }

    private fun sendObservations() {
        val observation = SimpleNumericObservation(1.toShort(), ObservationType.ORAL_TEMPERATURE, 38.7f, 1, Unit.PERCENT, Calendar.getInstance().time)
        Timber.i("Value ${observation.serialize().asHexString()}")
        sendBytes(observation.serialize())
        handler.postDelayed(notifyRunnable, 5000)
    }

    private val minimalMTU: Int
        private get() {
            var minMTU = 512
            for (central in peripheralManager.getConnectedCentrals()) {
                if (central.currentMtu < minMTU) {
                    minMTU = central.currentMtu
                }
            }
            return minMTU
        }

    companion object {
        private val GHS_SERVICE_UUID = UUID.fromString("0000183D-0000-1000-8000-00805f9b34fb")
        private val OBSERVATION_CHARACTERISTIC_UUID = UUID.fromString("00002AC4-0000-1000-8000-00805f9b34fb")
        private val CONTROL_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002AC6-0000-1000-8000-00805f9b34fb")
        private const val OBSERVATION_DESCRIPTION = "Characteristic for ACOM Observation segments."
        private const val CONTROL_POINT_DESCRIPTION = "Control point for generic health sensor."
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

        val bytes : ByteArray = "00010921000200010001092F00040002E00800010A560004FF0001830001099600040004022000010990000800000176F68D0161".convertHexStringtoByteArray()
        val test = SimpleNumericObservation.deserialize(bytes)
        Timber.i("$test")
    }
}