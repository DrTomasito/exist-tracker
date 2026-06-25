package com.existtracker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the tracking service after the phone reboots. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Settings s = new Settings(context);
            if (s.isLoggedIn()) {
                Intent svc = new Intent(context, TrackingService.class);
                context.startForegroundService(svc);
            }
        }
    }
}
