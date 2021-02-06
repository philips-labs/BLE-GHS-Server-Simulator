package com.welie.btserver

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.welie.btserver.extensions.asHexString
import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

class PeripheralManager(context: Context, bluetoothManager: BluetoothManager, callback: PeripheralManagerCallback) {
    private val context: Context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager: BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter
    private val bluetoothLeAdvertiser: BluetoothLeAdvertiser
    private lateinit var bluetoothGattServer: BluetoothGattServer
    private val callback: PeripheralManagerCallback
    private val commandQueue: Queue<Runnable> = ConcurrentLinkedQueue()
    private val writeLongTemporaryBytes = ConcurrentHashMap<BluetoothGattCharacteristic, ByteArray>()

    @Volatile
    private var commandQueueBusy = false
    private val connectedCentralsMap: MutableMap<String, Central> = ConcurrentHashMap()

    private val bluetoothGattServerCallback: BluetoothGattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Call connect() even though we are already connected
                    // It basically tells Android we will really use this connection
                    // If we don't do this, then cancelConnection won't work
                    // See https://issuetracker.google.com/issues/37127644
                    if (connectedCentralsMap.containsKey(device.address)) {
                        return
                    } else {
                        // This will lead to onConnectionStateChange be called again
                        bluetoothGattServer.connect(device, false)
                    }
                    handleDeviceConnected(device)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Deal is double disconnect messages
                    if (!connectedCentralsMap.containsKey(device.address)) return
                    handleDeviceDisconnected(device)
                }
            } else {
                Timber.i("Device '%s' disconnected with status %d", device.name, status)
                handleDeviceDisconnected(device)
            }
        }

        private fun handleDeviceConnected(device: BluetoothDevice) {
            Timber.i("Device '%s' connected", device.name)
            val central = Central(device.address, device.name)
            connectedCentralsMap[central.address] = central
            mainHandler.post { callback.onCentralConnected(central) }
        }

        private fun handleDeviceDisconnected(device: BluetoothDevice) {
            Timber.i("Device '%s' disconnected", device.name)
            val central = getCentral(device)
            if (central != null) {
                mainHandler.post { callback.onCentralDisconnected(central) }
            }
            removeCentral(device)
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            mainHandler.post { callback.onServiceAdded(status, service) }
            completedCommand()
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            Timber.i("read request for characteristic <%s> with offset %d", characteristic.uuid, offset)
            mainHandler.post {
                val central = getCentral(device)
                if (central != null) {
                    if (offset == 0) {
                        callback.onCharacteristicRead(central, characteristic)
                    }

                    // If data is longer than MTU - 1, cut the array. Only ATT_MTU - 1 bytes can be sent in Long Read.
                    val value = copyOf(nonnullOf(characteristic.value), offset, central.currentMtu - 1)
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }
        }

        private fun copyOf(source: ByteArray, offset: Int, maxSize: Int): ByteArray {
            if (source.size > maxSize) {
                val chunkSize = Math.min(source.size - offset, maxSize)
                val result = ByteArray(chunkSize)
                System.arraycopy(source, offset, result, 0, chunkSize)
                return result
            }
            return Arrays.copyOf(source, source.size)
        }


        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            Timber.i("write %s request <%s> offset %d for <%s>", if (responseNeeded) "WITH_RESPONSE" else "WITHOUT_RESPONSE", value.asHexString(), offset, characteristic.uuid)
            val safeValue = nonnullOf(value)
            mainHandler.post {
                val central = getCentral(device)
                if (central != null) {
                    var status = GattStatus.SUCCESS
                    if (!preparedWrite) {
                        status = callback.onCharacteristicWrite(central, characteristic, safeValue)
                        if (status === GattStatus.SUCCESS) {
                            characteristic.value = safeValue
                        }
                    } else {
                        if (offset == 0) {
                            writeLongTemporaryBytes[characteristic] = safeValue
                        } else {
                            val temporaryBytes = writeLongTemporaryBytes[characteristic]
                            if (temporaryBytes != null && offset == temporaryBytes.size) {
                                writeLongTemporaryBytes[characteristic] = temporaryBytes +  value
                            } else {
                                status = GattStatus.INVALID_OFFSET
                            }
                        }
                    }
                    if (responseNeeded) {
                        bluetoothGattServer.sendResponse(device, requestId, status.value, offset, safeValue)
                    }
                }
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            Timber.i("read request for descriptor <%s>", descriptor.uuid)
            mainHandler.post {
                val central = getCentral(device)
                if (central != null) {
                    if (offset == 0) {
                        callback.onDescriptorRead(central, descriptor)
                    }

                    // If data is longer than MTU - 1, cut the array. Only ATT_MTU - 1 bytes can be sent in Long Read.
                    val value = copyOf(nonnullOf(descriptor.value), offset, central.currentMtu - 1)
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val safeValue = nonnullOf(value)
            val characteristic = Objects.requireNonNull(descriptor.characteristic, "Descriptor does not have characteristic")

            mainHandler.post {
                val central = getCentral(device)
                if (central != null) {
                    val status: GattStatus
                    status = if (descriptor.uuid == CCC_DESCRIPTOR_UUID) {
                        checkCccDescriptorValue(safeValue, characteristic)
                    } else {
                        // Ask callback if value is ok or not
                        Timber.i("write request for descriptor <%s>", descriptor.uuid)
                        callback.onDescriptorWrite(central, descriptor, safeValue)
                    }
                    if (status === GattStatus.SUCCESS) {
                        descriptor.value = safeValue
                    }
                    if (responseNeeded) {
                        bluetoothGattServer.sendResponse(device, requestId, status.value, 0, null)
                    }
                    if (status === GattStatus.SUCCESS && descriptor.uuid == CCC_DESCRIPTOR_UUID) {
                        if (Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                                || Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                            Timber.i("notifying enabled for <%s>", characteristic.uuid)
                            callback.onNotifyingEnabled(central, characteristic)
                        } else {
                            Timber.i("notifying disabled for <%s>", characteristic.uuid)
                            callback.onNotifyingDisabled(central, characteristic)
                        }
                    }
                }
            }
        }

        // Check value to see if it is valid and if matches the characteristic properties
        private fun checkCccDescriptorValue(safeValue: ByteArray, characteristic: BluetoothGattCharacteristic): GattStatus {
            var status = GattStatus.SUCCESS
            if (safeValue.size != 2) {
                status = GattStatus.INVALID_ATTRIBUTE_VALUE_LENGTH
            } else if (!(Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                            || Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            || Arrays.equals(safeValue, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))) {
                status = GattStatus.REQUEST_NOT_SUPPORTED
            } else if (!supportsIndicate(characteristic) && Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                status = GattStatus.REQUEST_NOT_SUPPORTED
            } else if (!supportsNotify(characteristic) && Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                status = GattStatus.REQUEST_NOT_SUPPORTED
            }
            return status
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            if (execute) {
                mainHandler.post {
                    var status = GattStatus.SUCCESS
                    val central = getCentral(device)
                    if (central != null) {
                        val characteristic = writeLongTemporaryBytes.keys.iterator().next()
                        if (characteristic != null) {
                            status = callback.onCharacteristicWrite(central, characteristic, writeLongTemporaryBytes[characteristic]!!)
                            if (status === GattStatus.SUCCESS) {
                                characteristic.value = writeLongTemporaryBytes[characteristic]
                            }
                        }
                    }
                    writeLongTemporaryBytes.clear()
                    bluetoothGattServer.sendResponse(device, requestId, status.value, 0, null)
                }
            } else {
                // Long write was cancelled
                writeLongTemporaryBytes.clear()
                bluetoothGattServer.sendResponse(device, requestId, GattStatus.SUCCESS.value, 0, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            completedCommand()
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Timber.i("new MTU: %d", mtu)
            val central = getCentral(device)
            central?.currentMtu = mtu
        }

        override fun onPhyUpdate(device: BluetoothDevice, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(device, txPhy, rxPhy, status)
        }

        override fun onPhyRead(device: BluetoothDevice, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(device, txPhy, rxPhy, status)
        }
    }
    private val advertiseCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Timber.i("Advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Timber.i("Advertising failed")
        }
    }

    fun close() {
        bluetoothGattServer.close()
    }

    fun startAdvertising(settings: AdvertiseSettings?, advertiseData: AdvertiseData?, scanResponse: AdvertiseData?) {
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            Timber.e("device does not support advertising")
        } else {
            bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
        }
    }

    fun stopAdvertising() {
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
    }

    fun add(service: BluetoothGattService): Boolean {
        Objects.requireNonNull(service, SERVICE_IS_NULL)
        val result = commandQueue.add(Runnable {
            if (!bluetoothGattServer.addService(service)) {
                Timber.e("adding service %s failed", service.uuid)
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue add service command")
        }
        return result
    }

    fun remove(service: BluetoothGattService): Boolean {
        Objects.requireNonNull(service, SERVICE_IS_NULL)
        bluetoothGattServer.removeService(service)
        return true
    }

    fun removeAllServices() {
        bluetoothGattServer.clearServices()
    }

    val services: List<BluetoothGattService>
        get() = bluetoothGattServer.services

    val minimalMTU: Int
        get() = min((getConnectedCentrals().minOfOrNull { it.currentMtu } ?: MAX_MIN_MTU), MAX_MIN_MTU)

    /**
     * Notify all Centrals that a characteristic has changed
     *
     * @param characteristic the characteristic for which to send a notification
     * @return true if the operation was enqueued, otherwise false
     */
    fun notifyCharacteristicChanged(value: ByteArray, characteristic: BluetoothGattCharacteristic): Boolean {
        Objects.requireNonNull(characteristic, CHARACTERISTIC_IS_NULL)
        if (doesNotSupportNotifying(characteristic)) return false
        var result = true

        for (device in connectedDevices) {
            if (!notifyCharacteristicChanged(device, copyOf(value), characteristic)) {
                result = false
            }
        }
        return result
    }



    private fun notifyCharacteristicChanged(bluetoothDevice: BluetoothDevice, value: ByteArray, characteristic: BluetoothGattCharacteristic): Boolean {
        Objects.requireNonNull(characteristic.value, CHARACTERISTIC_VALUE_IS_NULL)

        if (doesNotSupportNotifying(characteristic)) return false
        val confirm = supportsIndicate(characteristic)
        val result = commandQueue.add(Runnable {
            characteristic.value = value
            if (!bluetoothGattServer.notifyCharacteristicChanged(bluetoothDevice, characteristic, confirm)) {
                Timber.e("notifying characteristic changed failed for <%s>", characteristic.uuid)
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue notify command")
        }
        return result
    }

    fun cancelConnection(central: Central) {
        Objects.requireNonNull(central, CENTRAL_IS_NULL)
        cancelConnection(bluetoothAdapter.getRemoteDevice(central.address))
    }

    private fun cancelConnection(bluetoothDevice: BluetoothDevice) {
        Objects.requireNonNull(bluetoothDevice, DEVICE_IS_NULL)
        Timber.i("cancelConnection with '%s' (%s)", bluetoothDevice.name, bluetoothDevice.address)
        bluetoothGattServer.cancelConnection(bluetoothDevice)
    }

    private val connectedDevices: List<BluetoothDevice>
        get() = bluetoothManager.getConnectedDevices(BluetoothGattServer.GATT)

    fun getConnectedCentrals(): Set<Central> {
        val centrals: Set<Central> = HashSet(connectedCentralsMap.values)
        return Collections.unmodifiableSet(centrals)
    }

    /**
     * The current command has been completed, move to the next command in the queue (if any)
     */
    private fun completedCommand() {
        commandQueue.poll()
        commandQueueBusy = false
        nextCommand()
    }

    /**
     * Execute the next command in the subscribe queue.
     * A queue is used because the calls have to be executed sequentially.
     * If the read or write fails, the next command in the queue is executed.
     */
    private fun nextCommand() {
        synchronized(this) {

            // If there is still a command being executed, then bail out
            if (commandQueueBusy) return

            // Check if there is something to do at all
            val bluetoothCommand = commandQueue.peek() ?: return

            // Execute the next command in the queue
            commandQueueBusy = true
            mainHandler.post {
                try {
                    bluetoothCommand.run()
                } catch (ex: Exception) {
                    Timber.e(ex, "command exception")
                    completedCommand()
                }
            }
        }
    }

    private fun getCentral(device: BluetoothDevice): Central? {
        Objects.requireNonNull(device, DEVICE_IS_NULL)
        return connectedCentralsMap[device.address]
    }

    private fun removeCentral(device: BluetoothDevice) {
        Objects.requireNonNull(device, DEVICE_IS_NULL)
        connectedCentralsMap.remove(device.address)
    }

    /**
     * Make a byte array nonnull by either returning the original byte array if non-null or an empty bytearray
     *
     * @param source byte array to make nonnull
     * @return the source byte array or an empty array if source was null
     */
    private fun nonnullOf(source: ByteArray?): ByteArray {
        return source ?: ByteArray(0)
    }

    fun copyOf(source: ByteArray?): ByteArray {
        return if (source == null) ByteArray(0) else Arrays.copyOf(source, source.size)
    }

    private fun supportsNotify(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0
    }

    private fun supportsIndicate(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0
    }

    private fun doesNotSupportNotifying(characteristic: BluetoothGattCharacteristic): Boolean {
        return !(supportsIndicate(characteristic) || supportsNotify(characteristic))
    }

    companion object {
        val CUD_DESCRIPTOR_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Error strings when nullability checks fail
        private const val CONTEXT_IS_NULL = "Context is null"
        private const val BLUETOOTH_MANAGER_IS_NULL = "BluetoothManager is null"
        private const val SERVICE_IS_NULL = "service is null"
        private const val CHARACTERISTIC_IS_NULL = "Characteristic is null"
        private const val DEVICE_IS_NULL = "Device is null"
        private const val CHARACTERISTIC_VALUE_IS_NULL = "Characteristic value is null"
        private const val CENTRAL_IS_NULL = "Central is null"

        private const val MAX_MIN_MTU = 23
    }

    init {
        this.context = Objects.requireNonNull(context, CONTEXT_IS_NULL)
        this.callback = Objects.requireNonNull(callback, "Callback is null")
        this.bluetoothManager = Objects.requireNonNull(bluetoothManager, BLUETOOTH_MANAGER_IS_NULL)
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothGattServer = bluetoothManager.openGattServer(context, bluetoothGattServerCallback)
        Timber.i("Current advertising %d services", services.size)
    }
}