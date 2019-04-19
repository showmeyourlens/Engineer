package com.example.android.bluetoothlegatt;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsChangeHandler {
    private double mDistance = 0.0;
    private String mPassword = "";




    public double distanceChange(LayoutInflater inflater, final Context context, double distance) {
        final AlertDialog.Builder mBuilder = new AlertDialog.Builder(context);
        final View mView = inflater.inflate(R.layout.distance_dialog, null);
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
                    setDistance(Double.valueOf(mDistance.getText().toString()));
                    Toast toast = Toast.makeText(context, "Distance changed", Toast.LENGTH_SHORT);
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
                Toast.makeText(context, "Distance has not been changed", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        dialog.show();
        return getDistance();
    }

    private void setDistance(double distance){
        mDistance = distance;
        DeviceControlActivity.distance_threshold = distance;
    }

    private double getDistance(){
        return mDistance;
    }


}
