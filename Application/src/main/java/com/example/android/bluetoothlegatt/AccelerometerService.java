package com.example.android.bluetoothlegatt;

import android.app.IntentService;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import static java.lang.Math.abs;

public class AccelerometerService extends IntentService implements SensorEventListener {

    private static final int AVERAGED_VALUES_QUANTITY = 2;
    private static final int BROADCAST_DELAY = 5;
    public static float MOVING_ACCELEROMETER_SEED = 3;
    private SensorManager manager;
    private TextView output;
    private Sensor sensor;
    private String outputString;
    private Vector<Float> oldValues = new Vector<>();
    private Vector<Float> newValues = new Vector<>();
    private Vector<Float> diffVector = new Vector<>();
    int counter = 0;

    public AccelerometerService() {
        super(AccelerometerService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        float diff = 0;
        newValues = new Vector<>();
        for (int i = 0; i < sensorEvent.values.length; i++) {
            newValues.add(sensorEvent.values[i]);

        }
        if (oldValues != null) {
            for (int i = 0; i < oldValues.size(); i++) {
                diff += abs(oldValues.elementAt(i) - newValues.elementAt(i));
            }
        }
        oldValues.clear();
        for (int i = 0; i < newValues.size(); i++) {
            oldValues.add(newValues.elementAt(i));
        }
        newValues.clear();

        isDeviceMoving(diff);
    }

    private void isDeviceMoving(float diff) {
        /*float avgDiff = 0;
        if (diffVector.size() < AVERAGED_VALUES_QUANTITY - 1) {
            diffVector.add(diff);
        } else {
            for (Float value : diffVector) {
                avgDiff += value;
            }

            avgDiff = avgDiff / AVERAGED_VALUES_QUANTITY;
            boolean resp = avgDiff > MOVING_ACCELEROMETER_SEED;*/
        boolean resp = diff > MOVING_ACCELEROMETER_SEED;
        if (counter > BROADCAST_DELAY || resp) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, resp);
            sendBroadcast(sendIntent);
            counter = 0;
        } else
            counter++;
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
