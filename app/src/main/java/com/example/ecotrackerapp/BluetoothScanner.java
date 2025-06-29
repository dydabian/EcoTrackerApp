package com.example.ecotrackerapp;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class BluetoothScanner {

    private final Activity activity;
    private final Context context;
    private static final long SCAN_PERIOD = 5_000;  //how long app will scan for bluetooth devices
    private static final int MANUFACTURER_ID = 307;  //blue maestro unique manufacturer ID
    private static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");  // notify from device
    private static final UUID RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");  // write to device

    private ArrayList<Float> sensorData = new ArrayList<>();
    private int readingCount;
    private final List<BluetoothDevice> deviceList = new ArrayList<>();

    private BluetoothDevice tempSensor;
    private BluetoothGatt blGatt;
    private BluetoothAdapter adapter;
    private ScanCallback scanCallback;

    private BluetoothCommand command =null;
    private static final String DOWNLOAD_COMMAND="*logall";
    private static final String CLEAR_COMMAND = "*clr";
    private static final String INFO_COMMAND = "*tell";
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothRequestCallback callback;

    private BluetoothStatusListener bleListener;

    public BluetoothScanner(Activity activity, BluetoothStatusListener listener) {
        this.bleListener = listener;
        this.activity = activity;
        this.context = activity.getApplicationContext();

        BluetoothManager manager = ContextCompat.getSystemService(this.context, BluetoothManager.class);
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public List<BluetoothDevice> getDeviceList() {return deviceList;}
    public void setSensor(BluetoothDevice sensor) {tempSensor = sensor;}

    public String getSensorResponse() {return command.getResponse();}
    public boolean hasSensor() {return tempSensor != null;}

    public ArrayList<Float> getSensorData() {
        sensorData.removeAll(Collections.singleton(null));
        return sensorData;}

    public String getSensorName() {
        if (tempSensor != null) {
            return tempSensor.getName();
        }
        else {
            return null;
        }
    }

    public BluetoothDevice getSensor() {
        return tempSensor;
    }
    public boolean isSupported() {
        return adapter != null;
    }
    public boolean enabled() {
        return adapter.isEnabled();
    }

    public void requestPermissions() {
        //request bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 (API 31)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, 1001);
            }
        }
    }

    public boolean hasCorrectPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    public void scan(ScanResultListener listener) {
        deviceList.clear();
        bleListener.onStatusChanged("scanning",0,0);

        if (scanCallback == null) {
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice device = result.getDevice();

                    if (!deviceList.contains(device)) {
                        deviceList.add(device);
                    }

                }
            };
        }

        adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothLeScanner bleScanner = adapter.getBluetoothLeScanner();

        ScanFilter filter = new ScanFilter.Builder()
                .setManufacturerData(MANUFACTURER_ID, new byte[]{})
                .build();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        bleScanner.startScan(filters, settings, scanCallback);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            bleScanner.stopScan(scanCallback);
            listener.onScanComplete(deviceList);
        }, SCAN_PERIOD);
    }

    public interface ScanResultListener {
        void onScanComplete(List<BluetoothDevice> devices);
    }



    public void setCommand(String cmdText) {
        bleListener.onStatusChanged("connecting", 0, 0);
        command = new BluetoothCommand(cmdText);
        adapter = BluetoothAdapter.getDefaultAdapter();
        blGatt = tempSensor.connectGatt(context, false, gattCallback);

        if (blGatt == null) {
            bleListener.onStatusChanged("connection fail", 0, 0);
        }
    }

    private boolean waitForCommandToFinish() {
        int wait_ms = 10_000;
        int elapsed = 0;
            while (!command.done()) {
                try {
                    Thread.sleep(500);
                    elapsed += 500;
                    if (elapsed > wait_ms) {
                        command.setFail(true);
                        break;
                    }
                } catch (InterruptedException e) {
                    bleListener.onStatusChanged("send fail", 0, 0);
                    break;
                }
            }
        return command.succeeded();
    }


    public boolean downloadData() {
        sensorData.clear();
        setCommand(DOWNLOAD_COMMAND);
        return waitForCommandToFinish();
    }

    public boolean clearData() {
        setCommand(CLEAR_COMMAND);
        return waitForCommandToFinish();
    }

    public boolean getSensorSettings() {
        setCommand(INFO_COMMAND);
        return waitForCommandToFinish();
    }


    // Write command to TX characteristic
    private void sendCommand() {
        Log.d("BLE", "Sending command: " + command.getText());
        bleListener.onStatusChanged("sending",0,0);
        BluetoothGattService service = blGatt.getService(SERVICE_UUID);

        if (service == null) {
            Log.d("BLE", "Failed to connect");
            return;
         }
        byte[] bytes = command.asBytes();
        BluetoothGattCharacteristic rxChar = service.getCharacteristic(RX_CHAR_UUID);
        if (rxChar == null) {
            Log.d("BLE", "Failed to get write characteristic");
            return;
         }

        new Thread(() -> {
            int attempt = 1;
            while (!command.sent()) {
                Log.d("SEND", "command sent: " + command.sent());
                rxChar.setValue(bytes);
                command.setSent(blGatt.writeCharacteristic(rxChar));
                attempt ++;

                if (command.sent()) {
                    break;
                }
                if (attempt > 3){
                    command.setFail(true);
                    bleListener.onStatusChanged("send fail", 0, 0);
                    break;
                }
                try {
                    Thread.sleep(1000); // wait 1000ms before retrying
                } catch (InterruptedException e) {
                    bleListener.onStatusChanged("send fail", 0, 0);
                    break;
                }
            }
        }).start();
    }

    // Enable notifications on RX characteristic to receive responses
    private void enableNotifications() {
        BluetoothGattService service = blGatt.getService(SERVICE_UUID);

        if (service == null) {
            Log.d("BLE", "Unable to get service");
            return;
        }

        BluetoothGattCharacteristic txChar = service.getCharacteristic(TX_CHAR_UUID);
        if (txChar == null) {
            Log.d("BLE", "Unable to get TX characteristic");
            return;
        }

        blGatt.setCharacteristicNotification(txChar, true);
        BluetoothGattDescriptor descriptor = txChar.getDescriptor(CCCD);

        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean written = blGatt.writeDescriptor(descriptor);
            Log.d("BLE", written + descriptor.toString());
        }
        else {
            Log.d("BLE", "BLE descriptor not found");
        }

        Log.d("BLE", "Notifications enabled");
        if (command !=null  && !command.sent()) {
            sendCommand();
        }
    }
    @SuppressLint("NewApi")
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("BLE", "status " + status + " state" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bleListener.onStatusChanged("connected",0,0);
                blGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED  && !command.done()) {
                command.setFail(true);
                bleListener.onStatusChanged("disconnect",0,0 );
            }
            else if (status == 133) {
                bleListener.onStatusChanged("connection fail",0,0);
                command.setFail(true);
                Log.d("BLE", String.valueOf(newState));
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableNotifications();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                command.setSent(true);
                Log.d("BLE","Command sent successfully");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            if (characteristic.getUuid().equals(TX_CHAR_UUID)) {
                byte[] response = characteristic.getValue();

                if (command.getText().equals(DOWNLOAD_COMMAND)  && !command.done()) {
                    if (response.length == 20) {
                        appendTemperatureReading(response);
                    }
                    else {
                        setReadingCount(response);
                    }
                }
                else if (command.getText().equals(INFO_COMMAND)) {
                    command.appendToResponse(response);
                    Log.d("RESPONSE", command.getResponse());
                    command.setSuccess(true);
                }
                else if (command.getText().equals(CLEAR_COMMAND)) {
                    command.appendToResponse(response);
                    String txtResponse = new String(response, java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (txtResponse.equals("OK")) {
                        command.setSuccess(true);
                    }
                }
            }
        }
    };

    private void setReadingCount(byte[] data) {
        int count = convertToInt16(data[4], data[5]);

        readingCount = count/10;
        Log.d("DOWNLOAD","count: " + count);
        if (count % 10 >= 5) {
            readingCount++;
        }
    }

    private void appendTemperatureReading(byte[] data) {
        float temperature =convertTemp(data[8], data[9]);
        sensorData.add(temperature);
        bleListener.onStatusChanged("downloading", sensorData.size(), readingCount);
        Log.d("TEMPERATURE", "Temperature received: " + temperature + " "  +  sensorData.size() + " of " + readingCount);
        if (sensorData.size() == readingCount) {
            command.setSuccess(true);
            Log.d("TEMPERATURE", "All data received");
        }
    }
    private int convertToInt16(byte first, byte second){
        int value = (int) first & 0xFF;
        value *= 256;
        value += (int) second & 0xFF;
        return value;
    }
    public float convertTemp(byte byteOne, byte byteTwo) {
        int value = byteOne & 0xFF;  // ensure unsigned byte
        value = value * 256;         // shift left by 8 bits
        value += byteTwo & 0xFF;     // add second byte

        if (value > 32768) {
            value -= 65536;          // correct for signed range
        }

        return (float) value /10;
    }

    public void promptUserToEnable(BluetoothRequestCallback callback) {
        this.callback = callback;
        //prompt user to turn on bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }


    protected void onActivityResult(int requestCode, int resultCode) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                if (callback != null) callback.onBluetoothEnabled();
            } else {
                if (callback != null) callback.onBluetoothDenied();
            }
        }
    }
}