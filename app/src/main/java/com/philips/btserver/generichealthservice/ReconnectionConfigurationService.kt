package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.asFormattedHexString
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import timber.log.Timber
import java.io.WriteAbortedException
import java.util.*

class ReconnectionConfigurationService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {

    override val service = BluetoothGattService(ReconnectionConfigurationService.RCS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

    private val rcFeaturesCharacteristic = BluetoothGattCharacteristic(
        RC_FEATURE_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ ,
        BluetoothGattCharacteristic.PERMISSION_READ
    )

    private val rcSettingsCharacteristic = BluetoothGattCharacteristic(
        RC_SETTINGS_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    )

    private val rcCP = BluetoothGattCharacteristic(
        RECONNECTION_CONFIGURATION_CONTROL_POINT_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    init {
        service.addCharacteristic(rcFeaturesCharacteristic)
        service.addCharacteristic(rcSettingsCharacteristic)
        rcSettingsCharacteristic.addDescriptor(getCccDescriptor())
        service.addCharacteristic(rcCP)
        rcCP.addDescriptor(getCccDescriptor())
    }

    /*
    Feature bits:
     */
    val E2E_CRCSupported = 1 shl 0
    val EnableDisconnectSupported = 1 shl 1
    val ReadyforDisconnectSupported = 1 shl 2
    val ProposeReconnectionTimeoutSupported= 1 shl 3
    val ProposeConnectionIntervalSupported = 1 shl 4
    val ProposeSlaveLatencySupported = 1 shl 5
    val ProposeSupervisionTimeoutSupported = 1 shl 6
    val ProposeAdvertisementIntervalSupported= 1 shl 7

    val ProposeAdvertisementCountSupported = 1 shl 8
    val ProposeAdvertisementRepetitionTimeSupported = 1 shl 9
    val AdvertisementConfiguration1Supported = 1 shl 10
    val AdvertisementConfiguration2Supported = 1 shl 11
    val AdvertisementConfiguration3Supported = 1 shl 12
    val AdvertisementConfiguration4Supported = 1 shl 13
    val UpgradetoLESCOnlySupported = 1 shl 14
    val NextPairingOOBSupported = 1 shl 15
    val UseofWhiteListSupported = 1 shl 16

    val LimitedAccessSupported = 1 shl 17
    val FeatureExtension = 1 shl 23

    var RCFeatures = EnableDisconnectSupported or ReadyforDisconnectSupported

    /*
    Setting bits
     */
    val LESC_Only = 1 shl 1
    val UseOOBPairing = 1 shl 2
    val ReadyForDisconnect = 1 shl 4
    val LimitedAccess = 1 shl 5
    val AccessPermitted = 1 shl 6
    val AdvertisementMode0 = 1 shl 8
    val AdvertisementMode1 = 1 shl 9

    var RCSettingsField = AdvertisementMode0
    val RCSettingsLength = 3

    // Notification from [central] that [characteristic] has notification enabled.
    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        when (characteristic.uuid) {
            RC_SETTINGS_CHARACTERISTIC_UUID -> Timber.i("RC Settings notifying enabled")
            RECONNECTION_CONFIGURATION_CONTROL_POINT_CHARACTERISTIC_UUID -> Timber.i("RCCP notifying enabled")
            else -> super.onNotifyingEnabled(central, characteristic)
        }
    }

    /*
     * onCharacteristicRead is a non-abstract method with an empty body to have a default behavior to do nothing
     */
    override fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) : ReadResponse {
        when (characteristic.uuid) {
            RC_FEATURE_CHARACTERISTIC_UUID -> return ReadResponse(GattStatus.SUCCESS, byteArrayOf(0xff.toByte(), 0xff.toByte(), RCFeatures.toByte(), (RCFeatures shr 8).toByte(), (RCFeatures shr 16).toByte()))
            RC_SETTINGS_CHARACTERISTIC_UUID -> return ReadResponse(GattStatus.SUCCESS, byteArrayOf(RCSettingsLength.toByte(), RCSettingsField.toByte(), (RCSettingsField shr 8).toByte()))
            else -> return super.onCharacteristicRead(central, characteristic)
        }
    }

    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        //Timber.i("onCharacteristicWrite with Bytes: ${value.asFormattedHexString()}")
        when (characteristic.uuid) {
            RECONNECTION_CONFIGURATION_CONTROL_POINT_CHARACTERISTIC_UUID -> {
                Timber.i("RCCP write")
                return GattStatus.SUCCESS
            }
            else -> return super.onCharacteristicWrite(central, characteristic, value)
        }
    }

    companion object {
        val RCS_SERVICE_UUID = UUID.fromString("00001829-0000-1000-8000-00805f9b34fb")
        val RC_FEATURE_CHARACTERISTIC_UUID =
            UUID.fromString("00002B1D-0000-1000-8000-00805f9b34fb")
        private const val RC_FEATURE_DESCRIPTION = "RC_FEATURE Characteristic"
        val RC_SETTINGS_CHARACTERISTIC_UUID =
            UUID.fromString("00002B1E-0000-1000-8000-00805f9b34fb")
        private const val RC_SETTINGS_DESCRIPTION = "RC_SETTINGS Characteristic"
        val RECONNECTION_CONFIGURATION_CONTROL_POINT_CHARACTERISTIC_UUID =
            UUID.fromString("00002B1F-0000-1000-8000-00805f9b34fb")
        private const val RECONNECTION_CONFIGURATION_CONTROL_POINT_DESCRIPTION = "RECONNECTION_CONFIGURATION_CONTROL_POINT Characteristic"

        /**
         * If the [BluetoothServer] singleton has an instance of a GenericHealthSensorService return it (otherwise null)
         */
        fun getInstance(): ReconnectionConfigurationService? {
            val bleServer = BluetoothServer.getInstance()
            val ghs = bleServer?.getServiceWithUUID(RCS_SERVICE_UUID)
            return ghs?.let { it as ReconnectionConfigurationService }
        }
    }

}