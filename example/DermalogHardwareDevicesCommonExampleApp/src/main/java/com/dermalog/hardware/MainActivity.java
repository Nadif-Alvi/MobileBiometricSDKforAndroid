package com.dermalog.hardware;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {
    private static final int BARCODE_REQUEST_CODE = 721;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (BuildConfig.DEBUG)
            System.setProperty("DERMALOG_SDK_LOG", "1");

        HALDevice device;
        if (BuildConfig.HALDEVICE.isEmpty()) {
            device = DeviceManager.getDevice(this);
        } else {
            device = DeviceManager.getDevice(this, BuildConfig.HALDEVICE);
        }


        findViewById(R.id.btnGoToFingerPrintExample).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, FingerprintScannerActivity.class);
                startActivity(intent);
            }
        });

        Button btn = (Button) findViewById(R.id.btnGoToPowerManagerExample);
        if (device.getPowerManager() != null && device.getPowerManager().supportedPowerTypes().size() > 0) {
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, PowerManagerActivity.class);
                    startActivity(intent);
                }
            });
            btn.setEnabled(true);
        } else {
            btn.setVisibility(View.GONE);
        }

        //Thermal printer
        btn = (Button) findViewById(R.id.btnPrinter);
        if (device.getThermalPrinter() != null) {
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, PrinterActivity.class);
                    startActivity(intent);
                }
            });
            btn.setEnabled(true);
        } else {
            btn.setVisibility(View.GONE);
        }

        //Magnetic card
        btn = (Button) findViewById(R.id.btnMagneticCard);
        if (device.getMagneticCardReader() != null) {
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, MagneticCardActivity.class);
                    startActivity(intent);
                }
            });
            btn.setEnabled(true);
        } else {
            btn.setVisibility(View.GONE);
        }

        //card reader
        findViewById(R.id.btnGoToCardReaderManagerExample).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CardReaderManagerActivity.class);
                startActivity(intent);
            }
        });

        HALDevice commonDev = new com.dermalog.hardware.generic.HALDeviceImpl(this);
        String version = BuildConfig.VERSION_NAME + "\n" + DeviceManager.getVersion() + "\n" + device.getVersionName() + "\n" + device.getVariant();

        ((TextView) findViewById(R.id.deviceName)).setText("APP\nHAL\n" + device.getDeviceType().name() + "\nVariant");
        ((TextView) findViewById(R.id.version)).setText(version);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
