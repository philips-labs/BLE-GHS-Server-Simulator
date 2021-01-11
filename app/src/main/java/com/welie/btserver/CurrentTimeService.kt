package com.welie.btserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.UUID;

import timber.log.Timber;

class CurrentTimeService extends BaseService {

    private static final UUID CTS_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    private static final UUID CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb");

    @NotNull
    BluetoothGattService service = new BluetoothGattService(CTS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

    @NotNull
    BluetoothGattCharacteristic currentTime = new BluetoothGattCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);

    @NotNull
    private final Handler handler = new Handler(Looper.getMainLooper());

    @NotNull
    private final Runnable notifyRunnable = this::notifyCurrentTime;

    public CurrentTimeService(@NotNull PeripheralManager peripheralManager) {
        super(peripheralManager);
        service.addCharacteristic(currentTime);
        currentTime.addDescriptor(getCccDescriptor());
        setCurrentTime();
    }

    @Override
    public GattStatus onCharacteristicWrite(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value) {
        Timber.i("onCharacteristicWrite <%s>", BluetoothBytesParser.bytes2String(value));
        return super.onCharacteristicWrite(central, characteristic, value);
    }

    @Override
    public void onNotifyingEnabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
        notifyCurrentTime();
    }

    @Override
    public void onNotifyingDisabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
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
    public @NotNull BluetoothGattService getService() {
        return service;
    }
}
