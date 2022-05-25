package com.dermalog.hardware;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class PowerManagerActivity extends AppCompatActivity {
    private MyPowerSwitcherTask task;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_manager);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.activity_powermanager_title);
        }

        DeviceManager.getDevice(this).getPowerManager().open();

        PowerTypeAdapter adapter = new PowerTypeAdapter(DeviceManager.getDevice(this).getPowerManager().supportedPowerTypes());
        ListView lv = (ListView) findViewById(R.id.powerTypes);
        lv.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            DeviceManager.getDevice(this).getPowerManager().close();
        } catch (Throwable e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Switching power on");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
    }

    private void hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    private class MyPowerSwitcherTask extends PowerSwitcherTask {
        private final TextView textView;
        private final String text;

        MyPowerSwitcherTask(Context context, PowerManager.PowerType powerType, TextView textView, String text) {
            super(context, powerType);
            this.textView = textView;
            this.text = text;
        }

        @Override
        protected void onPreExecute() {
            showProgressDialog();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            hideProgressDialog();

            if (result) {
                textView.setText(text);
            } else {
                Toast.makeText(PowerManagerActivity.this, "Power failed: " + getError().getMessage(), Toast.LENGTH_LONG).show();
            }

        }
    }

    class PowerTypeAdapter extends BaseAdapter {
        private final List<PowerManager.PowerType> powerTypes;

        PowerTypeAdapter(List<PowerManager.PowerType> powerTypes) {
            this.powerTypes = powerTypes;
        }

        @Override
        public int getCount() {
            return powerTypes.size();
        }

        @Override
        public Object getItem(int position) {
            return powerTypes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return powerTypes.get(position).ordinal();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.power_type_line, parent, false);
            }
            final PowerManager.PowerType powerType = (PowerManager.PowerType) getItem(position);
            ((TextView) convertView.findViewById(R.id.powerTypeLabel)).setText(powerType.name());

            final TextView statusLabel = (TextView) convertView.findViewById(R.id.status);

            convertView.findViewById(R.id.btnOn).setOnClickListener(v -> {
                task = new MyPowerSwitcherTask(getApplicationContext(), powerType, statusLabel, "ON");
                task.execute(true);
            });

            convertView.findViewById(R.id.btnOff).setOnClickListener(v -> {
                task = new MyPowerSwitcherTask(getApplicationContext(), powerType, statusLabel, "OFF");
                task.execute(false);
            });

            return convertView;
        }
    }
}
