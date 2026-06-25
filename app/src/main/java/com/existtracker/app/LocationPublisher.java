package com.existtracker.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.WifiManager;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Publishes this phone's location to our Together-Time backend in the exact
 * JSON shape OwnTracks uses, plus an extra "ssid" field and a "device" id.
 * The backend can't tell our app apart from the real OwnTracks iOS app.
 *
 * This means we do NOT need a second app on the Android side — our existing
 * tracking service calls publishOnce() on its normal sampling tick.
 */
public class LocationPublisher {

    private final Context ctx;
    private final Settings settings;

    public LocationPublisher(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.settings = new Settings(this.ctx);
    }

    /** Grab the current location and POST it to the backend. Safe to call often. */
    public void publishOnce() {
        if (!settings.getLocationPublishEnabled()) return;
        if (settings.getTogetherServerUrl().isEmpty()) return;
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        try {
            FusedLocationProviderClient client =
                    LocationServices.getFusedLocationProviderClient(ctx);
            // getCurrentLocation gives a fresh fix; fall back to lastLocation.
            CancellationTokenSource cts = new CancellationTokenSource();
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.getToken())
                    .addOnSuccessListener(loc -> {
                        if (loc != null) send(loc);
                        else client.getLastLocation().addOnSuccessListener(this::sendIfNotNull);
                    })
                    .addOnFailureListener(e ->
                            client.getLastLocation().addOnSuccessListener(this::sendIfNotNull));
        } catch (SecurityException ignored) {
        } catch (Exception ignored) {}
    }

    private void sendIfNotNull(Location loc) { if (loc != null) send(loc); }

    private void send(Location loc) {
        // Build the OwnTracks-format payload on a background thread.
        new Thread(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("_type", "location");
                o.put("lat", loc.getLatitude());
                o.put("lon", loc.getLongitude());
                o.put("tst", loc.getTime() / 1000L);  // OwnTracks uses unix seconds
                o.put("acc", (int) loc.getAccuracy());
                o.put("tid", shortId(settings.getMyDeviceId()));
                o.put("device", settings.getMyDeviceId());
                String ssid = currentSsid();
                if (ssid != null) o.put("ssid", ssid);

                String base = settings.getTogetherServerUrl();
                if (!base.endsWith("/")) base += "/";
                URL url = new URL(base + "pub");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("X-Secret", settings.getTogetherSecret());
                byte[] body = o.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = c.getOutputStream()) { os.write(body); }
                c.getResponseCode(); // fire and forget
                c.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private String shortId(String s) {
        if (s == null || s.isEmpty()) return "ME";
        s = s.trim();
        return s.length() <= 2 ? s : s.substring(0, 2);
    }

    private String currentSsid() {
        try {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return null;
            String s = wm.getConnectionInfo().getSSID();
            if (s == null) return null;
            s = s.replace("\"", "");
            if (s.isEmpty() || s.equals("<unknown ssid>")) return null;
            return s;
        } catch (Exception e) {
            return null;
        }
    }
}
