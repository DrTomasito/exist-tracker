package com.existtracker.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Calendar;

/**
 * Runs continuously in the foreground (low priority) and:
 *  - every SAMPLE_MS, checks current WiFi SSID + Bluetooth device, and
 *    adds the elapsed minutes to the right counter
 *  - reads YouTube / social app usage from the system usage-stats service
 *  - schedules a 11:50pm post and 11:58pm clear via AlarmManager
 */
public class TrackingService extends Service {

    public static final String ACTION_POST  = "com.existtracker.app.POST";
    public static final String ACTION_CLEAR = "com.existtracker.app.CLEAR";
    public static final String ACTION_SYNC_NOW = "com.existtracker.app.SYNC_NOW";
    public static final String ACTION_STOP = "com.existtracker.app.STOP";

    /** True while the service is alive — used by the dashboard toggle to show
     *  real running state. */
    public static volatile boolean RUNNING = false;

    private static final String CHANNEL = "tracking";
    private static final long SAMPLE_MS = 60_000L; // sample once a minute

    private Settings settings;
    private Handler handler;
    private Runnable ticker;
    private long lastTickElapsed;
    private String currentBucket = "away"; // work / home / away, updated each tick
    private ScreenReceiver screenReceiver;
    private long screenOnSinceElapsed = -1; // when the screen was last turned on
    private int sampleCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        RUNNING = true;
        settings = new Settings(this);
        handler = new Handler(Looper.getMainLooper());
        createChannel();
        startForeground(1, buildNotification("Tracking your day…"));
        scheduleDailyAlarms();
        lastTickElapsed = SystemClock.elapsedRealtime();
        registerScreenReceiver();
        startTicking();
    }

    private void registerScreenReceiver() {
        screenReceiver = new ScreenReceiver();
        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, f);
        // If the screen is already on when we start, begin counting now.
        android.os.PowerManager pm = (android.os.PowerManager)
                getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isInteractive()) {
            screenOnSinceElapsed = SystemClock.elapsedRealtime();
        }
    }

    /** Counts screen unlocked/in-use time as it happens. */
    private class ScreenReceiver extends android.content.BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            long now = SystemClock.elapsedRealtime();
            if (Intent.ACTION_SCREEN_ON.equals(a)) {
                screenOnSinceElapsed = now;
            } else if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                flushScreenTime(now);
            }
        }
    }

    private void flushScreenTime(long now) {
        if (screenOnSinceElapsed > 0) {
            int mins = (int) Math.round((now - screenOnSinceElapsed) / 60000.0);
            if (mins > 0) {
                settings.addScreen(mins);
                if ("home".equals(currentBucket)) settings.addScreenHome(mins);
            }
            screenOnSinceElapsed = -1;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Any normal start means tracking is desired (cleared only by STOP).
        if (intent == null || !ACTION_STOP.equals(intent.getAction())) {
            settings.setTrackingDesired(true);
        }
        if (intent != null && intent.getAction() != null) {
            if (ACTION_POST.equals(intent.getAction())) {
                postToExist();
                scheduleDailyAlarms(); // re-arm for tomorrow
            } else if (ACTION_SYNC_NOW.equals(intent.getAction())) {
                // Manual "sync now" (pull-to-refresh): post to Exist AND push to
                // cloud unconditionally (ignores the auto-backup toggle), so the
                // user can force everything up to date on demand.
                postToExist();
                try {
                    CloudSync cloud = new CloudSync(this);
                    if (cloud.isConfigured()) cloud.backupNow();
                } catch (Exception ce) {
                    settings.setCloudLastStatus("Manual cloud sync failed: " + ce.getMessage());
                }
            } else if (ACTION_CLEAR.equals(intent.getAction())) {
                settings.clearCounters();
                settings.clearUsageBaselines();
                scheduleDailyAlarms();
            } else if (ACTION_STOP.equals(intent.getAction())) {
                // User turned tracking OFF from the dashboard toggle. Remember
                // that choice so we don't auto-restart, then stop.
                settings.setTrackingDesired(false);
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY; // restart if Android kills us
    }

    // ---- Sampling loop ----

    private void startTicking() {
        ticker = new Runnable() {
            @Override public void run() {
                sampleOnce();
                handler.postDelayed(this, SAMPLE_MS);
            }
        };
        handler.postDelayed(ticker, SAMPLE_MS);
    }

    private void sampleOnce() {
        long now = SystemClock.elapsedRealtime();
        int minutes = (int) Math.max(1, Math.round((now - lastTickElapsed) / 60000.0));
        lastTickElapsed = now;

        // --- WiFi SSID ---
        String ssid = currentSsid();
        currentBucket = "away";
        boolean onWorkNow = false;
        boolean onHomeNow = false;
        if (ssid != null) {
            if (ssid.equalsIgnoreCase(settings.getHospitalSsid())) {
                settings.addHospital(minutes);
                currentBucket = "work";
                onWorkNow = true;
                // Arrival = first time we see work WiFi today; departure keeps
                // updating to the latest time we still see it.
                int minOfDay = minuteOfDayNow();
                if (settings.getArrivalToday() < 0) settings.setArrivalToday(minOfDay);
                settings.setDepartureToday(minOfDay);
            }
            if (ssid.equalsIgnoreCase(settings.getHomeSsid())) {
                settings.addHome(minutes);
                currentBucket = "home";
                onHomeNow = true;
            }
            if (ssid.equalsIgnoreCase(settings.getChurchSsid())) {
                settings.addChurch(minutes);
                // First church connect today = arrival (for backend inferences).
                if (settings.getChurchArrivalToday() < 0)
                    settings.setChurchArrivalToday(minuteOfDayNow());
            }
        }

        // --- "Got home from work" detection (SSID-only, no GPS) ---
        // Rule: log the FIRST connect to home WiFi that happens AFTER we have been
        // on work WiFi today and then left it. We track whether we were on work
        // today (arrival is set) and whether we've since left work (not on work now
        // after having been). Only the first qualifying home connect is recorded.
        if (onWorkNow) {
            // Currently at work — mark that we've been to work, and clear the
            // "left work" latch (we're back/here, so a later home connect counts).
            settings.setLeftWorkToday(false);
        } else if (settings.getArrivalToday() >= 0 && !onHomeNow) {
            // Been to work today, and right now not at work and not at home
            // (i.e. in transit / elsewhere) → we've left work.
            settings.setLeftWorkToday(true);
        }
        if (onHomeNow
                && settings.getArrivalToday() >= 0      // went to work today
                && settings.getLeftWorkToday()           // and have since left it
                && settings.getHomeArrivalToday() < 0) { // and not already logged
            settings.setHomeArrivalToday(minuteOfDayNow());
        }

        // --- Bluetooth connected device ---
        if (isBtDeviceConnected(settings.getDrivingDevice())) {
            settings.addDriving(minutes);
        }

        // --- App usage (absolute totals from system, count the delta) ---
        updateUsage();

        // --- Publish location for "together time" every ~5 minutes ---
        // Only when together-tracking is explicitly enabled, so no GPS fires
        // until the user sets it up. (SSID tracking is unaffected.)
        sampleCount++;
        if (settings.getLocationPublishEnabled() && sampleCount % 5 == 0) {
            try { new LocationPublisher(this).publishOnce(); } catch (Exception ignored) {}
        }

        // --- Screen in-use time: if screen is on, bank the elapsed portion ---
        long nowMs = SystemClock.elapsedRealtime();
        if (screenOnSinceElapsed > 0) {
            int mins = (int) Math.round((nowMs - screenOnSinceElapsed) / 60000.0);
            if (mins > 0) {
                settings.addScreen(mins);
                // Also bank screen time at home (calculated metric: how much of
                // screen time happened while on the home WiFi).
                if ("home".equals(currentBucket)) settings.addScreenHome(mins);
                screenOnSinceElapsed = nowMs; // reset baseline, keep counting
            }
        }

        // refresh the notification text so it's informative
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(1, buildNotification(statusLine()));
    }

    private String statusLine() {
        return "Hosp " + settings.getHospitalMin() + "m · Home " + settings.getHomeMin()
                + "m · YT " + settings.getYoutubeMin() + "m · Soc " + settings.getSocialMin()
                + "m · Drive " + settings.getDrivingMin() + "m";
    }

    // ---- WiFi ----
    private String currentSsid() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return null;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return null;
            String s = info.getSSID();
            if (s == null) return null;
            s = s.replace("\"", "");
            if (s.isEmpty() || s.equals("<unknown ssid>")) return null;
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Bluetooth ----
    private boolean isBtDeviceConnected(String nameContains) {
        try {
            BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bm == null) return false;
            BluetoothAdapter adapter = bm.getAdapter();
            if (adapter == null || !adapter.isEnabled()) return false;
            // Check common profiles for a connected device whose name matches.
            for (int profile : new int[]{BluetoothProfile.HEADSET,
                    BluetoothProfile.A2DP}) {
                int state = adapter.getProfileConnectionState(profile);
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    // We can't always list device names without extra callbacks,
                    // but bonded + connected name check below covers the case.
                }
            }
            // Look through bonded devices that report as connected.
            for (android.bluetooth.BluetoothDevice d : adapter.getBondedDevices()) {
                String n = d.getName();
                if (n != null && n.toLowerCase().contains(nameContains.toLowerCase())) {
                    if (isConnected(d)) return true;
                }
            }
        } catch (SecurityException se) {
            // BLUETOOTH_CONNECT not granted yet
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isConnected(android.bluetooth.BluetoothDevice device) {
        try {
            java.lang.reflect.Method m = device.getClass().getMethod("isConnected");
            Object r = m.invoke(device);
            return r instanceof Boolean && (Boolean) r;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- App usage ----
    private void updateUsage() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;

        long startOfDay = startOfTodayMillis();
        long now = System.currentTimeMillis();

        int youtubeTotalMin = (int) (foregroundSeconds(usm, startOfDay, now, settings.getYoutubePkgs()) / 60);
        int socialTotalMin  = (int) (foregroundSeconds(usm, startOfDay, now, settings.getSocialPkgs()) / 60);

        // 1) Daily total is AUTHORITATIVE — set directly to what the OS reports
        //    for today. This self-corrects every cycle and naturally resets at a
        //    new day (the query window starts at today's midnight), so it can
        //    never get "stuck" at a stale value.
        settings.setYoutube(youtubeTotalMin);
        settings.setSocial(socialTotalMin);

        // 2) Location buckets (work/home/away) are inherently incremental: we
        //    attribute the *increase* since the last sample to wherever we are
        //    now. Baseline holds the last-seen total for this purpose only, and
        //    is reset each new day via clearUsageBaselines() at the nightly clear.
        applyBucketDelta("youtube", youtubeTotalMin, true);
        applyBucketDelta("social",  socialTotalMin,  false);
    }

    /** Attribute the increase since last sample to the current location bucket. */
    private void applyBucketDelta(String key, int totalMin, boolean youtube) {
        long baseline = settings.getUsageBaseline(key);
        int delta;
        if (baseline < 0) {
            // First sample of the day: attribute the whole current total once.
            delta = totalMin;
        } else if (totalMin > baseline) {
            delta = (int) (totalMin - baseline);
        } else {
            delta = 0; // no increase (or OS number dipped) — attribute nothing
        }
        settings.setUsageBaseline(key, totalMin);
        if (delta <= 0) return;
        if (youtube) settings.addYtBucket(currentBucket, delta);
        else         settings.addSocBucket(currentBucket, delta);
    }

    /** Sum foreground time (seconds) today for a comma-separated package list. */
    private long foregroundSeconds(UsageStatsManager usm, long start, long end, String pkgCsv) {
        long total = 0;
        try {
            java.util.Map<String, android.app.usage.UsageStats> stats =
                    usm.queryAndAggregateUsageStats(start, end);
            for (String pkg : pkgCsv.split(",")) {
                pkg = pkg.trim();
                if (pkg.isEmpty()) continue;
                android.app.usage.UsageStats st = stats.get(pkg);
                if (st != null) total += st.getTotalTimeInForeground() / 1000;
            }
        } catch (Exception ignored) {}
        return total;
    }

    private long startOfTodayMillis() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ---- Posting to Exist ----
    private void postToExist() {
        new Thread(() -> {
            try {
                ExistApi api = new ExistApi(this);
                if (!settings.isLoggedIn()) {
                    settings.setLastStatus("Skipped: not connected to Exist.");
                    return;
                }
                String date = today();
                // Sleep source: either read from Exist (where Health Connect
                // already syncs it) or read Health Connect directly.
                if (settings.getSleepFromExist()) {
                    try {
                        int s = api.readTodayValue(settings.getSleepExistAttr());
                        if (s >= 0) settings.setSleepMin(s);
                    } catch (Exception ignored) {}
                } else {
                    int sleepMin = SleepReader.readLastNightMinutes(this);
                    if (sleepMin >= 0) settings.setSleepMin(sleepMin);
                }

                // Pull work-computer minutes from the relay (extension data).
                pullFromRelay(api);

                // Combined YouTube total = phone YouTube + work-computer YouTube.
                int youtubeTotal = settings.getYoutubeMin() + settings.getWorkYoutubeMin();

                // Make sure attributes exist / are owned (safe to repeat).
                api.ensureAttribute(settings.getHospitalAttr(), "Hospital Time", "location");
                api.ensureAttribute(settings.getHomeAttr(), "Home Time", "location");
                api.ensureAttribute(settings.getYoutubeAttr(), "Youtube Time", "social");
                api.ensureAttribute(settings.getSocialAttr(), "Social Media Time", "social");
                api.ensureAttribute(settings.getDrivingAttr(), "Time Driving", "location");

                api.updateValue(settings.getHospitalAttr(), date, settings.getHospitalMin());
                api.updateValue(settings.getHomeAttr(), date, settings.getHomeMin());
                api.updateValue(settings.getYoutubeAttr(), date, youtubeTotal);
                api.updateValue(settings.getSocialAttr(), date, settings.getSocialMin());
                api.updateValue(settings.getDrivingAttr(), date, settings.getDrivingMin());

                // Optional extra metrics — only posted if the user enabled them.
                postIfEnabled(api, "screen", "Screen Time", "media", date, settings.getScreenMin());
                postIfEnabled(api, "screen_home", "Screen Time at Home", "media", date, settings.getScreenHome());
                // NOTE: we do NOT post sleep to Exist — Health Connect already
                // syncs sleep into Exist, so writing it back would be redundant.
                postIfEnabled(api, "yt_work", "YouTube at Work", "social", date, settings.getYtWork());
                postIfEnabled(api, "yt_home", "YouTube at Home", "social", date, settings.getYtHome());
                postIfEnabled(api, "soc_work", "Social at Work", "social", date, settings.getSocWork());
                postIfEnabled(api, "soc_home", "Social at Home", "social", date, settings.getSocHome());
                postIfEnabled(api, "work_distract", "Work Distractions", "productivity", date, settings.getWorkDistractMin());

                // Save today's totals to local history for in-app trend charts.
                settings.saveHistory("hospital", date, settings.getHospitalMin());
                settings.saveHistory("home", date, settings.getHomeMin());
                settings.saveHistory("youtube", date, youtubeTotal);
                settings.saveHistory("social", date, settings.getSocialMin());
                settings.saveHistory("driving", date, settings.getDrivingMin());
                settings.saveHistory("church", date, settings.getChurchMin());
                settings.saveHistory("screen_home", date, settings.getScreenHome());
                if (settings.getChurchArrivalToday() >= 0)
                    settings.saveChurchArrival(date, settings.getChurchArrivalToday());
                settings.saveHistory("screen", date, settings.getScreenMin());
                settings.saveHistory("sleep", date, Math.max(0, settings.getSleepMin()));

                // Compute the hardwired inferences for the day (gated by their
                // on/off toggles). Stored as dated 0/1 flags for counting + cloud.
                computeInferences(date);
                settings.saveHistory("yt_work", date, settings.getYtWork());
                settings.saveHistory("yt_home", date, settings.getYtHome());
                settings.saveHistory("yt_away", date, settings.getYtAway());
                settings.saveHistory("soc_work", date, settings.getSocWork());
                settings.saveHistory("soc_home", date, settings.getSocHome());
                settings.saveHistory("soc_away", date, settings.getSocAway());
                settings.saveHistory("work_distract", date, settings.getWorkDistractMin());
                // home awake = home minus sleep (not below zero)
                int homeAwake = Math.max(0, settings.getHomeMin() - Math.max(0, settings.getSleepMin()));
                settings.saveHistory("home_awake", date, homeAwake);

                // --- Together time: read back what the backend posted to Exist,
                //     then compute "together awake" = together minus my sleep.
                //     Only when together-tracking is enabled. ---
                if (settings.getLocationPublishEnabled()) try {
                    int together = api.readTodayValue(settings.getTogetherAttr());
                    settings.setTogetherMin(together);
                    settings.saveHistory("together", date, together);
                    int togetherAwake = Math.max(0, together - Math.max(0, settings.getSleepMin()));
                    settings.saveHistory("together_awake", date, togetherAwake);
                    if (settings.getPostTogetherAwake()) {
                        api.ensureAttribute(settings.getTogetherAwakeAttr(),
                                "Time Together Awake", "social");
                        api.updateValue(settings.getTogetherAwakeAttr(), date, togetherAwake);
                    }
                } catch (Exception ignored) {}

                // Save arrival/departure into history if we have them.
                if (settings.getArrivalToday() >= 0)
                    settings.saveArrival(date, settings.getArrivalToday());
                if (settings.getDepartureToday() >= 0)
                    settings.saveDeparture(date, settings.getDepartureToday());
                if (settings.getHomeArrivalToday() >= 0)
                    settings.saveHomeArrival(date, settings.getHomeArrivalToday());

                // Optional: post arrival times to Exist as time-of-day (type 4).
                postTimeOfDayIfEnabled(api, "home_arrival", "Got Home Time",
                        "location", date, settings.getHomeArrivalToday());
                postTimeOfDayIfEnabled(api, "work_arrival", "Got To Work Time",
                        "location", date, settings.getArrivalToday());

                // Optional: time at church (duration). Uses the Church Time attr
                // from Step 3; only posts if the "post church" toggle is on.
                if (settings.postEnabled("church")) {
                    api.ensureAttribute(settings.getChurchAttr(), "Church Time", "location");
                    api.updateValue(settings.getChurchAttr(), date, settings.getChurchMin());
                }

                settings.setLastStatus("Posted " + date + ": " + statusLine());

                // Post each tracker's daily total to its Exist attribute,
                // but only those the user chose to push (not internal-only).
                try {
                    Stopwatches sw = new Stopwatches(this);
                    for (Stopwatches.SW s : sw.getAll()) {
                        if (s.pushToExist && s.attr != null && !s.attr.isEmpty()) {
                            int val = sw.getDailyTotal(s.id, date);
                            int vtype = s.valueType; // 3=duration, 0=integer, 8=scale
                            // Scale values must be 1-9; skip posting if unset today
                            // (a 0 would be rejected by Exist as out of range).
                            if (s.isScale() && (val < 1 || val > 9)) continue;
                            api.ensureAttribute(s.attr, s.name,
                                    s.isScale() ? "mood" : "productivity", vtype);
                            api.updateValue(s.attr, date, val);
                        }
                    }
                } catch (Exception ignored) {}

                // Send key metrics to the backend so DAKboard can display them.
                pushMetricsToBackend(date);

                // Cloud backup (Supabase) — only if the user enabled auto-backup.
                // Runs here on the same background thread as the nightly post.
                if (settings.getCloudAuto()) {
                    try {
                        CloudSync cloud = new CloudSync(this);
                        if (cloud.isConfigured()) cloud.backupNow();
                    } catch (Exception ce) {
                        settings.setCloudLastStatus("Auto-backup failed: " + ce.getMessage());
                    }
                }
            } catch (Exception e) {
                settings.setLastStatus("Post failed: " + e.getMessage());
            }
        }).start();
    }

    private void postIfEnabled(ExistApi api, String metric, String label,
                               String group, String date, int value) throws java.io.IOException {
        if (!settings.postEnabled(metric)) return;
        String attr = settings.getAttrFor(metric, defaultAttrName(metric));
        api.ensureAttribute(attr, label, group);
        api.updateValue(attr, date, value);
    }

    /** Post a time-of-day value (Exist value_type 4: minutes from midnight).
     *  Only posts if enabled and the value is valid (>= 0). */
    private void postTimeOfDayIfEnabled(ExistApi api, String metric, String label,
                                        String group, String date, int minOfDay)
            throws java.io.IOException {
        if (!settings.postEnabled(metric)) return;
        if (minOfDay < 0) return; // no event captured that day
        String attr = settings.getAttrFor(metric, defaultAttrName(metric));
        api.ensureAttribute(attr, label, group, 4); // 4 = time of day
        api.updateValue(attr, date, minOfDay);
    }

    private String defaultAttrName(String metric) {
        switch (metric) {
            case "screen": return "screen_time";
            case "screen_home": return "screen_time_at_home";
            case "sleep": return "time_asleep";
            case "yt_work": return "youtube_at_work";
            case "yt_home": return "youtube_at_home";
            case "soc_work": return "social_at_work";
            case "soc_home": return "social_at_home";
            case "home_arrival": return "got_home_time";
            case "work_arrival": return "got_to_work_time";
            case "church": return "time_at_church";
            default: return metric;
        }
    }

    /** Pull work-computer minutes from the configured relay before posting. */
    private void pullFromRelay(ExistApi api) {
        String mode = settings.getRelayMode();
        try {
            if ("exist".equals(mode)) {
                int distract = api.readTodayValue(settings.getRelayDistractAttr());
                int ytWork = api.readTodayValue(settings.getRelayYoutubeAttr());
                settings.setWorkDistractMin(distract);
                settings.setWorkYoutubeMin(ytWork);
            } else if ("jsonbin".equals(mode)) {
                RelayReader.WorkData d = RelayReader.readJsonbin(
                        settings.getJsonbinId(), settings.getJsonbinKey());
                if (d != null && today().equals(d.date)) {
                    settings.setWorkDistractMin(d.distract);
                    settings.setWorkYoutubeMin(d.youtube);
                }
            }
            // "off" -> leave at zero
        } catch (Exception ignored) {}
    }

    /** POST today's display metrics to the backend's /metrics endpoint. */
    private void pushMetricsToBackend(String date) {
        String base = settings.getTogetherServerUrl();
        if (base == null || base.isEmpty()) return;
        try {
            org.json.JSONObject values = new org.json.JSONObject();
            values.put("work", settings.getHospitalMin());
            values.put("home", settings.getHomeMin());
            values.put("youtube", settings.getYoutubeMin() + settings.getWorkYoutubeMin());
            values.put("social", settings.getSocialMin());
            values.put("screen", settings.getScreenMin());
            values.put("sleep", Math.max(0, settings.getSleepMin()));
            values.put("together_awake",
                    Math.max(0, settings.getTogetherMin() - Math.max(0, settings.getSleepMin())));
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("day", date);
            body.put("values", values);

            if (!base.endsWith("/")) base += "/";
            java.net.HttpURLConnection c =
                    (java.net.HttpURLConnection) new java.net.URL(base + "metrics").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-Secret", settings.getTogetherSecret());
            byte[] out = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (java.io.OutputStream os = c.getOutputStream()) { os.write(out); }
            c.getResponseCode();
            c.disconnect();
        } catch (Exception ignored) {}
    }

    private int minuteOfDayNow() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private String today() {
        Calendar c = Calendar.getInstance();
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Compute the hardwired inferences for the given day from the day's raw
     * location/time facts. Each is gated by its on/off toggle (Cloud Sync tab)
     * and stored as a dated 0/1 flag (so they can be counted over time and
     * synced). These are intentionally simple, stable rules; richer/probabilistic
     * interpretation is left to the couples-app AI.
     *
     * Thresholds (minutes from midnight):
     *   choir         = at church by 10:00am (<=600) on a Sunday
     *   late_church   = at church after 11:07am (>667) on a Sunday
     *   rounds        = at hospital before 6:45am (<405) on a weekday
     *   home_for_dinner = home from work by 6:05pm (<=1085) on a weekday
     *   weekend_out   = a weekend day with meaningful time away from home
     *                   (home minutes well below an at-home day) and not at work
     */
    private void computeInferences(String date) {
        int dow = dayOfWeekFor(date); // Calendar.SUNDAY..SATURDAY
        boolean isSunday = dow == Calendar.SUNDAY;
        boolean isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
        boolean isWeekday = !isWeekend;

        int churchArr = settings.getChurchArrivalToday(); // -1 if no church today
        int workArr   = settings.getArrivalToday();        // -1 if never at work
        int homeArr   = settings.getHomeArrivalToday();     // -1 if no home-from-work

        // Choir day — only evaluated on Sundays when church was attended.
        if (settings.inferenceEnabled("choir") && isSunday && churchArr >= 0) {
            settings.saveInference("choir", date, churchArr <= 600);
        }
        // Late to church — only on Sundays when church was attended.
        if (settings.inferenceEnabled("late_church") && isSunday && churchArr >= 0) {
            settings.saveInference("late_church", date, churchArr > 667);
        }
        // Made rounds — weekday, when there was a work arrival.
        if (settings.inferenceEnabled("rounds") && isWeekday && workArr >= 0) {
            settings.saveInference("rounds", date, workArr < 405);
        }
        // Home for dinner — weekday; true if home-from-work by 6:05pm. If TJ
        // went to work but never registered a home-from-work arrival, that's a
        // "false" (not home in time). Days he never went to work are skipped.
        if (settings.inferenceEnabled("home_for_dinner") && isWeekday && workArr >= 0) {
            boolean home = homeArr >= 0 && homeArr <= 1085;
            settings.saveInference("home_for_dinner", date, home);
        }
        // Weekend out and about — weekend day, not at work, where time at home
        // was well under a full at-home day (proxy: home minutes < 600 = under
        // 10h home while awake), implying meaningful time out as a couple/family.
        if (settings.inferenceEnabled("weekend_out") && isWeekend && workArr < 0) {
            boolean out = settings.getHomeMin() < 600;
            settings.saveInference("weekend_out", date, out);
        }
    }

    private int dayOfWeekFor(String date) {
        try {
            String[] p = date.split("-");
            Calendar c = Calendar.getInstance();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            return c.get(Calendar.DAY_OF_WEEK);
        } catch (Exception e) {
            return Calendar.MONDAY;
        }
    }

    // ---- Daily alarms (11:50 post, 11:58 clear) ----
    private void scheduleDailyAlarms() {
        AlarmManager am = getSystemService(AlarmManager.class);
        if (am == null) return;
        setAlarm(am, 23, 50, ACTION_POST, 101);
        setAlarm(am, 23, 58, ACTION_CLEAR, 102);
    }

    private void setAlarm(AlarmManager am, int hour, int min, String action, int reqCode) {
        Intent i = new Intent(this, TrackingService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(this, reqCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, min);
        c.set(Calendar.SECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_MONTH, 1); // already past today -> tomorrow
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        } catch (SecurityException se) {
            am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        }
    }

    // ---- Notification plumbing ----
    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                "Exist Tracking", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("Exist Tracker running")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        RUNNING = false;
        try { if (screenReceiver != null) unregisterReceiver(screenReceiver); }
        catch (Exception ignored) {}
    }

    /** If the user swipes the app away from recents, Android may stop the
     *  service. Schedule an immediate restart so tracking keeps running. */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            Settings s = new Settings(this);
            if (s.isLoggedIn() && s.isTrackingDesired()) {
                Intent restart = new Intent(getApplicationContext(), TrackingService.class);
                android.app.PendingIntent pi = android.app.PendingIntent.getForegroundService(
                        this, 7, restart,
                        android.app.PendingIntent.FLAG_ONE_SHOT
                                | android.app.PendingIntent.FLAG_IMMUTABLE);
                android.app.AlarmManager am =
                        (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
                if (am != null && pi != null) {
                    am.set(android.app.AlarmManager.RTC,
                            System.currentTimeMillis() + 1000, pi);
                }
            }
        } catch (Exception ignored) {}
        super.onTaskRemoved(rootIntent);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
