package com.welie.btserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import org.jetbrains.annotations.NotNull;

interface Service {

    BluetoothGattService getService();

    void onCharacteristicRead(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic);

    GattStatus onCharacteristicWrite(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value);

    void onDescriptorRead(@NotNull Central central, @NotNull BluetoothGattDescriptor descriptor);

    GattStatus onDescriptorWrite(@NotNull Central central, @NotNull BluetoothGattDescriptor descriptor, @NotNull byte[] value);

    void onNotifyingEnabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic);

    void onNotifyingDisabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic);

    void onCentralConnected(@NotNull Central central);

    void onCentralDisconnected(@NotNull Central central);
}
