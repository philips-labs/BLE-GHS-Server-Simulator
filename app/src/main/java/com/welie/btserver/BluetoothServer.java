package com.welie.btserver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

import timber.log.Timber;

class BluetoothServer {
    private static BluetoothServer instance = null;
    private final Context context;
    private final Handler handler = new Handler();
    BluetoothAdapter bluetoothAdapter;
    BluetoothManager bluetoothManager;

    @NotNull
    private final PeripheralManager peripheralManager;

    public static synchronized BluetoothServer getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothServer(context.getApplicationContext());
        }
        return instance;
    }

    private final PeripheralManagerCallback peripheralManagerCallback = new PeripheralManagerCallback() {
        @Override
        public void onServiceAdded(int status, @NotNull BluetoothGattService service) {

        }

        @Override
        public void onCharacteristicRead(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
            ServiceImplementation serviceImplementation = serviceImplementations.get(characteristic.getService());
            if (serviceImplementation != null) {
                serviceImplementation.onCharacteristicRead(central, characteristic);
            }
        }

        @Override
        public int onCharacteristicWrite(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value) {
            ServiceImplementation serviceImplementation = serviceImplementations.get(characteristic.getService());
            if (serviceImplementation != null) {
                return serviceImplementation.onCharacteristicWrite(central, characteristic, value);
            }
            return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
        }

        @Override
        public void onDescriptorRead(@NotNull Central central, @NotNull BluetoothGattDescriptor descriptor) {
            BluetoothGattCharacteristic characteristic = Objects.requireNonNull(descriptor.getCharacteristic(), "Descriptor has no Characteristic");
            BluetoothGattService service = Objects.requireNonNull(characteristic.getService(), "Characteristic has no Service");
            ServiceImplementation serviceImplementation = serviceImplementations.get(service);
            if (serviceImplementation != null) {
                serviceImplementation.onDescriptorRead(central, descriptor);
            }
        }

        @Override
        public int onDescriptorWrite(@NotNull Central central, @NotNull BluetoothGattDescriptor descriptor, @NotNull byte[] value) {
            BluetoothGattCharacteristic characteristic = Objects.requireNonNull(descriptor.getCharacteristic(), "Descriptor has no Characteristic");
            BluetoothGattService service = Objects.requireNonNull(characteristic.getService(), "Characteristic has no Service");
            ServiceImplementation serviceImplementation = serviceImplementations.get(service);
            if (serviceImplementation != null) {
                return serviceImplementation.onDescriptorWrite(central, descriptor, value);
            }
            return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
        }

        @Override
        public void onNotifyingEnabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
            ServiceImplementation serviceImplementation = serviceImplementations.get(characteristic.getService());
            if (serviceImplementation != null) {
                serviceImplementation.onNotifyingEnabled(central, characteristic);
            }
        }

        @Override
        public void onNotifyingDisabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
            ServiceImplementation serviceImplementation = serviceImplementations.get(characteristic.getService());
            if (serviceImplementation != null) {
                serviceImplementation.onNotifyingDisabled(central, characteristic);
            }
        }

        @Override
        public void onCentralConnected(@NotNull Central central) {
            for (ServiceImplementation serviceImplementation : serviceImplementations.values()) {
                serviceImplementation.onCentralConnected(central);
            }
        }

        @Override
        public void onCentralDisconnected(@NotNull Central central) {
            for (ServiceImplementation serviceImplementation : serviceImplementations.values()) {
                serviceImplementation.onCentralDisconnected(central);
            }
        }
    };

    public void startAdvertising(UUID serviceUUID) {
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(serviceUUID))
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        peripheralManager.startAdvertising(advertiseSettings, scanResponse, advertiseData);
    }

    private void setupServices() {
        for (BluetoothGattService service : serviceImplementations.keySet()) {
            peripheralManager.add(service);
        }
    }

    private final HashMap<BluetoothGattService,ServiceImplementation> serviceImplementations = new HashMap<>();

    BluetoothServer(Context context) {
        this.context = context;

        // Plant a tree
        Timber.plant(new Timber.DebugTree());

        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            Timber.e("not supporting advertising");
        }

        bluetoothAdapter.setName("Nokia 8");
        this.peripheralManager = new PeripheralManager(context, bluetoothManager, peripheralManagerCallback);

        DeviceInformationService dis = new DeviceInformationService(peripheralManager);
        CurrentTimeService cts = new CurrentTimeService(peripheralManager);
        HeartRateService hrs = new HeartRateService(peripheralManager);
        GenericHealthSensorService ghs = new GenericHealthSensorService(peripheralManager);
        serviceImplementations.put(dis.getService(), dis);
        serviceImplementations.put(cts.getService(), cts);
        serviceImplementations.put(hrs.getService(), hrs);
        serviceImplementations.put(ghs.getService(), ghs);

        setupServices();
        startAdvertising(ghs.getService().getUuid());
    }
}
