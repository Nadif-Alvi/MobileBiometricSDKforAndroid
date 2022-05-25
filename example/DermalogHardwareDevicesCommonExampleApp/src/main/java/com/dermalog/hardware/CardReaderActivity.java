package com.dermalog.hardware;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dermalog.hardware.cardreader.CardReader;
import com.dermalog.hardware.cardreader.CardReaderManager;
import com.dermalog.hardware.cardreader.CommandApdu;
import com.dermalog.hardware.cardreader.ResponseApdu;
import com.dermalog.hardware.exceptions.CardReaderException;
import com.dermalog.hardware.exceptions.PowerManagerError;
import com.dermalog.hardware.generic.NFCReaderImpl;
import com.dermalog.util.StringUtils;

public class CardReaderActivity extends AppCompatActivity {
    Button open;
    Button close;
    Button powerOn;
    Button powerOff;
    Button present;
    TextView atr;
    EditText cmdApdu;
    Button sendApdu;
    TextView resultApdu;

    String slot;
    CardReader reader;
    PowerManager powerManager;

    int state = STATE_CLOSED;

    private static final int STATE_CLOSED = 0;
    private static final int STATE_OPENED = 1;
    private static final int STATE_POWER_ON = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_reader);
        setTitle("Card Reader");
        Intent intent = getIntent();

        open = (Button) findViewById(R.id.btnOpen);
        close = (Button) findViewById(R.id.btnClose);
        powerOff = (Button) findViewById(R.id.btnPowerOff);
        powerOn = (Button) findViewById(R.id.btnPowerOn);
        present = (Button) findViewById(R.id.btnCardPresent);
        atr = (TextView) findViewById(R.id.atr);
        cmdApdu = (EditText) findViewById(R.id.apduCmd);
        sendApdu = (Button) findViewById(R.id.btnSendApdu);
        resultApdu = (TextView) findViewById(R.id.apduResult);
        updateUiElements();
        powerManager = DeviceManager.getDevice(this).getPowerManager();
        if (powerManager != null) {
            powerManager.open();
        }

        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new OpenDeviceTask(powerManager).execute();
            }
        });
        open.setEnabled(false);

        present.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (reader.isCardPresent()) {
                    Toast.makeText(CardReaderActivity.this, "Card present", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CardReaderActivity.this, "No card present", Toast.LENGTH_SHORT).show();
                }
            }
        });
        present.setEnabled(false);

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (reader.close()) {
                    state = STATE_CLOSED;
                    if (powerManager.isPowerTypeSupported(PowerManager.PowerType.CARD_READER)) {
                        DeviceManager.getDevice(CardReaderActivity.this).getPowerManager().power(PowerManager.PowerType.CARD_READER, false);
                    }
                    updateUiElements();
                }
            }
        });
        powerOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    atr.setText("");

                    if (reader.isCardPresent()) {
                        if (reader.powerOn()) {
                            state = STATE_POWER_ON;
                            updateUiElements();
                            atr.setText(StringUtils.bytesToHexString(reader.getATR()));
                        } else {
                            Toast.makeText(CardReaderActivity.this, "powerOn failed", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(CardReaderActivity.this, "No card present", Toast.LENGTH_SHORT).show();
                    }
                } catch (CardReaderException e) {
                    Toast.makeText(CardReaderActivity.this, "powerOn error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        powerOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    reader.powerOff();
                } catch (CardReaderException e) {
                    Toast.makeText(CardReaderActivity.this, "powerOff failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    state = STATE_OPENED;
                    updateUiElements();
                    atr.setText("");
                    resultApdu.setText("");
                }
            }
        });
        sendApdu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String hex = cmdApdu.getText().toString();
                byte[] bytes = StringUtils.hexStringToBytes(hex);
                SendApduTask t = new SendApduTask();
                t.execute(bytes);
            }
        });

        slot = intent.getStringExtra("SLOT");

        if (DeviceManager.getDevice(this).getCardReaderManager().hasPermissions(slot)) {
            openReader(slot);
        } else {
            DeviceManager.getDevice(this).getCardReaderManager().requestPermissions(slot, new CardReaderManager.OnPermissionRequestCompleted() {
                @Override
                public void result(final String slot, boolean granted) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            openReader(slot);
                        }
                    });
                }
            });
        }
        ((TextView) findViewById(R.id.slot)).setText(slot);
    }

    void openReader(String slot) {
        try {
            reader = DeviceManager.getDevice(CardReaderActivity.this).getCardReaderManager().getCardReader(slot);
            open.setEnabled(true);
        } catch (CardReaderException e) {
            Toast.makeText(CardReaderActivity.this, "Error opening card reader: " + e.getMessage(), Toast.LENGTH_LONG).show();
            open.setEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Android NfcAdapter handling
        if (slot.contains(NFCReaderImpl.READER_NAME)) {
            try {
                NFCReaderImpl.enableForegroundDispatch(this);
            } catch (Throwable t) {
                Toast.makeText(this, "enableForegroundDispatch error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                t.printStackTrace();
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        //Android NfcAdapter handling
        if (reader instanceof NFCReaderImpl) {
            try {
                ((NFCReaderImpl) reader).onNewIntent(intent);
            } catch (Throwable e) {
                Toast.makeText(this, "onNewIntent: " + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        //Android NfcAdapter handling
        if (reader instanceof NFCReaderImpl) {
            NFCReaderImpl.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reader != null) {
            try {
                reader.powerOff();
            } catch (CardReaderException e) {
                e.printStackTrace();
            }
            reader.close();
        }

        if (powerManager != null) {
            try {
                powerManager.close();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            powerManager = null;
        }
    }

    private void updateUiElements() {
        switch (state) {
            case STATE_CLOSED:
                open.setEnabled(true);
                close.setEnabled(false);
                present.setEnabled(false);
                powerOn.setEnabled(false);
                powerOff.setEnabled(false);
                cmdApdu.setEnabled(false);
                sendApdu.setEnabled(false);
                break;
            case STATE_OPENED:
                open.setEnabled(false);
                close.setEnabled(true);
                present.setEnabled(true);
                powerOn.setEnabled(true);
                powerOff.setEnabled(false);
                cmdApdu.setEnabled(false);
                sendApdu.setEnabled(false);
                break;
            case STATE_POWER_ON:
                open.setEnabled(false);
                close.setEnabled(false);
                present.setEnabled(true);
                powerOn.setEnabled(false);
                powerOff.setEnabled(true);
                cmdApdu.setEnabled(true);
                sendApdu.setEnabled(true);
                break;
        }
    }

    private class SendApduTask extends AsyncTask<byte[], Void, Object> {
        @Override
        protected Object doInBackground(byte[]... apdu) {
            try {
                CommandApdu command = new CommandApdu(apdu[0]);
                ResponseApdu response = reader.transceive(command);
                return response.getBytes();

            } catch (CardReaderException e) {
                e.printStackTrace();
                return e;
            }
        }

        @Override
        protected void onPostExecute(Object response) {
            if (response instanceof CardReaderException) {
                CardReaderException ex = (CardReaderException) response;
                resultApdu.setText("Error: " + ex.getMessage());
            } else {
                byte[] bytes = (byte[]) response;
                resultApdu.setText(StringUtils.bytesToHexString(bytes));
            }
        }
    }

    private class OpenDeviceTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog progressDialog;
        private PowerManager powerManager;

        OpenDeviceTask(PowerManager powerManager) {
            this.powerManager = powerManager;
        }

        @Override
        protected void onPreExecute() {
            open.setEnabled(false);
            progressDialog = new ProgressDialog(CardReaderActivity.this);
            progressDialog.setCancelable(false);
            progressDialog.setMessage("Opening card reader");
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                if (powerManager != null && powerManager.isPowerTypeSupported(PowerManager.PowerType.CARD_READER)) {
                    powerManager.power(PowerManager.PowerType.CARD_READER, true);
                }
                return reader.open();
            } catch (Throwable e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                state = STATE_OPENED;
            } else {
                Toast.makeText(CardReaderActivity.this, "open failed", Toast.LENGTH_SHORT).show();
            }
            updateUiElements();
            progressDialog.dismiss();
            progressDialog = null;
        }
    }
}
