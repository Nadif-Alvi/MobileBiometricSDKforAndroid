package com.dermalog.hardware;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.dermalog.hardware.magneticcard.MagneticCardException;
import com.dermalog.hardware.magneticcard.MagneticCardReader;
import com.dermalog.hardware.magneticcard.MagneticCardResult;

public class MagneticCardActivity extends AppCompatActivity implements MagneticCardReader.Callback {

    MagneticCardReader reader;

    TextView txt;
    Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_magnetic_card);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.activity_magneticcard_title);
        }

        txt = findViewById(R.id.txtLines);

        btn = findViewById(R.id.btnReadMagneticCard);

        try {
            reader = DeviceManager.getDevice(this).getMagneticCardReader();
            reader.open();

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        btn.setEnabled(false);
                        txt.setText("SWIPE CARD");
                        reader.read(MagneticCardActivity.this, 10000);
                    } catch (MagneticCardException e) {
                        e.printStackTrace();
                        txt.setText(e.getMessage());
                        btn.setEnabled(true);
                    }
                }
            });

        } catch (MagneticCardException e) {
            e.printStackTrace();
            txt.setText(e.getMessage());
            btn.setEnabled(false);
        }

    }

    public void onRead(final MagneticCardResult result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btn.setEnabled(true);

                switch (result.error) {
                    case MagneticCardResult.SUCCESS:
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < result.tracs.length; i++) {
                            if (result.tracs[i] != null) {
                                sb.append("Track " + (i + 1) + ":\n");
                                sb.append(result.tracs[i]).append("\n\n");
                            }
                        }
                        txt.setText(sb.toString());
                        break;

                    case MagneticCardResult.TIMEOUT:
                        txt.setText("TIMEOUT");
                        break;

                    case MagneticCardResult.ERR_INTERNAL:
                        txt.setText("Error: " + result.error + " " + (result.throwable != null ? result.throwable.getMessage() : ""));
                        break;
                }
            }
        }
    );
}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reader != null) {
            try {
                reader.close();
            } catch (MagneticCardException e) {
                e.printStackTrace();
            }
        }
    }
}
