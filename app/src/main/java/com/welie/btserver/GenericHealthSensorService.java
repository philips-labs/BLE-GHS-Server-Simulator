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
import java.util.Date;
import java.util.UUID;

import timber.log.Timber;

class GenericHealthSensorService extends BaseServiceImplementation {

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

    private byte[][] segments = {
            // First segment is all fixed except for last byte which is the current heart rate
            // Type: Obs Type, Length: 4, value: Heart Rate
            {0x00, 0x01, 0x09, 0x2F, 0x00, 0x04, 0x00, 0x02, 0x41, (byte) 0x82,
            // Type: Handle, Length: 2, Value: 0x0001
            0x00, 0x01, 0x09, 0x21, 0x00, 0x02, 0x00, 0x01,
            // Type: Unit Code, Length: 4, value: bpm 0x00040AA0
            0x00, 0x01, 0x09, (byte) 0x96, 0x00, 0x04, 0x00, 0x04, 0x0A, (byte) 0xA0,
            // Type: Simple Num, Length: 4, value: Current HR
            0x00, 0x01, 0x0A, 0x56, 0x00, 0x04, 0x00, 0x00, 0x00, 0x40},

            // Last segment is all fixed except for last 8 bytes which is the epoch time in milliseconds
            // Abs Timestamp, Length: 8, value: Wed Dec 02 2020 15:42:54
            {0x00, 0x01, 0x09, (byte) 0x90, 0x00, 0x08, 0x00, 0x00, 0x01, 0x76, 0x24, 0x1E, (byte) 0xE8, (byte) 0x82}
    };

    @Override
    public void onCentralDisconnected(@NotNull Central central) {
        if (noCentralsConnected()) {
            stopNotifying();
        }
    }

    @Override
    public void onNotifyingEnabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
        sendObservations();
    }

    @Override
    public void onNotifyingDisabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
        stopNotifying();
    }

    private void stopNotifying() {
        handler.removeCallbacks(notifyRunnable);
        observationCharacteristic.getDescriptor(PeripheralManager.CCC_DESCRIPTOR_UUID).setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
    }

    // Right now not handling > 63 segment wrap around
    private void sendBytes(byte[] bytes) {
        int numSegs = (int) Math.ceil(bytes.length / 19.f);
        for(int i = 0; i < numSegs; i++){

            // Compute the segment header byte (first/last seg, seg number)
            int segmentNumber = i + 1;
            int segByte = (segmentNumber << 2);
            segByte |= ((segmentNumber == 1) ? 0x01 : 0x0);
            segByte|= (segmentNumber == numSegs) ? 0x02 : 0x0;

            // Get the next segment data
            int startIndex = i * 19;
            int endIndex = Math.min(startIndex + 19, bytes.length - 1);
            int length = endIndex - startIndex;
            byte[] segment = new byte[length + 1];
            byte[] segmentData =  Arrays.copyOfRange(bytes, startIndex, endIndex);
            segment[0] = (byte) segByte;
            System.arraycopy(segmentData, 0, segment, 1, length);
            Timber.i("Sending <%s>", BluetoothBytesParser.bytes2String(segment));
            observationCharacteristic.setValue(segment);
            peripheralManager.notifyCharacteristicChanged(observationCharacteristic);
        }
    }

    private void sendObservations() {
        updateSegments();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for(int i = 0; i < segments.length; i++) {
            try {
                outputStream.write(segments[i]);
            } catch (IOException e) {
                Timber.e("ByteArray writing went bad... really?");
            }
        }
        sendBytes(outputStream.toByteArray());
        handler.postDelayed(notifyRunnable, 5000);
    }

    private void updateSegments() {

        // Generate random heart rate between 60 - 70
        byte hr = (byte) (Math.random() * (70 - 60 + 1) + 60);
        // Update the HR value in the byte array to send
        byte[] mainSegment = segments[0];
        mainSegment[mainSegment.length - 1] = hr;

        // Segment 3 - Type: Simple Num, Length: 4, value: Current HR
        // segments[2] = new byte[]{0x00, 0x01, 0x0A, 0x56, 0x00, 0x04, 0x00, 0x00, 0x00, hr};

        // Segment 5 - Abs Timestamp, Length: 8, value: Current time in milliseconds since epoch
        long millis = new Date().getTime();
        byte[] milliBytes = ByteBuffer.allocate(8).putLong(millis).array();
        byte[] timeBytes = {0x00, 0x01, 0x09, (byte) 0x90, 0x00, 0x08, milliBytes[0], milliBytes[1], milliBytes[2], milliBytes[3], milliBytes[4], milliBytes[5], milliBytes[6], milliBytes[7]};
        segments[1] = timeBytes;
    }

    @Override
    public @NotNull BluetoothGattService getService() {
        return service;
    }
}
