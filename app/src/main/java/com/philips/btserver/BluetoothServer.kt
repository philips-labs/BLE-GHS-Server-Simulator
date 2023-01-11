/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.philips.btserver.extensions.asFormattedHexString
import com.philips.btserver.gatt.DeviceInformationService
import com.philips.btserver.generichealthservice.*
import com.philips.btserver.generichealthservice.ElapsedTimeService
import com.philips.btserver.observations.ObservationStore
import com.philips.btserver.observations.ObservationStoreListener
import com.philips.btserver.userdataservice.UserDataService
import com.philips.btserverapp.AppLogTree
import com.welie.blessed.*
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.util.*

interface BluetoothServerConnectionListener {
    fun onCentralConnected(central: BluetoothCentral)
    fun onCentralDisconnected(central: BluetoothCentral)
}

interface BluetoothServerAdvertisingListener {
    fun onStartAdvertising()
    fun onStopAdvertising()
}

@RequiresApi(Build.VERSION_CODES.O)
internal class BluetoothServer(val context: Context) : ObservationStoreListener {

    var bluetoothAdapter: BluetoothAdapter
    var bluetoothManager: BluetoothManager

    private val peripheralManager: BluetoothPeripheralManager

    // Listeners for central connect/disconnects
    private val connectionListeners = mutableListOf<BluetoothServerConnectionListener>()

    private var advertUserIdByteArray = byteArrayOf()

    fun isCentralBonded(central: BluetoothCentral): Boolean {
        return getBondedDevices().map { it.address }.contains(central.address)
    }

