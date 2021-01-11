package com.welie.btserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import timber.log.Timber;

class GenericHealthSensorService extends BaseService {

    private static final UUID GHS_SERVICE_UUID = UUID.fromString("0000183D-0000-1000-8000-00805f9b34fb");
    private static final UUID OBSERVATION_CHARACTERISTIC_UUID = UUID.fromString("00002AC4-0000-1000-8000-00805f9b34fb");
    private static final UUID CONTROL_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002AC6-0000-1000-8000-00805f9b34fb");

    private static final String OBSERVATION_DESCRIPTION = "Characteristic for ACOM Observation segments.";
    private static final String CONTROL_POINT_DESCRIPTION = "Control point for generic health sensor.";

    @NotNull
    final BluetoothGattService service = new BluetoothGattService(GHS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

    @NotNull
    final BluetoothGattCharacteristic observationCharacteristic = new BluetoothGattCharacteristic(OBSERVATION_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0);

    @NotNull
    final BluetoothGattCharacteristic controlCharacteristic = new BluetoothGattCharacteristic(CONTROL_POINT_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_WRITE);

    @NotNull
    private final Handler handler = new Handler(Looper.getMainLooper());

    @NotNull
    private final Runnable notifyRunnable = this::sendObservations;

    public GenericHealthSensorService(@NotNull PeripheralManager peripheralManager) {
        super(peripheralManager);
        service.addCharacteristic(observationCharacteristic);
        service.addCharacteristic(controlCharacteristic);
        observationCharacteristic.addDescriptor(getCccDescriptor());
        observationCharacteristic.addDescriptor(getCudDescriptor(OBSERVATION_DESCRIPTION));
        observationCharacteristic.setValue(new byte[]{0x00});
        controlCharacteristic.addDescriptor(getCccDescriptor());
        controlCharacteristic.addDescriptor(getCudDescriptor(CONTROL_POINT_DESCRIPTION));
        controlCharacteristic.setValue(new byte[]{0x00});
    }

    @Override
    public void onCentralDisconnected(@NotNull Central central) {
        if (noCentralsConnected()) {
            stopNotifying();
        }
    }

    @Override
    public void onNotifyingEnabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(OBSERVATION_CHARACTERISTIC_UUID)) {
            sendObservations();
        }
    }

    @Override
    public void onNotifyingDisabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(OBSERVATION_CHARACTERISTIC_UUID)) {
            stopNotifying();
        }
    }

    private void stopNotifying() {
        handler.removeCallbacks(notifyRunnable);
        observationCharacteristic.getDescriptor(PeripheralManager.CCC_DESCRIPTOR_UUID).setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
    }

    // Right now not handling > 63 segment wrap around
    private void sendBytes(byte[] bytes) {
        int segmentSize = getMinimalMTU() - 4;
        int numSegs = (int) Math.ceil(bytes.length / (float) segmentSize);
        for(int i = 0; i < numSegs; i++){

            // Compute the segment header byte (first/last seg, seg number)
            int segmentNumber = i + 1;
            int segByte = (segmentNumber << 2);
            segByte |= ((segmentNumber == 1) ? 0x01 : 0x0);
            segByte|= (segmentNumber == numSegs) ? 0x02 : 0x0;

            // Get the next segment data
            int startIndex = i * segmentSize;
            int endIndex = Math.min(startIndex + segmentSize, bytes.length - 1);
            int length = endIndex - startIndex;
            byte[] segment = new byte[length + 1];
            byte[] segmentData =  Arrays.copyOfRange(bytes, startIndex, endIndex);
            segment[0] = (byte) segByte;
            System.arraycopy(segmentData, 0, segment, 1, length);

            // Send segment
            Timber.i("Sending <%s>", BluetoothBytesParser.bytes2String(segment));
            observationCharacteristic.setValue(segment);
            notifyCharacteristicChanged(observationCharacteristic);
        }
    }

    private void sendObservations() {
        SimpleNumericObservation observation = new SimpleNumericObservation((short) 1, ObservationType.PULSE_RATE, 85, Unit.BPM, Calendar.getInstance().getTime());
        sendBytes(observation.serialize());
        handler.postDelayed(notifyRunnable, 5000);
    }

    private int getMinimalMTU() {
        int minMTU = 512;
        for (Central central : peripheralManager.getConnectedCentrals()) {
            if (central.getCurrentMtu() < minMTU) {
                minMTU = central.getCurrentMtu();
            }
        }
        return minMTU;
    }

    @Override
    public @NotNull BluetoothGattService getService() {
        return service;
    }
}
