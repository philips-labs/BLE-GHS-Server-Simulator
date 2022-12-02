package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.philips.btserver.BaseService
import com.philips.btserver.BluetoothServer
import com.philips.btserver.extensions.*
import com.welie.blessed.*
import timber.log.Timber
import java.util.*

@Suppress("unused")
enum class RCSFeatureBits(override val bit: Long) : Flags {
    E2ECrcSupported((1 shl 0).toLong()),
    EnableDisconnectSupported((1 shl 1).toLong()),
    ReadyforDisconnectSupported((1 shl 2).toLong()),
    ProposeReconnectionTimeoutSupported((1 shl 3).toLong()),
    ProposeConnectionIntervalSupported((1 shl 4).toLong()),
    ProposeSlaveLatencySupported((1 shl 5).toLong()),
    ProposeSupervisionTimeoutSupported((1 shl 6).toLong()),
    ProposeAdvertisementIntervalSupported((1 shl 7).toLong()),
    ProposeAdvertisementCountSupported((1 shl 8).toLong()),
    ProposeAdvertisementRepetitionTimeSupported((1 shl 9).toLong()),
    AdvertisementConfiguration1Supported((1 shl 10).toLong()),
    AdvertisementConfiguration2Supported((1 shl 11).toLong()),
    AdvertisementConfiguration3Supported((1 shl 12).toLong()),
    AdvertisementConfiguration4Supported((1 shl 13).toLong()),
    UpgradetoLESCOnlySupported((1 shl 14).toLong()),
    NextPairingOOBSupported((1 shl 15).toLong()),
    UseofWhiteListSupported((1 shl 16).toLong()),
    LimitedAccessSupported((1 shl 17).toLong()),
    FeatureExtension((1 shl 23).toLong())
}

@Suppress("unused")
enum class RCSSettingsBits(override val bit: Long) : Flags {
    LESCOnly((1 shl 1).toLong()),
    UseOOBPairing((1 shl 2).toLong()),
    ReadyForDisconnect((1 shl 4).toLong()),
    LimitedAccess((1 shl 5).toLong()),
    AccessPermitted((1 shl 6).toLong()),
    AdvertisementMode0((1 shl 8).toLong()),
    AdvertisementMode1((1 shl 9).toLong())
}

class ReconnectionConfigurationService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {


    var featuresBits = BitMask( RCSFeatureBits.EnableDisconnectSupported.bit or RCSFeatureBits.ReadyforDisconnectSupported.bit)
    var settingsBits = BitMask(RCSSettingsBits.AdvertisementMode0.bit)
    val settingsLength = 3.toByte()

    private val featuresCharacteristicBytes: ByteArray get() = byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + featuresBits.intValue.asLittleEndianUint32Array()
    private val settingsCharacteristicBytes: ByteArray get() = byteArrayOf(settingsLength) + settingsBits.intValue.asLittleEndianArray()

    override val service = BluetoothGattService(RCS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

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

    private val rcControlPointCharacteristic = BluetoothGattCharacteristic(
        RC_CONTROL_POINT_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    // Notification from [central] that [characteristic] has notification enabled.
    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        when (characteristic.uuid) {
            RC_SETTINGS_CHARACTERISTIC_UUID -> Timber.i("RC Settings notifying enabled")
            RC_CONTROL_POINT_CHARACTERISTIC_UUID -> Timber.i("RCCP notifying enabled")
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
        return when (characteristic.uuid) {
            RC_FEATURE_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, featuresCharacteristicBytes)
            RC_SETTINGS_CHARACTERISTIC_UUID -> ReadResponse(GattStatus.SUCCESS, settingsCharacteristicBytes)
            else -> super.onCharacteristicRead(central, characteristic)
        }
    }

    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        //Timber.i("onCharacteristicWrite with Bytes: ${value.asFormattedHexString()}")
        when (characteristic.uuid) {
            RC_CONTROL_POINT_CHARACTERISTIC_UUID -> {
                Timber.i("RCCP write")
                return GattStatus.SUCCESS
            }
            else -> return super.onCharacteristicWrite(central, characteristic, value)
        }
    }

    init {
        initCharacteristic(rcFeaturesCharacteristic, RC_FEATURES_DESCRIPTION)
        initCharacteristic(rcSettingsCharacteristic, RC_SETTINGS_DESCRIPTION)
        initCharacteristic(rcControlPointCharacteristic, RC_CONTROL_POINT_DESCRIPTION)
    }

    companion object {
        val RCS_SERVICE_UUID = UUID.fromString("00001829-0000-1000-8000-00805f9b34fb")
        val RC_FEATURE_CHARACTERISTIC_UUID =
            UUID.fromString("00002B1D-0000-1000-8000-00805f9b34fb")
        private const val RC_FEATURES_DESCRIPTION = "RCS Feature Characteristic"
        val RC_SETTINGS_CHARACTERISTIC_UUID =
            UUID.fromString("00002B1E-0000-1000-8000-00805f9b34fb")
        private const val RC_SETTINGS_DESCRIPTION = "RCS Settings Characteristic"
        val RC_CONTROL_POINT_CHARACTERISTIC_UUID =
            UUID.fromString("00002B1F-0000-1000-8000-00805f9b34fb")
        private const val RC_CONTROL_POINT_DESCRIPTION = "RCS Control Point Characteristic"

        /**
         * If the [BluetoothServer] singleton has an instance of a ReconnectionConfigurationService return it (otherwise null)
         */
        fun getInstance(): ReconnectionConfigurationService? {
            val rcs = BluetoothServer.getInstance()?.getServiceWithUUID(RCS_SERVICE_UUID)
            return rcs?.let { it as ReconnectionConfigurationService }
        }
    }

}