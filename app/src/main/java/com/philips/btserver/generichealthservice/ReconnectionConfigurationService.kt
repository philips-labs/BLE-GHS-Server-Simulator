package com.philips.btserver.generichealthservice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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

enum class RCCPOpCodes(val opCode : Byte) {
    EnableDisconnect(0),
    GetActualCommunicationParameters(1),
    ProposeSetting(2),
    ActivateStoredSettings(3),
    GetMaxValues(4),
    GetMinValues(5),
    GetStoredValues(6),
    SetFilterAcceptListTimer(7),
    GetFilterAcceptListTimer(8),
    SetAdvertisementConfiguration(9),
    UpgradetoLESCOnly(0x0A),
    SwitchOOBPairing(0x0B),
    LimitedAccess(0x0C),
    // responses
    ProcedureResponse(0x0E),
    CommunicationParameterResponse(0x0F);
    // there are more

    companion object {
        infix fun from(value: Byte): RCCPOpCodes? = RCCPOpCodes.values().firstOrNull { it.opCode == value }
    }
}

enum class RCCPResultCodes(val resultCode : Byte) {
    Success(1),
    OpcodeNotSupported(2),
    InvalidOperand(3),
    OperationFailed(4);
    // there are more....
}

class ReconnectionConfigurationService(peripheralManager: BluetoothPeripheralManager) : BaseService(peripheralManager) {


    val featuresBits = BitMask( RCSFeatureBits.EnableDisconnectSupported.bit or RCSFeatureBits.ReadyforDisconnectSupported.bit)
    var settingsBits = BitMask(RCSSettingsBits.AdvertisementMode0.bit)
    val settingsLength = 3.toByte()

    private val featuresCharacteristicBytes: ByteArray get() = byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + featuresBits.intValue.asLittleEndianUint32Array()
    private var settingsCharacteristicBytes: ByteArray  = byteArrayOf(settingsLength) + settingsBits.intValue.asLittleEndianArray()

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


    // function to call when the server is ready for disconnect / has no more data to send
    fun readyForDisconnect() {
        if (noCentralsConnected()) {
            Timber.i("Nothing to disconnect from...")
        } else {
            Timber.i("Ready for Disconnect: notifying connected Centrals.")
        }

        // set the ready for disconnect settings bit
        settingsBits = settingsBits.or(RCSSettingsBits.ReadyForDisconnect)
        settingsCharacteristicBytes = byteArrayOf(settingsLength) + settingsBits.intValue.asLittleEndianArray()

        // notify the settings characteristic to connected client(s)
        super.notifyCharacteristicChanged(settingsCharacteristicBytes, rcSettingsCharacteristic)
    }

    // Notification from [central] that [characteristic] has notification enabled.
    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        when (characteristic.uuid) {
            RC_SETTINGS_CHARACTERISTIC_UUID -> {
                Timber.i("RC Settings notifying enabled")
                rcSettingsCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID)?.setValue(
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
            RC_CONTROL_POINT_CHARACTERISTIC_UUID -> {
                Timber.i("RCCP notifying enabled")
                rcControlPointCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID)?.setValue(
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            }
            else -> { // weird??
            }
        }
        super.onNotifyingEnabled(central, characteristic)
    }

    override fun onNotifyingDisabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        when (characteristic.uuid) {
            RC_SETTINGS_CHARACTERISTIC_UUID -> {
                Timber.i("RC Settings notifying disabled")
                rcSettingsCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID)?.setValue(
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                //peripheralManager.add
            }
            RC_CONTROL_POINT_CHARACTERISTIC_UUID -> {
                Timber.i("RCCP notifying disabled")
                rcControlPointCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID)?.setValue(
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            }
            else -> {
            }
        }
        super.onNotifyingDisabled(central, characteristic)
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
                Timber.i("RCCP write received")
                // first check CCCD - To Do: decide on which method to use for checking - probably best t leave only check 2 and remove the local admin which only works for one client
                if (!rcControlPointCharacteristic.isIndicateEnabled()) {
                    Timber.i("RCCP CCCD improperly configured - check 1.")
                    return GattStatus.CCCD_CFG_ERROR
                }
                if (!(central in peripheralManager.getCentralsWantingIndications(rcControlPointCharacteristic))){
                    Timber.i("RCCP CCCD improperly configured - check 2.")
                    return GattStatus.CCCD_CFG_ERROR
                }

                val parser = BluetoothBytesParser(value)
                val opcodeValue = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT8).toByte()
                val opcode = RCCPOpCodes.from(opcodeValue)
                if(opcode != null) {
                    when(opcode){
                        RCCPOpCodes.EnableDisconnect -> {
                            Timber.i("Disconnect Enabbled...")
                            // indicate response
                            val responseBytes = byteArrayOf(RCCPOpCodes.ProcedureResponse.opCode, opcodeValue, RCCPResultCodes.Success.resultCode)
                            notifyCharacteristicChanged(responseBytes, central, rcControlPointCharacteristic)
                        }
                        else -> {
                            Timber.i( "RCS CP command ${opcode.name} NOT supported")
                            // indicate opcode not supported...
                            val responseBytes = byteArrayOf(RCCPOpCodes.ProcedureResponse.opCode, opcodeValue, RCCPResultCodes.OpcodeNotSupported.resultCode)
                            notifyCharacteristicChanged(responseBytes, central, rcControlPointCharacteristic)
                        }
                    }
                } else {
                    Timber.i("RCS CP: invalid operand...")
                    val responseBytes = byteArrayOf(RCCPOpCodes.ProcedureResponse.opCode, opcodeValue, RCCPResultCodes.InvalidOperand.resultCode)
                    notifyCharacteristicChanged(responseBytes, central, rcControlPointCharacteristic)
                }
                return GattStatus.SUCCESS
            }
            else -> return super.onCharacteristicWrite(central, characteristic, value)
        }
    }

    override fun onCentralConnected(central: BluetoothCentral) {
        super.onCentralConnected(central)
        // reset ready to disconnect settings
        settingsBits = BitMask(RCSSettingsBits.AdvertisementMode0.bit)
        settingsCharacteristicBytes = byteArrayOf(settingsLength) + settingsBits.intValue.asLittleEndianArray()
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        super.onCentralDisconnected(central)
        if (noCentralsConnected()){
            rcSettingsCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID)?.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            rcControlPointCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID)?.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
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
        fun getInstance(): ReconnectionConfigurationService {
            val rcs = BluetoothServer.getInstance()!!.getServiceWithUUID(RCS_SERVICE_UUID)
            return rcs.let { it as ReconnectionConfigurationService }
        }
    }

}