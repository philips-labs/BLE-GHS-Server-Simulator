package com.welie.btserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import org.jetbrains.annotations.NotNull;

interface PeripheralManagerCallback {

    void onCharacteristicRead(@NotNull BluetoothGattCharacteristic characteristic);

    int onCharacteristicWrite(@NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value);

    void onDescriptorWrite(@NotNull BluetoothGattDescriptor descriptor, @NotNull byte[] value);

    void onNotifyingEnabled(@NotNull BluetoothGattCharacteristic characteristic);

    void onNotifyingDisabled(@NotNull BluetoothGattCharacteristic characteristic);

}

