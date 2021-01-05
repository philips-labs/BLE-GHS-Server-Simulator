package com.welie.btserver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import timber.log.Timber;

@SuppressWarnings("UnusedReturnValue")
public class PeripheralManager {

    public static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Error strings when nullability checks fail
    private static final String CONTEXT_IS_NULL = "Context is null";
    private static final String BLUETOOTH_MANAGER_IS_NULL = "BluetoothManager is null";
    private static final String SERVICE_IS_NULL = "service is null";
    private static final String CHARACTERISTIC_IS_NULL = "Characteristic is null";
    private static final String DEVICE_IS_NULL = "Device is null";

    @NotNull
    private final Context context;

    @NotNull
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @NotNull
    private final BluetoothManager bluetoothManager;

    @NotNull
    private final BluetoothAdapter bluetoothAdapter;

    @NotNull
    private final BluetoothLeAdvertiser bluetoothLeAdvertiser;

    @NotNull
    private final BluetoothGattServer bluetoothGattServer;

    @NotNull
    private final PeripheralManagerCallback callback;

    @NotNull
    private final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();

    private volatile boolean commandQueueBusy = false;
    private int currentMtu = 23;

    @NotNull
    private final HashSet<BluetoothDevice> connectedDevices = new HashSet<>();

    private final BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Timber.i("Device %s connected", device.getName());
                    connectedDevices.add(device);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.i("Device %s disconnected", device.getName());
                    connectedDevices.remove(device);
                }
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Timber.i("added service <%s>", service.getUuid());
            completedCommand();
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            Timber.i("read request for <%s>",characteristic );

            mainHandler.post(() -> {
                callback.onCharacteristicRead(characteristic);
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            });
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Timber.i("write %s request <%s> for <%s>",characteristic, bytes2String(value), responseNeeded ? "WITH_RESPONSE" : "WITHOUT_RESPONSE");

            // Ask callback if this write is ok or not
            final int status = callback.onCharacteristicWrite(characteristic, value);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic.setValue(value);
            }

            if (responseNeeded) {
                mainHandler.post(() -> bluetoothGattServer.sendResponse(device, requestId, status, 0, null));
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            final byte[] value = nonnullOf(descriptor.getValue());
            mainHandler.post(() -> bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value));
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            int status = BluetoothGatt.GATT_SUCCESS;
            if (descriptor.getUuid().equals(CCC_DESCRIPTOR_UUID)) {
                if (value.length != 2) {
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                } else if (!(Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        ||  Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        || Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))) {
                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                }
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                descriptor.setValue(value);
            }

            if (responseNeeded) {
                final int finalStatus = status;
                mainHandler.post(() -> bluetoothGattServer.sendResponse(device, requestId, finalStatus, 0, null));
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getUuid().equals(CCC_DESCRIPTOR_UUID)) {
                    BluetoothGattCharacteristic characteristic = Objects.requireNonNull(descriptor.getCharacteristic(), "Descriptor does not have characteristic");
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                            || Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        mainHandler.post(() -> callback.onNotifyingEnabled(characteristic));
                    } else {
                        mainHandler.post(() -> callback.onNotifyingDisabled(characteristic));
                    }
                } else {
                    mainHandler.post(() -> callback.onDescriptorWrite(descriptor, value));
                }
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            completedCommand();
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Timber.i("new MTU: %d", mtu);
            currentMtu = mtu;
        }

        @Override
        public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(device, txPhy, rxPhy, status);
        }

        @Override
        public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            super.onPhyRead(device, txPhy, rxPhy, status);
        }
    };

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Timber.i("Advertising started");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Timber.i("Advertising failed");
        }
    };

    public PeripheralManager(@NotNull Context context, @NotNull BluetoothManager bluetoothManager, @NotNull PeripheralManagerCallback callback) {
        this.context = Objects.requireNonNull(context, CONTEXT_IS_NULL);
        this.callback = Objects.requireNonNull(callback, "Callback is null");
        this.bluetoothManager = Objects.requireNonNull(bluetoothManager, BLUETOOTH_MANAGER_IS_NULL);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        this.bluetoothGattServer = bluetoothManager.openGattServer(context, bluetoothGattServerCallback);
    }

    public void close() {
        bluetoothGattServer.close();
    }

    public void startAdvertising(AdvertiseSettings settings, AdvertiseData advertiseData, AdvertiseData scanResponse) {
        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            Timber.e("device does not support advertising");
        } else {
            bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback);
        }
    }

    public void stopAdvertising() {
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
    }


    public boolean add(@NotNull BluetoothGattService service) {
        Objects.requireNonNull(service, SERVICE_IS_NULL);

        boolean result = commandQueue.add(() -> {
            if (!bluetoothGattServer.addService(service)) {
                Timber.e("adding service %s failed", service.getUuid());
                completedCommand();
                } else {
                    Timber.d("adding service <%s>", service.getUuid());
                }
            });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue read characteristic command");
        }
        return result;
    }

    public boolean remove(BluetoothGattService service) {
        bluetoothGattServer.removeService(service);
        return true;
    }

    public boolean removeAllServices() {
        boolean result = true;
        List<BluetoothGattService> services = bluetoothGattServer.getServices();
        for (BluetoothGattService service : services) {
            result = remove(service);
        }
        return result;
    }

    public boolean notifyCharacteristicChanged(@NotNull final BluetoothGattCharacteristic characteristic) {
        Objects.requireNonNull(characteristic, CHARACTERISTIC_IS_NULL);

        boolean result = true;
        for (BluetoothDevice device : connectedDevices) {
            if(!notifyCharacteristicChanged(device, characteristic)) {
                result = false;
            }
        }
        return result;
    }

    private boolean notifyCharacteristicChanged(@NotNull final BluetoothDevice bluetoothDevice, @NotNull final BluetoothGattCharacteristic characteristic) {
        Objects.requireNonNull(bluetoothDevice, DEVICE_IS_NULL);
        Objects.requireNonNull(characteristic, CHARACTERISTIC_IS_NULL);

        final boolean confirm = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0;
        boolean result = commandQueue.add(() -> bluetoothGattServer.notifyCharacteristicChanged(bluetoothDevice, characteristic, confirm));

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue read characteristic command");
        }
        return result;
    }

    public int getCurrentMtu() {
        return currentMtu;
    }

    /**
     * The current command has been completed, move to the next command in the queue (if any)
     */
    private void completedCommand() {
        commandQueue.poll();
        commandQueueBusy = false;
        nextCommand();
    }

    /**
     * Execute the next command in the subscribe queue.
     * A queue is used because the calls have to be executed sequentially.
     * If the read or write fails, the next command in the queue is executed.
     */
    private void nextCommand() {
        synchronized (this) {
            // If there is still a command being executed, then bail out
            if (commandQueueBusy) return;

            // Check if there is something to do at all
            final Runnable bluetoothCommand = commandQueue.peek();
            if (bluetoothCommand == null) return;

            // Execute the next command in the queue
            commandQueueBusy = true;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        bluetoothCommand.run();
                    } catch (Exception ex) {
                        Timber.e(ex, "command exception");
                        completedCommand();
                    }
                }
            });
        }
    }

    /**
     * Make a byte array nonnull by either returning the original byte array if non-null or an empty bytearray
     *
     * @param source byte array to make nonnull
     * @return the source byte array or an empty array if source was null
     */
    @NotNull
    private byte[] nonnullOf(@Nullable byte[] source) {
        return (source == null) ? new byte[0] : source;
    }


    /**
     * Converts byte array to hex string
     *
     * @param bytes the byte array to convert
     * @return String representing the byte array as a HEX string
     */
    @NotNull
    private static String bytes2String(@Nullable final byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
