package com.dermalog.hardware;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.dermalog.afis.fingercode3.Encoder;
import com.dermalog.afis.fingercode3.FC3Exception;
import com.dermalog.afis.fingercode3.Matcher;
import com.dermalog.afis.fingercode3.Template;
import com.dermalog.afis.imagecontainer.DICException;
import com.dermalog.afis.imagecontainer.Decoder;
import com.dermalog.afis.imagecontainer.EncoderWsq;
import com.dermalog.afis.imagecontainer.RawImage;
import com.dermalog.afis.nistqualitycheck.Functions;
import com.dermalog.biometricpassportsdk.BiometricPassportException;
import com.dermalog.biometricpassportsdk.BiometricPassportSdkAndroid;
import com.dermalog.biometricpassportsdk.Device;
import com.dermalog.biometricpassportsdk.enums.DeviceId;
import com.dermalog.biometricpassportsdk.enums.FeatureId;
import com.dermalog.biometricpassportsdk.usb.permission.DevicePermission;
import com.dermalog.biometricpassportsdk.utils.BitmapUtil;
import com.dermalog.biometricpassportsdk.wrapped.DeviceFeature;
import com.dermalog.biometricpassportsdk.wrapped.DeviceInfo;
import com.dermalog.biometricpassportsdk.wrapped.arguments.EventArgument;
import com.dermalog.biometricpassportsdk.wrapped.arguments.ImageArgument;
import com.dermalog.common.exception.ErrorCodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class FingerprintScannerActivity extends AppCompatActivity {
    static final String TAG = "FingerprintScanner";
    static final int SIDE_UNKNOWN = -1;
    static final int SIDE_LEFT = 0;
    static final int SIDE_RIGHT = 1;


    private int captureSide = SIDE_UNKNOWN;
    private Bitmap bmpLeft, bmpRight;
    private Button btnCaptureLeft, btnCaptureRight;
    private Button btnCancelLeft, btnCancelRight;
    private TextView txtScore, txtLeft, txtRight, txtMessage;
    private ImageView imgPrintLeft, imgPrintRight;
    private ProgressDialog progressDialog;

    private ProcessImageTask imageTask;


    //BiometricPassportSDK
    private BiometricPassportSdkAndroid biometricsSdk;
    private Device scannerHandle;

    //ImageContainer
    private Decoder icDecoder;
    private com.dermalog.afis.imagecontainer.Encoder icWsqEncoder;

    //FingerCode3
    private Encoder fc3Encoder;
    private Matcher fc3Matcher;
    private Template fc3TemplateLeft, fc3TemplateRight;

    void shareBitmap(Bitmap bmp) {
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Captured.png");
        try (FileOutputStream outStream = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.flush();

            Uri uri = FileProvider.getUriForFile(FingerprintScannerActivity.this, "com.dermalog.hardware.fileprovider", file);
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_SEND);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(Intent.createChooser(intent, "Share"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        setContentView(R.layout.activity_fingerprint_scanner);

        imgPrintLeft = findViewById(R.id.imgPrintLeft);
        imgPrintRight = findViewById(R.id.imgPrintRight);

        btnCaptureLeft = findViewById(R.id.btnStartCaptureLeft);
        btnCaptureRight = findViewById(R.id.btnStartCaptureRight);
        btnCancelLeft = findViewById(R.id.btnCancelLeft);
        btnCancelRight = findViewById(R.id.btnCancelRight);
        btnCaptureLeft.setEnabled(false);
        btnCaptureRight.setEnabled(false);

        txtMessage = findViewById(R.id.txtMessage);
        txtScore = findViewById(R.id.score);
        txtLeft = findViewById(R.id.txtLeft);
        txtRight = findViewById(R.id.txtRight);

        Button btnShareRight = findViewById(R.id.btnShareRight);
        btnShareRight.setOnClickListener(v -> shareBitmap(bmpRight));

        Button btnShareLeft = findViewById(R.id.btnShareLeft);
        btnShareLeft.setOnClickListener(v -> shareBitmap(bmpLeft));

        btnCaptureRight.setOnClickListener(v -> {
            try {
                startAutoCapture(SIDE_RIGHT);
            } catch (BiometricPassportException e) {
                e.printStackTrace();
                showError(e.getMessage());
            }
        });
        btnCaptureLeft.setOnClickListener(v -> {
            try {
                startAutoCapture(SIDE_LEFT);
            } catch (BiometricPassportException e) {
                e.printStackTrace();
                showError(e.getMessage());
            }
        });

        View.OnClickListener cancelListener = v -> {
            if (scannerHandle != null) {
                try {
                    scannerHandle.stopCapture();
                } catch (BiometricPassportException ignored) {
                }
                onAutoCaptureFinished();
            }
        };

        btnCancelLeft.setOnClickListener(cancelListener);
        btnCancelRight.setOnClickListener(cancelListener);

        com.dermalog.afis.imagecontainer.Android.SetContext(this);
        if (!BuildConfig.LICENSE.isEmpty()) {
            com.dermalog.afis.fingercode3.Android.SetLicense(BuildConfig.LICENSE.getBytes(), this.getApplicationContext());
            com.dermalog.afis.nistqualitycheck.Android.SetLicense(BuildConfig.LICENSE.getBytes(), this.getApplicationContext());
        } else {
            com.dermalog.afis.fingercode3.Android.SetContext(this.getApplicationContext());
            com.dermalog.afis.nistqualitycheck.Android.SetContext(this.getApplicationContext());
        }

        showHint("Initializing...");
        switchPowerOn();
    }

    void initializeSDKs() throws BiometricPassportException, DICException {
        biometricsSdk = new BiometricPassportSdkAndroid(this);

        icDecoder = new Decoder();
        icWsqEncoder = new EncoderWsq();

        try {
            fc3Encoder = new Encoder();
            fc3Encoder.setCodingType(1); //fast encoding
            fc3Matcher = new Matcher();
        } catch (FC3Exception e) {
            e.printStackTrace();
            Toast.makeText(FingerprintScannerActivity.this, "FingerCode3: NO LICENSE", Toast.LENGTH_LONG).show();
            txtScore.setText("FC3\nN/A");
        }
    }

    void getPermissions() {
        biometricsSdk.requestUSBPermissionsAsync(permissionResult -> {
            for (DevicePermission dp : permissionResult.getDevicePermissions()) {
                UsbDevice d = dp.getDevice();
                Log.d(TAG, "Permission for USB device '" + d.getProductName()
                        + "', serial# '" + (dp.isGranted() ? "" : " not") + " granted");
            }

            boolean tryOpen = false;

            switch (permissionResult.getResult()) {
                case NoDevice:
                    showWarning("No scanner attached!");
                    break;
                case NoPermission:
                    showError("No USB permission!");
                    break;
                case PartialPermission:
                    tryOpen = true;
                    showWarning("Not all USB permissions!");
                    break;
                case Success:
                    tryOpen = true;
                    showHint("Press 'CAPTURE'");
                    break;
                case UsbNotSupported:
                    showError("No USB support!");
                    break;
            }

            if (tryOpen) {
                try {
                    openScanner();
                } catch (Exception e) {
                    Toast.makeText(FingerprintScannerActivity.this, "Error opening scanner", Toast.LENGTH_LONG).show();
                    btnCaptureLeft.setEnabled(false);
                    btnCaptureRight.setEnabled(false);
                }
            }

        });
    }

    private void showHint(String s) {
        showMessage(0, s);
    }

    private void showWarning(String s) {
        showMessage(1, s);
    }

    private void showError(final String s) {
        showMessage(2, s);
    }

    private void showMessage(final int type, final String message) {
        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            switch (type) {
                case 1:
                    txtMessage.setTextColor(Color.YELLOW);
                    break;
                case 2:
                    txtMessage.setTextColor(Color.RED);
                    break;

                default:
                    txtMessage.setTextColor(Color.WHITE);
                    break;
            }
            txtMessage.setText(message);
        } else {
            runOnUiThread(() -> showMessage(type, message));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scannerHandle != null) {
            try {
                scannerHandle.stopCapture();
            } catch (BiometricPassportException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uninitializeSDK();
        switchPowerOff();
    }

    void switchPowerOn() {
        PowerManager powerManager = DeviceManager.getDevice(this).getPowerManager();
        if (powerManager != null && powerManager.isPowerTypeSupported(PowerManager.PowerType.USB_FINGERPRINT_SCANNER)) {
            powerManager.open();

            LocalPowerSwitcherTask task = new LocalPowerSwitcherTask(this, PowerManager.PowerType.USB_FINGERPRINT_SCANNER);
            task.execute(true);
        }
    }

    void switchPowerOff() {
        try {
            PowerManager powerManager = DeviceManager.getDevice(this).getPowerManager();
            if (powerManager != null && powerManager.isPowerTypeSupported(PowerManager.PowerType.USB_FINGERPRINT_SCANNER)) {
                powerManager.power(PowerManager.PowerType.USB_FINGERPRINT_SCANNER, false);
                powerManager.close();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void uninitializeSDK() {
        closeScanner();


        if (biometricsSdk != null) {
            biometricsSdk.dispose();
            biometricsSdk = null;
        }

        if (fc3Matcher != null) {
            try {
                fc3Matcher.close();
            } catch (FC3Exception e) {
                e.printStackTrace();
            }
            fc3Matcher = null;
        }

        if (fc3Encoder != null) {
            try {
                fc3Encoder.close();
            } catch (FC3Exception e) {
                e.printStackTrace();
            }
            fc3Encoder = null;
        }

        if (icDecoder != null) {
            try {
                icDecoder.close();
            } catch (DICException e) {
                e.printStackTrace();
            }
            icDecoder = null;
        }

        if (icWsqEncoder != null) {
            try {
                icWsqEncoder.close();
            } catch (DICException e) {
                e.printStackTrace();
            }
            icWsqEncoder = null;
        }


        if (fc3TemplateLeft != null) {
            try {
                fc3TemplateLeft.close();
            } catch (FC3Exception e) {
                e.printStackTrace();
            }
            fc3TemplateLeft = null;
        }

        if (fc3TemplateRight != null) {
            try {
                fc3TemplateRight.close();
            } catch (FC3Exception e) {
                e.printStackTrace();
            }
            fc3TemplateRight = null;
        }

    }

    private void openScanner() throws BiometricPassportException {
        closeScanner();

        // search scanner and open first
        DeviceInfo[] devices = biometricsSdk.enumerateDevices(DeviceId.ALL);
        if (devices.length > 0) {
            scannerHandle = biometricsSdk.createDevice(devices[0]);

            List<DeviceFeature> features = scannerHandle.getSupportedFeatures();

            for (DeviceFeature f : features) {

                switch (f.getId()) {
                    case FINGER_MASK_SENSITIVITY:
                        //Change mask sensitivity. Value depends on used scanner.
                        //Suprema BioSlim 3: 1-7. default: 7
                        scannerHandle.setFeature(FeatureId.FINGER_MASK_SENSITIVITY, 7);
                        break;

                    case FINGER_LOW_CONTRAST_MODE:
                        //Enable Low contrast mode for better dry finger detection (e.g. F1 / ZF1)
                        scannerHandle.setFeature(FeatureId.FINGER_LOW_CONTRAST_MODE, 1);
                        break;
                }

            }


            scannerHandle.registerCallback((device, deviceCallbackEventArgument) -> {

                ImageArgument imageArgument = null;

                //Get detect image
                for (EventArgument ea : deviceCallbackEventArgument.getArguments()) {
                    if (ea instanceof ImageArgument) {
                        imageArgument = (ImageArgument) ea;
                        break;
                    }
                }

                switch (deviceCallbackEventArgument.getEventId()) {

                    case FINGER_IMAGE:
                        showHint("FINGER_IMAGE");
                        if (imageArgument != null) {
                            try {
                                Bitmap bmp = BitmapUtil.fromImageArgument(imageArgument);
                                displayImage(bmp);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        break;

                    case FINGER_DETECT:
                        showHint("FINGER_DETECT");
                        processImage(imageArgument);
                        break;

                    case STOP:
                        break;

                }
            });

            runOnUiThread(() -> {
                btnCaptureLeft.setEnabled(true);
                btnCaptureRight.setEnabled(true);
            });
        }
    }

    void displayImage(final Bitmap bmp) {
        runOnUiThread(() -> {
            switch (captureSide) {
                case SIDE_LEFT:
                    bmpLeft = bmp;
                    imgPrintLeft.setImageBitmap(bmpLeft);
                    break;

                case SIDE_RIGHT:
                    bmpRight = bmp;
                    imgPrintRight.setImageBitmap(bmpRight);
                    break;
            }
        });

    }

    void disableCancel() {
        btnCancelLeft.setEnabled(false);
        btnCancelRight.setEnabled(false);
    }

    void processImage(final ImageArgument imageArgument) {

        if (imageTask != null && imageTask.getStatus() != AsyncTask.Status.FINISHED)
            return;

        imageTask = new ProcessImageTask();
        imageTask.execute(imageArgument);
    }

    private void closeScanner() {
        if (scannerHandle != null) {
            scannerHandle.dispose();
            scannerHandle = null;
        }
    }


    private void startAutoCapture(int side) throws BiometricPassportException {
        captureSide = SIDE_UNKNOWN;

        if (biometricsSdk == null) {
            showError("SDK is null");
            return;
        }

        if (scannerHandle == null) {
            showError("Scanner not opened");
            return;
        }

        btnCaptureLeft.setEnabled(false);
        btnCaptureRight.setEnabled(false);

        if (side == SIDE_LEFT) {
            btnCancelLeft.setVisibility(View.VISIBLE);
            btnCaptureLeft.setVisibility(View.GONE);
        } else if (side == SIDE_RIGHT) {
            btnCancelRight.setVisibility(View.VISIBLE);
            btnCaptureRight.setVisibility(View.GONE);
        } else {
            throw new BiometricPassportException(ErrorCodes.FPC_ERROR_INVALID_PARAMETER, "Invalid parameter: " + side);
        }

        captureSide = side;
        scannerHandle.startCapture();

        showHint("Place finger onto scanner");
    }

    private void onAutoCaptureFinished() {
        btnCaptureLeft.setEnabled(true);
        btnCaptureRight.setEnabled(true);
        btnCaptureLeft.setVisibility(View.VISIBLE);
        btnCaptureRight.setVisibility(View.VISIBLE);

        btnCancelLeft.setVisibility(View.GONE);
        btnCancelLeft.setEnabled(true);
        btnCancelRight.setVisibility(View.GONE);
        btnCancelRight.setEnabled(true);
    }

    void showProgressDialog(String message) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    void hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    class LocalPowerSwitcherTask extends PowerSwitcherTask {
        LocalPowerSwitcherTask(Context context, PowerManager.PowerType powerType) {
            super(context, powerType);
        }

        @Override
        protected void onPreExecute() {
            showProgressDialog("Switching on USB power");
        }

        @Override
        protected void onPostExecute(Boolean result) {
            hideProgressDialog();
            try {
                initializeSDKs();
                getPermissions();

            } catch (Exception e) {
                e.printStackTrace();
                showError("Error creating SDKs: " + e.getMessage());
            }
        }
    }

    //void processImage(final ImageArgument imageArgument) {
    class ProcessImageTask extends AsyncTask<ImageArgument, String, Exception> {

        double score = -1.0;
        int nfiq2 = -1;
        byte[] wsqBytes;

        @Override
        protected void onPreExecute() {
            runOnUiThread(() -> {
                disableCancel();
                showProgressDialog("Processing...");
            });
        }

        @Override
        protected void onProgressUpdate(String... values) {
            progressDialog.setMessage(values[0]);
        }

        @Override
        protected Exception doInBackground(ImageArgument... imageArguments) {
            try {
                scannerHandle.stopCapture();
            } catch (BiometricPassportException e) {
                e.printStackTrace();
            }

            RawImage rawImg = null;

            try {
                ImageArgument imageArgument = imageArguments[0];

                publishProgress("Create Android Bitmap");
                Bitmap bmp = BitmapUtil.fromImageArgument(imageArgument);

                displayImage(bmp);

                //crate RawImage for NistQuality and FingerCode3
                publishProgress("Create RawImage");
                rawImg = icDecoder.Decode(imageArgument.bitmapInfoHeaderData().getRawData());

                //convert image to WSQ
                publishProgress("Convert to WSQ");
                wsqBytes = icWsqEncoder.Encode(rawImg);

                if (fc3Encoder != null) {
                    //check NFIQ2
                    publishProgress("Calculate NFIQ2");
                    nfiq2 = Functions.CheckNfiq2(rawImg);

                    //create template
                    publishProgress("Create Template");
                    Template template = fc3Encoder.Encode(rawImg);

                    switch (captureSide) {
                        case SIDE_LEFT:
                            if (fc3TemplateLeft != null)
                                fc3TemplateLeft.close();
                            fc3TemplateLeft = template;
                            break;

                        case SIDE_RIGHT:
                            if (fc3TemplateRight != null)
                                fc3TemplateRight.close();
                            fc3TemplateRight = template;
                            break;
                    }

                    publishProgress("Match Templates");
                    //match templates
                    if (fc3TemplateLeft != null && fc3TemplateRight != null) {
                        score = fc3Matcher.Match(fc3TemplateLeft, fc3TemplateRight);
                    } else {
                        score = -1.0;
                    }
                } else {
                    score = Double.NEGATIVE_INFINITY;
                }
            } catch (Exception e) {
                e.printStackTrace();
                showError(e.getMessage());
            } finally {
                //free RawImage
                if (rawImg != null) {
                    try {
                        rawImg.close();
                    } catch (DICException ignored) {

                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception e) {
            hideProgressDialog();
            onAutoCaptureFinished();

            if (e != null) {
                showError(e.getMessage());
                return;
            }

            String msg = "WSQ: " + wsqBytes.length;

            if (nfiq2 > 0) {
                msg += "\nNFIQ2: " + nfiq2;
            }

            switch (captureSide) {
                case SIDE_LEFT:
                    txtLeft.setText(msg);
                    break;

                case SIDE_RIGHT:
                    txtRight.setText(msg);
                    break;
            }

            if (score > 0.0f)
                txtScore.setText(String.format(Locale.US, "%.2f", score));
        }
    }
}
