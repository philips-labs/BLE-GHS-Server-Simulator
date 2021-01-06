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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import timber.log.Timber;

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
    private static final String ADDRESS_IS_NULL = "Address is null";

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
    private final ConcurrentHashMap<String, Central> connectedCentrals = new ConcurrentHashMap<>();

    private final BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            final Central central = getCentral(device);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Timber.i("Device %s connected", central.getName());
                    mainHandler.post(() -> callback.onCentralConnected(central));
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.i("Device %s disconnected", central.getName());
                    mainHandler.post(() -> callback.onCentralDisconnected(central));
                }
            }
        }

        @Override
        public void onServiceAdded(int status, final BluetoothGattService service) {
            Timber.i("added service <%s>", service.getUuid());
            mainHandler.post(() -> callback.onServiceAdded(status, service));
            completedCommand();
        }

        @Override
        public void onCharacteristicReadRequest(@NotNull final BluetoothDevice device, int requestId, int offset, @NotNull final BluetoothGattCharacteristic characteristic) {
            Timber.i("read request for characteristic <%s>", characteristic.getUuid());

            mainHandler.post(() -> {
                callback.onCharacteristicRead(getCentral(device), characteristic);
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, nonnullOf(characteristic.getValue()));
            });
        }

        @Override
        public void onCharacteristicWriteRequest(@NotNull final BluetoothDevice device, int requestId, @NotNull final BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, @Nullable byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Timber.i("write %s request <%s> for <%s>", responseNeeded ? "WITH_RESPONSE" : "WITHOUT_RESPONSE", bytes2String(value), characteristic.getUuid());

            // Ask callback if this write is ok or not
            final byte[] safeValue = nonnullOf(value);
            final int status = callback.onCharacteristicWrite(getCentral(device),characteristic, safeValue);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic.setValue(safeValue);
            }

            if (responseNeeded) {
                mainHandler.post(() -> bluetoothGattServer.sendResponse(device, requestId, status, 0, null));
            }
        }

        @Override
        public void onDescriptorReadRequest(@NotNull final BluetoothDevice device, int requestId, int offset, final BluetoothGattDescriptor descriptor) {
            Timber.i("read request for descriptor <%s>", descriptor.getUuid());

            mainHandler.post(() -> {
                callback.onDescriptorRead(getCentral(device),descriptor);
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, nonnullOf(descriptor.getValue()));
            });
        }

        @Override
        public void onDescriptorWriteRequest(@NotNull final BluetoothDevice device, int requestId, @NotNull final BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, @Nullable byte[] value) {
            int status = BluetoothGatt.GATT_SUCCESS;
            final byte[] safeValue = nonnullOf(value);
            if (descriptor.getUuid().equals(CCC_DESCRIPTOR_UUID)) {
                if (safeValue.length != 2) {
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                } else if (!(Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        || Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        || Arrays.equals(safeValue, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))) {
                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                }
            } else {
                // Ask callback if value is ok or not
                Timber.i("write request for descriptor <%s>", descriptor.getUuid());
                status = callback.onDescriptorWrite(getCentral(device),descriptor, safeValue);
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                descriptor.setValue(safeValue);
            }

            if (responseNeeded) {
                final int finalStatus = status;
                mainHandler.post(() -> bluetoothGattServer.sendResponse(device, requestId, finalStatus, 0, null));
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getUuid().equals(CCC_DESCRIPTOR_UUID)) {
                    BluetoothGattCharacteristic characteristic = Objects.requireNonNull(descriptor.getCharacteristic(), "Descriptor does not have characteristic");
                    if (Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                            || Arrays.equals(safeValue, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        Timber.i("notifying enabled for <%s>", characteristic.getUuid());
                        mainHandler.post(() -> callback.onNotifyingEnabled(getCentral(device),characteristic));
                    } else {
                        Timber.i("notifying disabled for <%s>", characteristic.getUuid());
                        mainHandler.post(() -> callback.onNotifyingDisabled(getCentral(device),characteristic));
                    }
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
            Central central = getCentral(device);
            central.setCurrentMtu(mtu);
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

        final boolean confirm = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0;
        boolean result = commandQueue.add(() -> bluetoothGattServer.notifyCharacteristicChanged(bluetoothDevice, characteristic, confirm));

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue read characteristic command");
        }
        return result;
    }

    public boolean cancelConnection(@NotNull String peripheralAddress) {
        Objects.requireNonNull(peripheralAddress, ADDRESS_IS_NULL);
        List<BluetoothDevice> bluetoothDevices = getConnectedDevices();

        for (BluetoothDevice device : bluetoothDevices) {
            if (device.getAddress().equals(peripheralAddress)) {
                cancelConnection(device);
                return true;
            }
        }
        return false;
    }

    public @NotNull List<BluetoothDevice> getConnectedDevices() {
        return bluetoothManager.getConnectedDevices(BluetoothGattServer.GATT);
    }

    private void cancelConnection(@NotNull BluetoothDevice bluetoothDevice) {
        Objects.requireNonNull(bluetoothDevice, DEVICE_IS_NULL);
        bluetoothGattServer.cancelConnection(bluetoothDevice);
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

    private Central getCentral(BluetoothDevice device) {
        String address = device.getAddress();

        if (connectedCentrals.contains(address)) {
            return connectedCentrals.get(address);
        }

        Central central = new Central(device.getAddress(), device.getName());
        connectedCentrals.put(central.getAddress(), central);
        return central;
    }

    private void removeCentral(BluetoothDevice device) {
        String address = device.getAddress();

        if (connectedCentrals.contains(address)) {
            connectedCentrals.remove(address);
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
