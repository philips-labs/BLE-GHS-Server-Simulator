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
;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import timber.log.Timber;

import static com.welie.btserver.BluetoothBytesParser.bytes2String;
import static com.welie.btserver.BluetoothBytesParser.mergeArrays;

@SuppressWarnings("UnusedReturnValue")
public class PeripheralManager {

    public static final UUID CUD_DESCRIPTOR_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb");
    public static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Error strings when nullability checks fail
    private static final String CONTEXT_IS_NULL = "Context is null";
    private static final String BLUETOOTH_MANAGER_IS_NULL = "BluetoothManager is null";
    private static final String SERVICE_IS_NULL = "service is null";
    private static final String CHARACTERISTIC_IS_NULL = "Characteristic is null";
    private static final String DEVICE_IS_NULL = "Device is null";
    private static final String CHARACTERISTIC_VALUE_IS_NULL = "Characteristic value is null";
    private static final String CENTRAL_IS_NULL = "Central is null";

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


    @NotNull
    private final Map<String, Central> connectedCentrals = new ConcurrentHashMap<>();

    private final BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, int status, int newState) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Call connect() even though we are already connected
                    // It basically tells Android we will really use this connection
                    // If we don't do this, then cancelConnection won't work
                    // See https://issuetracker.google.com/issues/37127644
                    if (connectedCentrals.containsKey(device.getAddress())) {
                        return;
                    } else {
                        // This will lead to onConnectionStateChange be called again
                        bluetoothGattServer.connect(device, false);
                    }

                    handleDeviceConnected(device);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Deal is double disconnect messages
                    if (!connectedCentrals.containsKey(device.getAddress())) return;

                    handleDeviceDisconnected(device);
                }
            } else {
                Timber.i("Device '%s' disconnected with status %d", device.getName(), status);
                handleDeviceDisconnected(device);
            }
        }

        private void handleDeviceConnected(BluetoothDevice device) {
            Timber.i("Device '%s' connected", device.getName());
            final Central central = new Central(device.getAddress(), device.getName());
            connectedCentrals.put(central.getAddress(), central);
            mainHandler.post(() -> callback.onCentralConnected(central));
        }

        private void handleDeviceDisconnected(BluetoothDevice device) {
            Timber.i("Device '%s' disconnected", device.getName());
            final Central central = getCentral(device);
            if (central != null) {
                mainHandler.post(() -> callback.onCentralDisconnected(central));
            }
            removeCentral(device);
        }

        @Override
        public void onServiceAdded(int status, final BluetoothGattService service) {
            mainHandler.post(() -> callback.onServiceAdded(status, service));
            completedCommand();
        }

        @Override
        public void onCharacteristicReadRequest(@NotNull final BluetoothDevice device, final int requestId, final int offset, @NotNull final BluetoothGattCharacteristic characteristic) {
            Timber.i("read request for characteristic <%s> with offset %d", characteristic.getUuid(), offset);

            mainHandler.post(() -> {
                final Central central = getCentral(device);
                if (central != null) {
                    if (offset == 0) {
                        callback.onCharacteristicRead(central, characteristic);
                    }

                    // If data is longer than MTU - 1, cut the array. Only ATT_MTU - 1 bytes can be sent in Long Read.
                    final byte[] value = copyOf(nonnullOf(characteristic.getValue()), offset, central.getCurrentMtu() - 1);

                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            });
        }

        private @NotNull byte[] copyOf(@NotNull byte[] source, int offset, int maxSize) {
            if (source.length > maxSize) {
                final int chunkSize = Math.min(source.length - offset, maxSize);
                final byte[] result = new byte[chunkSize];
                System.arraycopy(source, offset, result, 0, chunkSize);
                return result;
            }
            return Arrays.copyOf(source, source.length);
        }

        @NotNull
        private final HashMap<BluetoothGattCharacteristic, byte[]> writeLongTemporaryBytes = new HashMap<>();

        @Override
        public void onCharacteristicWriteRequest(@NotNull final BluetoothDevice device, final int requestId, @NotNull final BluetoothGattCharacteristic characteristic, final boolean preparedWrite, final boolean responseNeeded, final int offset, @Nullable final byte[] value) {
            Timber.i("write %s request <%s> offset %d for <%s>", responseNeeded ? "WITH_RESPONSE" : "WITHOUT_RESPONSE", bytes2String(value), offset, characteristic.getUuid());

            final byte[] safeValue = nonnullOf(value);
            mainHandler.post(() -> {
                final Central central = getCentral(device);
                if (central != null) {
                    GattStatus status = GattStatus.SUCCESS;

                    if (!preparedWrite) {
                        status = callback.onCharacteristicWrite(central, characteristic, safeValue);

                        if (status == GattStatus.SUCCESS) {
                            characteristic.setValue(safeValue);
                        }
                    } else {
                        if (offset == 0) {
                            writeLongTemporaryBytes.put(characteristic, safeValue);
                        } else {
                            byte[] temporaryBytes = writeLongTemporaryBytes.get(characteristic);
                            if (temporaryBytes != null && offset == temporaryBytes.length) {
                                writeLongTemporaryBytes.put(characteristic, mergeArrays(temporaryBytes, value));
                            } else {
                                status = GattStatus.INVALID_OFFSET;
                            }
                        }
                    }

                    if (responseNeeded) {
                        bluetoothGattServer.sendResponse(device, requestId, status.getValue(), offset, safeValue);
                    }
                }
            });
        }

        @Override
        public void onDescriptorReadRequest(@NotNull final BluetoothDevice device, int requestId, int offset, final BluetoothGattDescriptor descriptor) {
            Timber.i("read request for descriptor <%s>", descriptor.getUuid());

            mainHandler.post(() -> {
                final Central central = getCentral(device);
                if (central != null) {
                    if (offset == 0) {
                        callback.onDescriptorRead(central, descriptor);
                    }

                    // If data is longer than MTU - 1, cut the array. Only ATT_MTU - 1 bytes can be sent in Long Read.
                    final byte[] value = copyOf(nonnullOf(descriptor.getValue()), offset, central.getCurrentMtu() - 1);

                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            });
        }

        @Override
        public void onDescriptorWriteRequest(@NotNull final BluetoothDevice device, int requestId, @NotNull final BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, @Nullable byte[] value) {
            final byte[] safeValue = nonnullOf(value);
            final BluetoothGattCharacteristic characteristic = Objects.requireNonNull(descriptor.getCharacteristic(), "Descriptor does not have characteristic");

            mainHandler.post(() -> {
                final Central central = getCentral(device);
                if (central != null) {
                    GattStatus status;
                    if (descriptor.getUuid().equals(CCC_DESCRIPTOR_UUID)) {
                        status = checkCccDescriptorValue(safeValue, characteristic);
                    } else {
                        // Ask callback if value is ok or not
                        Timber.i("write request for descriptor <%s>", descriptor.getUuid());
                        status = callback.onDescriptorWrite(central, descriptor, safeValue);
                    }

                    if (status == GattStatus.SUCCESS) {
                        descriptor.setValue(safeValue);
                    }

                    if (responseNeeded) {
                        bluetoothGattServer.sendResponse(device, requestId, status.getValue(), 0, null);
                    }

                    if (status == GattStatus.SUCCESS && descriptor.getUuid().equals(CCC_DESCRIPTOR_UUID)) {
                        if (Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                                || Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                            Timber.i("notifying enabled for <%s>", characteristic.getUuid());
                            callback.onNotifyingEnabled(central, characteristic);
                        } else {
                            Timber.i("notifying disabled for <%s>", characteristic.getUuid());
                            callback.onNotifyingDisabled(central, characteristic);
                        }
                    }
                }
            });
        }

        // Check value to see if it is valid and if matches the characteristic properties
        private GattStatus checkCccDescriptorValue(@NotNull byte[] safeValue, @NotNull BluetoothGattCharacteristic characteristic) {
            GattStatus status = GattStatus.SUCCESS;

            if (safeValue.length != 2) {
                status = GattStatus.INVALID_ATTRIBUTE_VALUE_LENGTH;
            } else if (!(Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    || Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    || Arrays.equals(safeValue, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))) {
                status = GattStatus.REQUEST_NOT_SUPPORTED;
            } else if (!supportsIndicate(characteristic) && Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                status = GattStatus.REQUEST_NOT_SUPPORTED;
            } else if (!supportsNotify(characteristic) && Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                status = GattStatus.REQUEST_NOT_SUPPORTED;
            }
            return status;
        }

        @Override
        public void onExecuteWrite(final BluetoothDevice device, final int requestId, final boolean execute) {
            if (execute) {
                mainHandler.post(() -> {
                    GattStatus status = GattStatus.SUCCESS;
                    final Central central = getCentral(device);
                    if (central != null) {
                        BluetoothGattCharacteristic characteristic = writeLongTemporaryBytes.keySet().iterator().next();
                        if(characteristic != null) {
                            status = callback.onCharacteristicWrite(central, characteristic, writeLongTemporaryBytes.get(characteristic));

                            if (status == GattStatus.SUCCESS) {
                                characteristic.setValue(writeLongTemporaryBytes.get(characteristic));
                            }
                        }
                    }
                    writeLongTemporaryBytes.clear();
                    bluetoothGattServer.sendResponse(device, requestId, status.getValue(), 0, null);
                });
            } else {
                // Long write was cancelled
                writeLongTemporaryBytes.clear();
                bluetoothGattServer.sendResponse(device, requestId, GattStatus.SUCCESS.getValue(), 0, null);
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            completedCommand();
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Timber.i("new MTU: %d", mtu);
            Central central = getCentral(device);
            if (central != null) {
                central.setCurrentMtu(mtu);
            }
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

        Timber.i("Current advertising %d services", getServices().size());
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
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue add service command");
        }
        return result;
    }

    public boolean remove(@NotNull BluetoothGattService service) {
        Objects.requireNonNull(service, SERVICE_IS_NULL);

        bluetoothGattServer.removeService(service);
        return true;
    }

    public void removeAllServices() {
        bluetoothGattServer.clearServices();
    }

    @NotNull
    public List<BluetoothGattService> getServices() {
        return bluetoothGattServer.getServices();
    }

    /**
     * Notify all Centrals that a characteristic has changed
     *
     * @param characteristic the characteristic for which to send a notification
     * @return true if the operation was enqueued, otherwise false
     */
    public boolean notifyCharacteristicChanged(@NotNull final BluetoothGattCharacteristic characteristic) {
        Objects.requireNonNull(characteristic, CHARACTERISTIC_IS_NULL);

        if (doesNotSupportNotifying(characteristic)) return false;

        boolean result = true;
        for (BluetoothDevice device : getConnectedDevices()) {
            if (!notifyCharacteristicChanged(device, characteristic)) {
                result = false;
            }
        }
        return result;
    }

    private boolean notifyCharacteristicChanged(@NotNull final BluetoothDevice bluetoothDevice, @NotNull final BluetoothGattCharacteristic characteristic) {
        Objects.requireNonNull(bluetoothDevice, DEVICE_IS_NULL);
        Objects.requireNonNull(characteristic, CHARACTERISTIC_IS_NULL);
        Objects.requireNonNull(characteristic.getValue(), CHARACTERISTIC_VALUE_IS_NULL);

        if (doesNotSupportNotifying(characteristic)) return false;

        final boolean confirm = supportsIndicate(characteristic);
        boolean result = commandQueue.add(() -> {
            if (!bluetoothGattServer.notifyCharacteristicChanged(bluetoothDevice, characteristic, confirm)) {
                Timber.e("notifying characteristic changed failed for <%s>", characteristic.getUuid());
                completedCommand();
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue notify command");
        }
        return result;
    }

    public void cancelConnection(@NotNull Central central) {
        Objects.requireNonNull(central, CENTRAL_IS_NULL);
        cancelConnection(bluetoothAdapter.getRemoteDevice(central.getAddress()));
    }

    private void cancelConnection(@NotNull BluetoothDevice bluetoothDevice) {
        Objects.requireNonNull(bluetoothDevice, DEVICE_IS_NULL);

        Timber.i("cancelConnection with '%s' (%s)", bluetoothDevice.getName(), bluetoothDevice.getAddress());
        bluetoothGattServer.cancelConnection(bluetoothDevice);
    }

    private @NotNull List<BluetoothDevice> getConnectedDevices() {
        return bluetoothManager.getConnectedDevices(BluetoothGattServer.GATT);
    }

    public @NotNull Set<Central> getConnectedCentrals() {
        Set<Central> centrals = new HashSet<>(connectedCentrals.values());
        return Collections.unmodifiableSet(centrals);
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
            mainHandler.post(() -> {
                try {
                    bluetoothCommand.run();
                } catch (Exception ex) {
                    Timber.e(ex, "command exception");
                    completedCommand();
                }
            });
        }
    }

    @Nullable
    private Central getCentral(@NotNull BluetoothDevice device) {
        Objects.requireNonNull(device, DEVICE_IS_NULL);

        return connectedCentrals.get(device.getAddress());
    }

    private void removeCentral(@NotNull BluetoothDevice device) {
        Objects.requireNonNull(device, DEVICE_IS_NULL);

        connectedCentrals.remove(device.getAddress());
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

    private boolean supportsNotify(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
    }

    private boolean supportsIndicate(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0;
    }

    private boolean doesNotSupportNotifying(@NotNull BluetoothGattCharacteristic characteristic) {
        return !(supportsIndicate(characteristic) || supportsNotify(characteristic));
    }
}
