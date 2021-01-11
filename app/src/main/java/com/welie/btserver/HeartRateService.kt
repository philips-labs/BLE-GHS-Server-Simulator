package com.welie.btserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

import timber.log.Timber;

class HeartRateService extends BaseService {

    private static final UUID HRS_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private static final UUID HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");

    @NotNull
    BluetoothGattService service = new BluetoothGattService(HRS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

    @NotNull
    BluetoothGattCharacteristic measurement = new BluetoothGattCharacteristic(HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_INDICATE,BluetoothGattCharacteristic.PERMISSION_READ);

    @NotNull
    private final Handler handler = new Handler(Looper.getMainLooper());

    @NotNull
    private final Runnable notifyRunnable = this::notifyHeartRate;

    private int currentHR = 80;

    public HeartRateService(@NotNull PeripheralManager peripheralManager) {
        super(peripheralManager);
        service.addCharacteristic(measurement);
        measurement.setValue(new byte[]{0x00, 0x40});
        measurement.addDescriptor(getCccDescriptor());
    }

    @Override
    public @NotNull BluetoothGattService getService() {
        return service;
    }

    @Override
    public void onCentralDisconnected(@NotNull Central central) {
        if (noCentralsConnected()) {
            stopNotifying();
        }
    }

    @Override
    public void onNotifyingEnabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID)) {
            notifyHeartRate();
        }
    }

    @Override
    public void onNotifyingDisabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID)) {
            stopNotifying();
        }
    }

    private void notifyHeartRate() {
        currentHR += (int) ((Math.random() * 10) - 5);
        measurement.setValue(new byte[]{0x00, (byte) currentHR});
        notifyCharacteristicChanged(measurement);
        Timber.i("new hr: %d", currentHR);

        handler.postDelayed(notifyRunnable, 1000);
    }

    private void stopNotifying() {
        handler.removeCallbacks(notifyRunnable);
        measurement.getDescriptor(PeripheralManager.CCC_DESCRIPTOR_UUID).setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
    }
}
