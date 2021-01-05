package com.welie.btserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.welie.btserver.PeripheralManager.CCC_DESCRIPTOR_UUID;

class BaseServiceImplementation implements ServiceImplementation{

    @NotNull
    protected final PeripheralManager peripheralManager;

    BaseServiceImplementation(@NotNull PeripheralManager peripheralManager) {
        this.peripheralManager = Objects.requireNonNull(peripheralManager);
    }

    BluetoothGattDescriptor getCccDescriptor() {
        BluetoothGattDescriptor cccDescriptor = new BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        cccDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        return cccDescriptor;
    }

    @Override
    public BluetoothGattService getService() {
        return null;
    }

    @Override
    public void onCharacteristicRead(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public int onCharacteristicWrite(@NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value) {
        return 0;
    }

    @Override
    public void onDescriptorWrite(@NotNull BluetoothGattDescriptor descriptor, @NotNull byte[] value) {

    }

    @Override
    public void onNotifyingEnabled(@NotNull BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onNotifyingDisabled(@NotNull BluetoothGattCharacteristic characteristic) {

    }
}
