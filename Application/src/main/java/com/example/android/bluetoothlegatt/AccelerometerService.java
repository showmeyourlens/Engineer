package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.TextView;

import java.util.Vector;

import static java.lang.Math.abs;


public class AccelerometerService extends IntentService implements SensorEventListener {

    public static float MOVING_ACCELEROMETER_SEED = 6;
    private SensorManager manager;
    private TextView output;
    private Sensor sensor;
    private String outputString;
    private Vector <Float> oldValues = new Vector<>();
    private Vector <Float> newValues = new Vector<>();

    public AccelerometerService() {
        super(AccelerometerService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            manager.registerListener(this, sensor, manager.SENSOR_DELAY_NORMAL);

        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        float diff = 0;
        newValues = new Vector<>();
        for (int i=0; i<sensorEvent.values.length; i++) {
            newValues.add(sensorEvent.values[i]);

        }
        if (oldValues != null) {
            for (int i=0; i<oldValues.size(); i++)
            {
                diff += abs(oldValues.elementAt(i) - newValues.elementAt(i));
            }
        }
        oldValues.clear();
        for (int i=0; i<newValues.size(); i++)
        {
            oldValues.add(newValues.elementAt(i));
        }
        newValues.clear();

        isDeviceMoving(diff);

    }

    private void isDeviceMoving(float diff) {

        String resp = diff > MOVING_ACCELEROMETER_SEED ? "Moving" : "Not moving";
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, resp);
            sendBroadcast(sendIntent);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }


    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

    }

    public class LocalBinder extends Binder {
        AccelerometerService getService() {
            return AccelerometerService.this;
        }
    }

}
