package com.welie.btserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.UUID;

class CurrentTimeService extends BaseServiceImplementation {

    private static final UUID CTS_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    private static final UUID CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb");

    BluetoothGattService service = new BluetoothGattService(CTS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    BluetoothGattCharacteristic currentTime = new BluetoothGattCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable notifyRunnable = this::notifyCurrentTime;

    public CurrentTimeService(@NotNull PeripheralManager peripheralManager) {
        super(peripheralManager);
        service.addCharacteristic(currentTime);
        currentTime.addDescriptor(getCccDescriptor());
        setCurrentTime();
    }

    @Override
    public void onNotifyingEnabled(@NotNull BluetoothGattCharacteristic characteristic) {
        notifyCurrentTime();
    }

    @Override
    public void onNotifyingDisabled(@NotNull BluetoothGattCharacteristic characteristic) {
        handler.removeCallbacks(notifyRunnable);
    }

    private void notifyCurrentTime() {
        setCurrentTime();
        peripheralManager.notifyCharacteristicChanged(currentTime);
        handler.postDelayed(notifyRunnable, 1000);
    }

    private void setCurrentTime() {
        BluetoothBytesParser parser = new BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN);
        parser.setCurrentTime(Calendar.getInstance());
        currentTime.setValue(parser.getValue());
    }

    @Override
    public BluetoothGattService getService() {
        return service;
    }
}
