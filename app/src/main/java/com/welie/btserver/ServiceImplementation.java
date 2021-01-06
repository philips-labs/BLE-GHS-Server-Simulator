package com.welie.btserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import org.jetbrains.annotations.NotNull;

interface ServiceImplementation {

    BluetoothGattService getService();

    void onCharacteristicRead(@NotNull BluetoothGattCharacteristic characteristic);

    int onCharacteristicWrite(@NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value);

    void onDescriptorRead(@NotNull BluetoothGattDescriptor descriptor);

    int onDescriptorWrite(@NotNull BluetoothGattDescriptor descriptor, @NotNull byte[] value);

    void onNotifyingEnabled(@NotNull BluetoothGattCharacteristic characteristic);

    void onNotifyingDisabled(@NotNull BluetoothGattCharacteristic characteristic);
}
