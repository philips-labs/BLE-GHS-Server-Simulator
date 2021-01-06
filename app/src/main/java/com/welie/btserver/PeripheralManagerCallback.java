package com.welie.btserver;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import org.jetbrains.annotations.NotNull;

interface PeripheralManagerCallback {

    /**
     * Indicates whether a local service has been added successfully.
     *
     * @param status Returns {@link BluetoothGatt#GATT_SUCCESS} if the service was added
     * successfully.
     * @param service The service that has been added
     */
    void onServiceAdded(int status, @NotNull BluetoothGattService service);

    void onCharacteristicRead(@NotNull BluetoothGattCharacteristic characteristic);

    int onCharacteristicWrite(@NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value);

    void onDescriptorRead(@NotNull BluetoothGattDescriptor descriptor);

    int onDescriptorWrite(@NotNull BluetoothGattDescriptor descriptor, @NotNull byte[] value);

    void onNotifyingEnabled(@NotNull BluetoothGattCharacteristic characteristic);

    void onNotifyingDisabled(@NotNull BluetoothGattCharacteristic characteristic);

}

