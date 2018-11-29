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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
    private static final int RSSI_PERIOD = 1000;

    private TextView mConnectionState;
    private TextView mDataField;
    private TextView mRSSI;
    private TextView mAccelerometer;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothAdapter mBluetoothAdapter;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private Service mAccelerometerService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic bluetoothGattCharacteristicHM_10;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

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
            // Get extra data included in the Intent
            String message = intent.getStringExtra(Intent.EXTRA_TEXT);
            mAccelerometer.setText(message);
            // Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    };

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
            }

            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            }

            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                displayRSSI(intent.getStringExtra(BluetoothLeService.EXTRA_RSSI));

                if (bluetoothGattCharacteristicHM_10 != null && !intent.getBooleanExtra(BluetoothLeService.isRSSI, false)){
                    //final byte[] rxBytes = bluetoothGattCharacteristicHM_10.getValue();
                    final String msg = bluetoothGattCharacteristicHM_10.getStringValue(0);
                    if (!msg.equals("passreq")) {
                        //bluetoothGattCharacteristicHM_10.setValue(rxBytes);
                        bluetoothGattCharacteristicHM_10.setValue(msg);
                        mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
                        mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
                    }
                    else{
                        bluetoothGattCharacteristicHM_10.setValue("pass");
                        mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
                        mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
                    }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener(){

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
      //  mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
       // mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        mRSSI = (TextView) findViewById(R.id.RSSI_value);
        mAccelerometer = (TextView) findViewById(R.id.Accelerometer_value);
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mBLSServiceConnection, BIND_AUTO_CREATE);

        Intent accServiceIntent = new Intent(this, AccelerometerService.class);
        bindService(accServiceIntent, mAccServiceConnection, BIND_AUTO_CREATE);


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);

        }
        registerReceiver(mAccUpdateReceiver, makeAccUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mBLSServiceConnection);
        mBluetoothLeService = null;
        unbindService(mAccServiceConnection);
        mAccelerometerService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
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

    private void displayRSSI(String data) {
        if (data != null) {
            mRSSI.setText(data);
        }
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
        return intentFilter;
    }

    private static IntentFilter makeAccUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SEND);
        return intentFilter;
    }

    public void hardOpen(View view) {
        if(bluetoothGattCharacteristicHM_10 != null){
            bluetoothGattCharacteristicHM_10.setValue("o");
            mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
            mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10,true);
        }

    }
}
