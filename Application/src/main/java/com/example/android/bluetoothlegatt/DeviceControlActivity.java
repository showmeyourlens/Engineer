/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final int TRESHOLD_RSSI = -65;
    private static final int CAR_OPENING_COUNTER = 5;

    private Button hardOpenButton;
    private TextView mConnectionState;
    private TextView mDataField;
    private TextView mRSSI;
    private TextView mAccelerometer;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;
    private Service mAccelerometerService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic bluetoothGattCharacteristicHM_10;
    private String password = "pass";
    private double distance = 0.0;
    private boolean areReceiversRegistered = false;
    private int rssiValue = 0;
    private boolean isMoving = false;
    private int counter = 0;
    private TimerTask connectionMonitor;
    private Timer timer;


    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }

        mDeviceName = "HM-10";
        mDeviceAddress = "D4:36:39:DA:F8:12";

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        mRSSI = (TextView) findViewById(R.id.RSSI_value);
        mAccelerometer = (TextView) findViewById(R.id.Accelerometer_value);
        hardOpenButton = findViewById(R.id.hardOpenButton);
        getActionBar().setTitle(mDeviceName);
        // getActionBar().setDisplayHomeAsUpEnabled(true);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mBLSServiceConnection, BIND_AUTO_CREATE);

        Intent accServiceIntent = new Intent(this, AccelerometerService.class);
        bindService(accServiceIntent, mAccServiceConnection, BIND_AUTO_CREATE);

        if (connectionMonitor == null) {
            connectionMonitor = new TimerTask() {
                @Override
                public void run() {
                    if (mConnected == false)
                        mBluetoothLeService.connect(mDeviceAddress);
                }
            };
            timer = new Timer();
            timer.schedule(connectionMonitor, 1000, 5000);
        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mBLSServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final ServiceConnection mAccServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            AccelerometerService.LocalBinder binder = (AccelerometerService.LocalBinder) service;
            mAccelerometerService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAccelerometerService = null;
        }
    };

    private final BroadcastReceiver mAccUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isMoving = intent.getBooleanExtra(Intent.EXTRA_TEXT, false);
            displayIsMoving(isMoving);
        }
    };

    private void displayIsMoving(boolean isMoving) {
        String response = isMoving ? "yes" : "no";
        mAccelerometer.setText(response);
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_RSSI_AVAILABLE.equals(action)) {
                handleRSSI(intent.getIntExtra(BluetoothLeService.EXTRA_RSSI, -100));
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                if (bluetoothGattCharacteristicHM_10 != null) {
                    final String msg = bluetoothGattCharacteristicHM_10.getStringValue(0);
                    if (!msg.equals("passreq")) {
                        bluetoothGattCharacteristicHM_10.setValue(msg);
                    } else {
                        bluetoothGattCharacteristicHM_10.setValue(password);
                    }
                    mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
                    mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
                    displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                }
            }
        }
    };

    private void clearUI() {
        // mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }



    @Override
    protected void onResume() {
        super.onResume();
        if (!areReceiversRegistered) {
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            registerReceiver(mAccUpdateReceiver, makeAccUpdateIntentFilter());
            areReceiversRegistered = true;
        }
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (areReceiversRegistered) {
            unregisterReceiver(mGattUpdateReceiver);
            unregisterReceiver(mAccUpdateReceiver);
            areReceiversRegistered = false;
        }
        unbindService(mBLSServiceConnection);
        mBluetoothLeService = null;
        unbindService(mAccServiceConnection);
        mAccelerometerService = null;
        connectionMonitor.cancel();
        timer.cancel();
        connectionMonitor = null;
        timer = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
            hardOpenButton.setEnabled(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
            hardOpenButton.setEnabled(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.distance_change:
                distanceChange();
                return true;
            case R.id.password_change:
                passwordChange();
                return true;
            case R.id.menu_connect:
               // mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;


        }
        return super.onOptionsItemSelected(item);
    }

    private void distanceChange() {
        final AlertDialog.Builder mBuilder = new AlertDialog.Builder(DeviceControlActivity.this);
        final View mView = getLayoutInflater().inflate(R.layout.distance_dialog, null);
        final EditText mDistance = (EditText) mView.findViewById(R.id.etDistance);
        mDistance.setText(String.valueOf(distance));
        Button mOK = (Button) mView.findViewById(R.id.OKButton);
        Button mCancel = (Button) mView.findViewById(R.id.CancelButton);
        mBuilder.setView(mView);
        final AlertDialog dialog = mBuilder.create();
        mOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mDistance.getText().toString().isEmpty()) {
                    distance = Double.valueOf(mDistance.getText().toString());
                    Toast toast = Toast.makeText(DeviceControlActivity.this, "Distance changed", Toast.LENGTH_SHORT);
                    toast.setGravity(0, 0, Gravity.END);

                    dialog.cancel();
                    toast.show();
                } else {
                    Toast.makeText(DeviceControlActivity.this, "Please fill the box", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(DeviceControlActivity.this, "Distance has not been changed", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void passwordChange() {

        final AlertDialog.Builder mBuilder = new AlertDialog.Builder(DeviceControlActivity.this);
        final View mView = getLayoutInflater().inflate(R.layout.password_dialog, null);
        final EditText mPassword = (EditText) mView.findViewById(R.id.etPassword);
        Button mOK = (Button) mView.findViewById(R.id.OKButton);
        Button mCancel = (Button) mView.findViewById(R.id.CancelButton);
        mBuilder.setView(mView);
        final AlertDialog dialog = mBuilder.create();
        mOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mPassword.getText().toString().isEmpty()) {
                    Toast.makeText(DeviceControlActivity.this, "Password changed", Toast.LENGTH_SHORT).show();
                    dialog.cancel();
                } else {
                    Toast.makeText(DeviceControlActivity.this, "Please fill the box", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(DeviceControlActivity.this, "Password has not been changed", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        dialog.show();
    }


    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }


    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    private void handleRSSI(int data) {
        //mRSSI.setText(calculateDistance(data));
        mRSSI.setText(String.valueOf(data));
        rssiValue = data;
        if (isMoving && rssiValue > TRESHOLD_RSSI && counter > CAR_OPENING_COUNTER && bluetoothGattCharacteristicHM_10 != null){
            bluetoothGattCharacteristicHM_10.setValue("open");
            mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
            mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
            counter = 0;
        }
        else if (counter > CAR_OPENING_COUNTER && bluetoothGattCharacteristicHM_10 != null){
            bluetoothGattCharacteristicHM_10.setValue("close");
            mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
            mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
            counter = 0;
        }
        else if (counter < CAR_OPENING_COUNTER){counter ++;}
        else counter++;

    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;

        for (BluetoothGattService gattService : gattServices) {

            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                uuid = gattCharacteristic.getUuid().toString();
                if (uuid.equals(SampleGattAttributes.HM_10)) {
                    bluetoothGattCharacteristicHM_10 = gattService.getCharacteristic(BluetoothLeService.UUID_HEART_RATE_MEASUREMENT);

                    // ------------- Żeby czytał charakterystyke HM-10 -----------------------------------
                    final int charaProp = bluetoothGattCharacteristicHM_10.getProperties();
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        // If there is an active notification on a characteristic, clear
                        // it first so it doesn't update the data field on the user interface.
                        if (mNotifyCharacteristic != null) {
                            mBluetoothLeService.setCharacteristicNotification(
                                    mNotifyCharacteristic, false);
                            mNotifyCharacteristic = null;
                        }
                        mBluetoothLeService.readCharacteristic(bluetoothGattCharacteristicHM_10);
                    }
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                        mNotifyCharacteristic = bluetoothGattCharacteristicHM_10;
                        mBluetoothLeService.setCharacteristicNotification(
                                bluetoothGattCharacteristicHM_10, true);
                    }

                }
            }
        }
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_RSSI_AVAILABLE);
        return intentFilter;
    }

    private static IntentFilter makeAccUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SEND);
        return intentFilter;
    }

    public void hardOpen(View view) {
        if (bluetoothGattCharacteristicHM_10 != null) {
            bluetoothGattCharacteristicHM_10.setValue("open");
            mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
            mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
        }

    }

    public String calculateDistance(String stringRssi) {

        if (stringRssi == null) return "err";
        double rssi = Double.parseDouble(stringRssi);
        double txPower = -62; //hard coded power value. Usually ranges between -59 to -65

        if (rssi == 0) {
            return String.valueOf(-1.0);
        }

        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return String.valueOf(Math.pow(ratio, 10));
        } else {
            double distance = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
            return String.valueOf(distance);
        }
    }
}
