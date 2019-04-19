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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.support.v4.app.NotificationCompat;
import android.util.LayoutDirection;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.security.NoSuchAlgorithmException;
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
    private static final int CAR_OPENING_COUNTER = 5;
    private static final String CHANNEL_ID = "ChannelID";
    private static final CharSequence CHANNEL_NAME = "Channel_name";

    private boolean isCarOpened = false;
    private ImageView carImage;
    private Button hardOpenButton;
    private TextView mConnectionState;
    private TextView mDataField;
    private TextView mRSSI;
    private TextView mAccelerometer;
    private TextView mSafeModeState;
    private String mDeviceName = "HM-10";
    public static String mDeviceAddress = "D4:36:39:DA:F8:12";
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;
    private Service mAccelerometerService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic bluetoothGattCharacteristicHM_10;
    private String HASH = "91b4d142823f7d20c5f08df69122de43f35f057a988d9619f6d3138485c9a203";
    public static String password = "000000";
    public static double distance_threshold = 4.0;
    private boolean areReceiversRegistered = false;
    private int rssiValue = 0;
    private boolean isMoving = false;
    private int counter = 0;
    private TimerTask connectionMonitor;
    private Timer timer;
    boolean isSafeModeOn = false;
    private Context context;
    private LayoutInflater layoutInflater;
    private SettingsChangeHandler settingsChangeHandler;
    private boolean mAccServiceBound = false;
    private boolean mBLEServiceBound = false;
    private NotificationChannel mChannel;
    private Verification verification;
    private String newPassword = "";


    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        settingsChangeHandler = new SettingsChangeHandler();
        layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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



        // Sets up UI references.
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mSafeModeState = (TextView) findViewById(R.id.safe_mode_state);
        mRSSI = (TextView) findViewById(R.id.RSSI_value);
       safeModeState(false);
        hardOpenButton = findViewById(R.id.hardOpenButton);
        //getActionBar().setTitle(mDeviceName);
        // getActionBar().setDisplayHomeAsUpEnabled(true);
        carImage = findViewById(R.id.carImage);
        carImage.setImageResource(R.drawable.car_closed);
        verification = new Verification();


        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mBLSServiceConnection, BIND_AUTO_CREATE);

        Intent accServiceIntent = new Intent(this, AccelerometerService.class);
        bindService(accServiceIntent, mAccServiceConnection, BIND_AUTO_CREATE);

        if (connectionMonitor == null) {
            connectionMonitor = new TimerTask() {
                @Override
                public void run() {
                    if (!mConnected)
                        mBluetoothLeService.connect(mDeviceAddress);
                }
            };
            timer = new Timer();
            timer.schedule(connectionMonitor, 1000, 5000);
        }
    }

    private final ServiceConnection mBLSServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            mBLEServiceBound = true;
            mBluetoothLeService.connect(mDeviceAddress);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            mBLEServiceBound = false;
        }
    };

    private final ServiceConnection mAccServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            AccelerometerService.LocalBinder binder = (AccelerometerService.LocalBinder) service;
            mAccelerometerService = binder.getService();
            mAccServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAccelerometerService = null;
            mAccServiceBound = false;
        }
    };

    private final BroadcastReceiver mAccUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isMoving = intent.getBooleanExtra(Intent.EXTRA_TEXT, false);
           // displayIsMoving(isMoving);
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
                if (!isSafeModeOn) mBluetoothLeService.rssiMeasureManager(true);
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            }
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            }
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            }
            else if (BluetoothLeService.ACTION_RSSI_AVAILABLE.equals(action)) {
                handleRSSI(intent.getIntExtra(BluetoothLeService.EXTRA_RSSI, -100));
            }
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                if (bluetoothGattCharacteristicHM_10 != null) {
                    final String msg = bluetoothGattCharacteristicHM_10.getStringValue(0);
//                    mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
                    mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
 //                   displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                    if (msg.contains("PAIR"))
                    {
                    }
                    if (msg.contains("REQ")){
                       // bluetoothGattCharacteristicHM_10.setValue(verification.charsFromHASH(msg));
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                // this code will be executed after 2 seconds
                                bluetoothGattCharacteristicHM_10.setValue("VERIFY " + verification.charsFromHASH(msg, HASH));
                                mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
                                mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
                            }
                        }, 80);

                    }
                    if (msg.equals("car opened") || msg.equals("car hard opened")){
                        if (!isCarOpened) {
                            isCarOpened = true;
                            carImage.setImageResource(R.drawable.car_open);
                            addNotification();
                        }
                    }
                    if (msg.equals("car closed")){
                        isCarOpened = false;
                        carImage.setImageResource(R.drawable.car_closed);
                    }
                    if (msg.equals("PASSWORD CHANGED")) {
                        password = newPassword;
                        try {
                            HASH = verification.SHA256(password);
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }
        }
    };

    private void clearUI() {
        // mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
//        mDataField.setText(R.string.no_data);
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
        if(isCarOpened) {
            carImage.setImageResource(R.drawable.car_open);
        }
        else{
            carImage.setImageResource(R.drawable.car_closed);
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
            //menu.findItem(R.id.menu_connect).setVisible(false);
            //menu.findItem(R.id.menu_disconnect).setVisible(true);
            hardOpenButton.setEnabled(true);
        } else {
           // menu.findItem(R.id.menu_connect).setVisible(true);
           // menu.findItem(R.id.menu_disconnect).setVisible(false);
            mRSSI.setText("Undefined");
            hardOpenButton.setEnabled(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.distance_change:
                distance_threshold = settingsChangeHandler.distanceChange(layoutInflater, context, distance_threshold);
                return true;
            case R.id.password_change:
                passwordChange(layoutInflater, context, password);
                return true;
            case R.id.pair:
                pair(layoutInflater,context);
                return true;
            case R.id.safe_mode:
                safeModeChange();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;


        }
        return super.onOptionsItemSelected(item);
    }


    private void safeModeChange() {
        final AlertDialog.Builder mBuilder = new AlertDialog.Builder(DeviceControlActivity.this);
        final View mView = getLayoutInflater().inflate(R.layout.safe_mode, null);
        Button mYES = (Button) mView.findViewById(R.id.YESButton);
        Button mNO = (Button) mView.findViewById(R.id.NOButton);
        mBuilder.setView(mView);
        final AlertDialog dialog = mBuilder.create();
        mNO.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                    safeModeState(true);
                    mBluetoothLeService.rssiMeasureManager(false);
                    Toast.makeText(DeviceControlActivity.this, "Distance measure disabled", Toast.LENGTH_SHORT).show();
                    mRSSI.setText("Not measured");
                    unbindAccService();
                    mAccServiceBound = false;
                   // mAccelerometer.setText("Not measured");
                    dialog.cancel();
            }
        });
        mYES.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                safeModeState(false);
                mBluetoothLeService.rssiMeasureManager(true);
                    Toast.makeText(DeviceControlActivity.this, "Distance measure enabled", Toast.LENGTH_SHORT).show();
                    Intent accServiceIntent = new Intent(getApplicationContext(), AccelerometerService.class);
                    bindService(accServiceIntent, mAccServiceConnection, BIND_AUTO_CREATE);
                    dialog.dismiss();
            }
        });
        dialog.show();
    }


    private void unbindAccService() {
        if (mAccServiceBound) {
            unbindService(mAccServiceConnection);
        }
        else {
   //         mAccelerometer.setText("err");
        }
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

    @SuppressLint("DefaultLocale")
    private void handleRSSI(int data) {
        double distance = calculateDistance(data);
        mRSSI.setText(String.format("%.1f", distance));
        if (isMoving && distance < distance_threshold && counter > CAR_OPENING_COUNTER && bluetoothGattCharacteristicHM_10 != null) {
            bluetoothGattCharacteristicHM_10.setValue("open");
            mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
            mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
            counter = 0;
        }
//        else if (counter > CAR_OPENING_COUNTER && bluetoothGattCharacteristicHM_10 != null) {
//            bluetoothGattCharacteristicHM_10.setValue("close");
//            mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
//            mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
//            counter = 0;
//        }
        else
            if (counter > CAR_OPENING_COUNTER)
            {
            counter = 0;
            }
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
                    bluetoothGattCharacteristicHM_10 = gattService.getCharacteristic(BluetoothLeService.HM10_UUID);

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
            bluetoothGattCharacteristicHM_10.setValue("HARDOPEN");
            mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
            mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
        }

    }
    private void addNotification(){
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.sentinel_logo_tiny)
                .setContentTitle("Car status")
                .setContentText("Car has been opened")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[] {1000} );

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mChannel == null){
            mChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);}
            assert manager != null;
            manager.createNotificationChannel(mChannel);
        }
        manager.notify(1 , notification.build());
    }

    public double calculateDistance(int data) {
        double rssi = (double) data;
        double txPower = -61;
        if (rssi == 0) {
            return -1.0;
        }
        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            return (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
        }
    }

    private boolean safeModeState(boolean parameter)
    {
        if (parameter) {
            mSafeModeState.setText("on");
            isSafeModeOn = true;
        } else {
            mSafeModeState.setText("off");
            isSafeModeOn = false;
        }
        return parameter;
    }

    public void passwordChange(LayoutInflater inflater, final Context context, String password) {
        final AlertDialog.Builder mBuilder = new AlertDialog.Builder(context);
        final View mView = inflater.inflate(R.layout.password_dialog, null);
        final EditText mPassword = (EditText) mView.findViewById(R.id.etPassword);
        mPassword.setText(String.valueOf(password));
        Button mOK = (Button) mView.findViewById(R.id.OKButton);
        Button mCancel = (Button) mView.findViewById(R.id.CancelButton);
        mBuilder.setView(mView);

        final AlertDialog dialog = mBuilder.create();
        mOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mPassword.getText().toString().isEmpty()) {
                    verifyPassword(mPassword.getText().toString());
                    Toast toast = Toast.makeText(context, "Password changing...", Toast.LENGTH_SHORT);
                    toast.setGravity(0, 0, 700);
                    dialog.cancel();
                    toast.show();
                } else {
                    Toast.makeText(context, "Please fill the box", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(context, "Password has not been changed", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    public void pair(LayoutInflater inflater, final Context context) {

        final AlertDialog.Builder mBuilder = new AlertDialog.Builder(context);
        final View mView = inflater.inflate(R.layout.pair_dialog, null);
        final EditText etMac = (EditText) mView.findViewById(R.id.etMac);
        final EditText etPassword = (EditText) mView.findViewById(R.id.etPassword);
        etMac.setText(mDeviceAddress);
        Button mOK = (Button) mView.findViewById(R.id.OKButton);
        Button mCancel = (Button) mView.findViewById(R.id.CancelButton);
        mBuilder.setView(mView);
        final AlertDialog dialog = mBuilder.create();
        mOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!etMac.getText().toString().isEmpty() && !etPassword.getText().toString().isEmpty()) {
                    DeviceControlActivity.mDeviceAddress = etMac.getText().toString();
                    bluetoothGattCharacteristicHM_10.setValue("PASS " + etPassword.getText().toString() + " P " + "42352");
                    mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
                    mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
                    Toast toast = Toast.makeText(context, "Pairing", Toast.LENGTH_SHORT);
                    toast.setGravity(0, 0, 700);
                    dialog.cancel();
                    toast.show();

                } else {
                    Toast.makeText(context, "Please fill boxes", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(context, "Device not paired", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void verifyPassword(String newPwd){
        bluetoothGattCharacteristicHM_10.setValue("PASS " + password + " C " + newPwd);
        mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_10);
        mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_10, true);
        newPassword = newPwd;


    }
}
