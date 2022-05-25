package com.dermalog.hardware;

import android.content.Context;
import android.os.AsyncTask;

public class PowerSwitcherTask extends AsyncTask<Boolean, Void, Boolean> {
    private final Context context;
    private final PowerManager.PowerType powerType;

    private Throwable error;

    public Throwable getError()
    {
        return error;
    }

    protected PowerSwitcherTask(Context context, PowerManager.PowerType powerType) {
        this.context = context;
        this.powerType = powerType;
    }

    @Override
    protected Boolean doInBackground(Boolean... enable) {
        try {
            error = null;
            PowerManager powerManager =  DeviceManager.getDevice(context)
                    .getPowerManager();

            if(powerManager != null && powerManager.isPowerTypeSupported(powerType))
            {
                powerManager.power(powerType, enable[0]);
            }

            return true;
        }catch(Throwable t)
        {
            error = t;
            t.printStackTrace();
            return false;
        }
    }
}
