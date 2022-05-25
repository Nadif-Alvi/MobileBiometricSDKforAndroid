package com.dermalog.hardware;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class PrinterActivity extends AppCompatActivity {

    EditText txt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.activity_printer_title);
        }

        txt = findViewById(R.id.editText);

        findViewById(R.id.btnPrintTest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PrintTask task = new PrintTask();
                task.execute();
            }
        });


        findViewById(R.id.btnPrintText).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = txt.getText().toString().trim();
                if(text.length() > 0) {
                    PrintTask task = new PrintTask();
                    task.execute(text);
                }
            }
        });
    }

    class PrintTask extends AsyncTask<String, Void, Throwable> {
        @Override
        protected Throwable doInBackground(String... params) {
            HALDevice device = DeviceManager.getDevice(PrinterActivity.this);
            PowerManager powerManager = device.getPowerManager();
            if (powerManager != null && powerManager.isPowerTypeSupported(PowerManager.PowerType.THERMAL_PRINTER)) {
                powerManager.open();
                powerManager.power(PowerManager.PowerType.THERMAL_PRINTER, true);
            }

            ThermalPrinter printer = device.getThermalPrinter();
            if (printer == null) {
                return null;
            }
            try {
                Bitmap bmp = BitmapFactory.decodeStream(getAssets().open("logo-bw.png"));

                printer.claim();

                if(params.length > 0) {
                    printer.setAlignment(ThermalPrinter.Alignment.Left).addString(params[0]).print().feedLine(10);
                }else{
                    printer.setAlignment(ThermalPrinter.Alignment.Center)
                            .addImage(bmp)
                            .addString(Build.MODEL)
                            .setAlignment(ThermalPrinter.Alignment.Left)
                            .addString("Printer")
                            .setAlignment(ThermalPrinter.Alignment.Right)
                            .addString("Test")
                            .print()
                            .feedLine(10);
                }
                printer.release();


                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return e;
            }finally {
                if (powerManager != null && powerManager.isPowerTypeSupported(PowerManager.PowerType.THERMAL_PRINTER)) {
                    powerManager.power(PowerManager.PowerType.THERMAL_PRINTER, false);
                    powerManager.close();
                }
            }
        }

        @Override
        protected void onPostExecute(Throwable error) {
            if(error != null)
                Toast.makeText(PrinterActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
