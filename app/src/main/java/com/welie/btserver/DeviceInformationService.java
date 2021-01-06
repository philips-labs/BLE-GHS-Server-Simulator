package com.welie.btserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;


class DeviceInformationService extends BaseServiceImplementation {

    private static final UUID DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private static final UUID MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");

    @NotNull
    BluetoothGattService service = new BluetoothGattService(DIS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

    @NotNull
    BluetoothGattCharacteristic manufacturer = new BluetoothGattCharacteristic(MANUFACTURER_NAME_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);

    @NotNull
    BluetoothGattCharacteristic modelNumber = new BluetoothGattCharacteristic(MODEL_NUMBER_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);

    public DeviceInformationService(@NotNull PeripheralManager peripheralManager) {
        super(peripheralManager);
        service.addCharacteristic(manufacturer);
        service.addCharacteristic(modelNumber);

        manufacturer.setValue(Build.MANUFACTURER);
        modelNumber.setValue(Build.MODEL);
    }

    @Override
    public @NotNull BluetoothGattService getService() {
        return service;
    }
}