    fun getBondedDevices(): Set<BluetoothDevice> {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) { return emptySet() }
        return bluetoothAdapter.bondedDevices
    }

    private val peripheralManagerCallback: BluetoothPeripheralManagerCallback = object : BluetoothPeripheralManagerCallback() {
        override fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse {
            return serviceImplementations[characteristic.service]?.onCharacteristicRead(central, characteristic) ?: ReadResponse(GattStatus.REQUEST_NOT_SUPPORTED, byteArrayOf())
        }

        override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
            return serviceImplementations[characteristic.service]?.onCharacteristicWrite(central, characteristic, value)
                    ?: GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onCharacteristicWriteCompleted(
            bluetoothCentral: BluetoothCentral,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray) {
            serviceImplementations[characteristic.service]?.onCharacteristicWriteCompleted(bluetoothCentral, characteristic, value)
        }

        override fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor): ReadResponse {
            return serviceImplementations[descriptor.characteristic.service]?.onDescriptorRead(central, descriptor) ?: ReadResponse(GattStatus.REQUEST_NOT_SUPPORTED, byteArrayOf())
        }

        override fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
            return serviceImplementations[descriptor.characteristic.service]?.onDescriptorWrite(central, descriptor, value)
                    ?: GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
            serviceImplementations[characteristic.service]?.onNotifyingEnabled(central, characteristic)
        }

        override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
            serviceImplementations[characteristic.service]?.onNotifyingDisabled(central, characteristic)
        }

        override fun onNotificationSent(
            bluetoothCentral: BluetoothCentral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            serviceImplementations[characteristic.service]?.onNotificationSent(bluetoothCentral,
                value,
                characteristic,
                status)
        }

        val disconnectedBondedCentrals = mutableListOf<BluetoothCentral>()

        override fun onCentralConnected(central: BluetoothCentral) {
            (connectionListeners + serviceImplementations.values).forEach { it.onCentralConnected(central) }
            if(isCentralBonded(central)) disconnectedBondedCentrals.remove(central)
        }

        override fun onCentralDisconnected(central: BluetoothCentral) {
            (connectionListeners + serviceImplementations.values).forEach { it.onCentralDisconnected(central) }
            if(isCentralBonded(central)) disconnectedBondedCentrals.add(central)
        }

        override fun onAdvertisingStarted(settingsInEffect: AdvertiseSettings) {
            Timber.i("onAdvertisingStarted")
        }

        override fun onAdvertiseFailure(advertiseError: AdvertiseError) {
            Timber.i("onAdvertiseFailure")
        }

        override fun onAdvertisingStopped() {
            Timber.i("onAdvertisingStopped")
        }

    }

    fun addConnectionListener(connectionListner: BluetoothServerConnectionListener) {
        connectionListeners.add(connectionListner)
    }

    fun removeConnectionListener(connectionListner: BluetoothServerConnectionListener) {
        connectionListeners.remove(connectionListner)
    }

    fun numberOfCentralsConnected(): Int {
        return peripheralManager.getConnectedCentrals().size
    }

    // TODO Add the GHS advert data with (5.6.1)
    // Bytes from Brian when doing this TODO
    /*
        02 01 02 // flags
        0F 09 49 4E 32 30 31 33 2D 47 48 53 2D 53 49 4D //name
        02 0A F5 //tx power
        03 03 44 7F // service uuid
        06 16 44 7F 06 10 00 //service data -ECG - no pairing
     */
    fun startAdvertising() {
        val serviceUUID = GenericHealthSensorService.GHS_SERVICE_UUID
        val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
        val advertiseData = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(ParcelUuid(serviceUUID))
                .addServiceUuid(ParcelUuid(DeviceInformationService.DIS_SERVICE_UUID))
                .addServiceUuid(ParcelUuid(UserDataService.USER_DATA_SERVICE_UUID))
                .addServiceData(ParcelUuid(serviceUUID), getGHSAdvertBytes())
                .build()
        val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()
        peripheralManager?.stopAdvertising()
        peripheralManager?.startAdvertising(advertiseSettings, advertiseData, scanResponse)
    }

    fun stopAdvertising() {
        peripheralManager?.stopAdvertising()
    }

    private fun getGHSAdvertBytes(): ByteArray {
        // get the first 2 supported specializations
        val devspecs = DeviceSpecialization.values().take(2) //.asAdvertisementDataBytes()
        var devspecBytes = byteArrayOf() 
        for( devspec in devspecs) devspecBytes = devspecBytes + devspec.asAdvertisementDataBytes()
        Timber.i("Supported device specializations: ${devspecBytes.asFormattedHexString()}")

        // get the first 2 users with new observations
        val users = ObservationStore.usersWithTemporaryStoredObservations// .take(2)
        var userBytes = byteArrayOf()
        for( user in users) userBytes = userBytes + user.toByte()
        Timber.i("Users with new observations: ${userBytes.asFormattedHexString()}")

        val ADdata = byteArrayOf(devspecs.count().toByte()) + devspecBytes + byteArrayOf(users.count().toByte()) + userBytes
        Timber.i("Advert Data Bytes: ${ADdata.asFormattedHexString()}")
        return ADdata
    }

    private fun setupServices() {
        serviceClasses().forEach {
            serviceImplementations[it.service] = it
            peripheralManager.add(it.service)
        }
    }

    private fun serviceClasses(): List<BaseService> {
        return listOf(
            DeviceInformationService(peripheralManager),
            GenericHealthSensorService(peripheralManager),
            UserDataService(peripheralManager),
            ElapsedTimeService(peripheralManager),
            ReconnectionConfigurationService(peripheralManager)
        )
    }

    fun getServiceWithUUID(serviceUUID: UUID): BaseService? {
        return serviceImplementations.entries.find { it.key.uuid == serviceUUID }?.value
    }

    private val serviceImplementations = HashMap<BluetoothGattService, BaseService>()

    companion object {
        private var instance: BluetoothServer? = null

        fun getInstance(): BluetoothServer? {
            return instance
        }

        @Synchronized
        fun getInstance(context: Context): BluetoothServer? {
            if (instance == null) {
                instance = BluetoothServer(context.applicationContext)
            }
            return instance
        }
    }

    fun getAdvertisingName(): String {
        return try {
            bluetoothAdapter.name
        } catch (e: SecurityException) {
            Timber.i("Security Exception in getting BT adapter name. Check permission logic")
            "Can't get name"
        }
    }

    fun setAdvertisingName(advName: String) {
        try {
            bluetoothAdapter.name = advName
            // TODO: cycle advertising to see if this updates all the ad fields, lengths... see if this is needed
            stopAdvertising()
            startAdvertising()
        } catch (e: SecurityException) {
            Timber.i("Security Exception in setting BT adapter name. Check permission logic")
        }
    }

    init {

        // Plant a tree
        Timber.plant(DebugTree())
        Timber.plant(AppLogTree())
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            Timber.e("not supporting advertising")
        }
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        }
        setAdvertisingName("GHS-${Build.MODEL}")
        peripheralManager = BluetoothPeripheralManager(context, bluetoothManager, peripheralManagerCallback)
        peripheralManager.removeAllServices()

        ObservationStore.addListener(this)

        setupServices()


        // stuff to add a characteristic to the Generic Attribute Service - parked for now....
/*
        val GENERIC_ATTRIBUTE_SERVICE = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
        val LE_GATT_SECURITY_LEVELS_CHARACTERISTIC = UUID.fromString("00002BF5-0000-1000-8000-00805f9b34fb")
        val securityLevelCharacteristic = BluetoothGattCharacteristic(
            LE_GATT_SECURITY_LEVELS_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_READ,
            0
        )
        var genericAttributeService : BluetoothGattService
        var gattServer : BluetoothGattServer
        val bgsc : BluetoothGattServerCallback // only need to add something for reading the new characteristic
        gattServer = bluetoothManager.openGattServer(context, bgsc)

        genericAttributeService = gattServer.getService(GENERIC_ATTRIBUTE_SERVICE)
        genericAttributeService.addCharacteristic(securityLevelCharacteristic)
*/
        startAdvertising()
    }

    override fun observationStoreUserChanged() {
        startAdvertising()
    }
}

/**
 *
 * @return the receiver converted into a ByteArray with each byte
 */
fun List<Int>.asByteArray(): ByteArray {
    val result = ByteArray(this.size)
    var i = 0
    this.iterator().forEach {
        result[i] = it.toByte()
        i++
    }
    return result
}
