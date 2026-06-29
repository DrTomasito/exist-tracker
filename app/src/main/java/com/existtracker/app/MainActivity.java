package com.existtracker.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.health.connect.client.PermissionController;

import java.util.Collections;
import java.util.Set;

/**
 * Single-screen UI. Walks the user through:
 *   1. Entering their Exist OAuth client ID + secret
 *   2. Connecting (OAuth, paste-the-code)
 *   3. Granting permissions
 *   4. Editing attribute names + SSIDs / packages / device
 *   5. Starting tracking, and a "Post now" test button
 */
public class MainActivity extends AppCompatActivity {

    private Settings settings;
    private TextView statusView;
    private final java.util.List<Runnable> fieldSavers = new java.util.ArrayList<>();
    private ActivityResultLauncher<Set<String>> sleepPermLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        settings = new Settings(this);
        // Register the Health Connect permission request (no-op result; we just
        // need the user to grant it so SleepReader can work later).
        try {
            sleepPermLauncher = registerForActivityResult(
                    PermissionController.createRequestPermissionResultContract(),
                    granted -> {
                        if (granted != null && granted.contains(SleepReader.SLEEP_PERMISSION)) {
                            toast("Sleep access granted.");
                        } else {
                            toast("Sleep access not granted.");
                        }
                    });
        } catch (Exception ignored) {}

        // Backup: create a document and write our JSON into it.
        exportLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri == null) return;
                    try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                        os.write(settings.exportToJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        toast("Settings exported.");
                    } catch (Exception e) {
                        toast("Export failed: " + e.getMessage());
                    }
                });

        // Restore: open a document and read JSON from it.
        importLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[4096]; int n;
                        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                        boolean ok = settings.importFromJson(
                                new String(bo.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                        toast(ok ? "Settings imported. Reopening…" : "Import failed: bad file.");
                        if (ok) recreate();
                    } catch (Exception e) {
                        toast("Import failed: " + e.getMessage());
                    }
                });

        setContentView(buildUi());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveFields();
    }

    private void saveFields() {
        for (Runnable r : fieldSavers) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    private View buildUi() {
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(header("Exist Tracker"));
        statusView = new TextView(this);
        statusView.setTextColor(Ui.TEXT);
        statusView.setPadding(0, dp(4), 0, dp(12));
        root.addView(statusView);
        root.addView(Ui.navRow(this, "settings"));

        // --- Step 1: credentials ---
        root.addView(section("Step 1 — Connect your Exist account"));
        root.addView(note("Create an OAuth2 app at exist.io/account/apps/ "
                + "(scopes: manual_read + manual_write; redirect URL: "
                + "https://exist.io/account/apps/). Paste the Client ID and Secret below."));

        Button creds = new Button(this);
        creds.setText("Enter Client ID & Secret");
        creds.setOnClickListener(v -> editCredentials());
        root.addView(creds);

        Button connect = new Button(this);
        connect.setText("Connect to Exist (log in)");
        connect.setOnClickListener(v -> startOAuth());
        root.addView(connect);

        root.addView(note("Already have tokens? Skip the login above and paste "
                + "them directly:"));
        Button pasteTokens = new Button(this);
        pasteTokens.setText("Paste tokens directly");
        pasteTokens.setOnClickListener(v -> pasteTokensDialog());
        root.addView(pasteTokens);

        // --- Step 2: permissions ---
        root.addView(section("Step 2 — Grant permissions"));
        root.addView(note("These let the app see WiFi name, Bluetooth, and app "
                + "usage. Tap each and allow."));

        root.addView(permButton("Location (allow ALL THE TIME)", v -> requestLocation()));
        root.addView(permButton("Background location", v -> requestBackgroundLocation()));
        root.addView(permButton("Notifications", v -> requestNotifications()));
        root.addView(permButton("Nearby devices / Bluetooth", v -> requestBluetooth()));
        root.addView(permButton("Usage access (for YouTube/social)", v -> openUsageAccess()));
        root.addView(permButton("Sleep access (Health Connect)", v -> requestSleep()));
        root.addView(permButton("Disable battery optimization", v -> openBatterySettings()));

        // --- Step 3: what to track ---
        root.addView(section("Step 3 — What to track"));

        root.addView(trackerEditor("Hospital Time",
                () -> settings.getHospitalAttr(), s -> settings.setHospitalAttr(s),
                "WiFi name", () -> settings.getHospitalSsid(), s -> settings.setHospitalSsid(s),
                "location"));

        root.addView(trackerEditor("Home Time",
                () -> settings.getHomeAttr(), s -> settings.setHomeAttr(s),
                "WiFi name", () -> settings.getHomeSsid(), s -> settings.setHomeSsid(s),
                "location"));

        // Church location — same full editor as Home/Hospital (title, Exist attr
        // + chooser, and the WiFi SSID). Tracked in Trends (days/month) and
        // optionally posted to Exist. Intentionally NOT shown on the dashboard.
        root.addView(trackerEditor("Church Time",
                () -> settings.getChurchAttr(), s -> settings.setChurchAttr(s),
                "WiFi name", () -> settings.getChurchSsid(), s -> settings.setChurchSsid(s),
                "location"));

        root.addView(trackerEditor("Youtube Time",
                () -> settings.getYoutubeAttr(), s -> settings.setYoutubeAttr(s),
                "App package(s)", () -> settings.getYoutubePkgs(), s -> settings.setYoutubePkgs(s),
                "social"));

        root.addView(trackerEditor("Social Media Time",
                () -> settings.getSocialAttr(), s -> settings.setSocialAttr(s),
                "App package(s), comma-separated",
                () -> settings.getSocialPkgs(), s -> settings.setSocialPkgs(s),
                "social"));

        root.addView(trackerEditor("Time Driving",
                () -> settings.getDrivingAttr(), s -> settings.setDrivingAttr(s),
                "Bluetooth device name", () -> settings.getDrivingDevice(),
                s -> settings.setDrivingDevice(s),
                "location"));

        // --- Step 4: run ---
        root.addView(section("Step 4 — Run"));

        Button start = new Button(this);
        start.setText("Start tracking");
        start.setOnClickListener(v -> {
            saveFields();
            if (!settings.isLoggedIn()) {
                toast("Connect to Exist first.");
                return;
            }
            startForegroundService(new Intent(this, TrackingService.class));
            toast("Tracking started. It runs in the background.");
        });
        root.addView(start);

        Button postNow = new Button(this);
        postNow.setText("Post to Exist now (test)");
        postNow.setOnClickListener(v -> {
            saveFields();
            Intent i = new Intent(this, TrackingService.class)
                    .setAction(TrackingService.ACTION_POST);
            startForegroundService(i);
            toast("Posting… check status in a few seconds.");
            statusView.postDelayed(this::refreshStatus, 4000);
        });
        root.addView(postNow);

        Button clearNow = new Button(this);
        clearNow.setText("Reset today's counters");
        clearNow.setOnClickListener(v -> {
            settings.clearCounters();
            settings.clearUsageBaselines();
            refreshStatus();
            toast("Counters reset.");
        });
        root.addView(clearNow);

        Button trends = new Button(this);
        trends.setText("📈 View Trends & Charts");
        trends.setOnClickListener(v ->
                startActivity(new Intent(this, TrendsActivity.class)));
        root.addView(trends);

        // --- Step 5: optional extra metrics to also post to Exist ---
        root.addView(section("Step 5 — Extra metrics (optional)"));
        root.addView(note("These are always shown in Trends. Tick a box to ALSO "
                + "post it to Exist, and set its attribute name."));

        root.addView(postToggle("Phone screen time", "screen", "screen_time"));
        root.addView(postToggle("Screen time at home", "screen_home", "screen_time_at_home"));
        root.addView(postToggle("Time asleep (Health Connect)", "sleep", "time_asleep"));
        root.addView(postToggle("YouTube at work", "yt_work", "youtube_at_work"));
        root.addView(postToggle("YouTube at home", "yt_home", "youtube_at_home"));
        root.addView(postToggle("Social at work", "soc_work", "social_at_work"));
        root.addView(postToggle("Social at home", "soc_home", "social_at_home"));

        // Church posting uses the Church Time attribute from Step 3 (above), so
        // this is just an on/off toggle — no separate attribute field.
        CheckBox churchPost = new CheckBox(this);
        churchPost.setText("Post time at church to Exist");
        churchPost.setTextColor(Ui.TEXT);
        churchPost.setChecked(settings.postEnabled("church"));
        churchPost.setOnCheckedChangeListener((v, on) -> settings.setPostEnabled("church", on));
        root.addView(churchPost);

        // Arrival times (time-of-day, value_type 4). Detected via WiFi SSID only.
        root.addView(note("Arrival times below post as a time-of-day value. "
                + "When creating a new attribute, pick \"Time of day\"."));
        root.addView(postToggle("Got home from work (time)", "home_arrival", "got_home_time"));
        root.addView(postToggle("Got to work (time)", "work_arrival", "got_to_work_time"));

        // --- Dashboard color thresholds ---
        root.addView(section("Dashboard colors (optional)"));
        root.addView(note("Set a daily limit (in minutes) for a metric and its "
                + "dashboard number turns amber once you cross it. Leave blank for "
                + "no color change. \"Together\" is a goal: it stays green once you "
                + "reach it, muted until then."));
        root.addView(thresholdRow("Time at work — warn over", "hospital"));
        root.addView(thresholdRow("YouTube (all) — warn over", "youtube"));
        root.addView(thresholdRow("Social (Insta+FB) — warn over", "social"));
        root.addView(thresholdRow("Screen time — warn over", "screen"));
        root.addView(thresholdRow("Work distractions — warn over", "work_distract"));
        root.addView(thresholdRow("Time together — goal (at least)", "together"));

        // --- Cloud backup (Supabase) ---
        root.addView(section("Cloud backup"));
        root.addView(note("Auto-save all tracker data (including notes) to your "
                + "Supabase project so nothing is lost and future tools can read "
                + "it. Paste your Project URL and service key (see the setup "
                + "guide). Stored only on this phone."));
        EditText cloudUrl = new EditText(this);
        darkEt(cloudUrl);
        cloudUrl.setHint("https://yourproject.supabase.co");
        cloudUrl.setText(settings.getCloudUrl());
        fieldSavers.add(() -> settings.setCloudUrl(cloudUrl.getText().toString().trim()));
        root.addView(labeled("Project URL", cloudUrl));

        EditText cloudKey = new EditText(this);
        darkEt(cloudKey);
        cloudKey.setHint("service_role key");
        cloudKey.setText(settings.getCloudKey());
        fieldSavers.add(() -> settings.setCloudKey(cloudKey.getText().toString().trim()));
        root.addView(labeled("Service key", cloudKey));

        EditText cloudDevice = new EditText(this);
        darkEt(cloudDevice);
        cloudDevice.setHint("tj-pixel");
        cloudDevice.setText(settings.getCloudDevice());
        fieldSavers.add(() -> {
            String d = cloudDevice.getText().toString().trim();
            settings.setCloudDevice(d.isEmpty() ? "tj-pixel" : d);
        });
        root.addView(labeled("Device name", cloudDevice));

        CheckBox cloudAuto = new CheckBox(this);
        cloudAuto.setText("Auto-backup nightly (with the Exist post)");
        cloudAuto.setTextColor(Ui.TEXT);
        cloudAuto.setChecked(settings.getCloudAuto());
        cloudAuto.setOnCheckedChangeListener((v, on) -> settings.setCloudAuto(on));
        root.addView(cloudAuto);

        TextView cloudStatus = new TextView(this);
        cloudStatus.setText("Last backup: " + settings.getCloudLastStatus());
        cloudStatus.setTextColor(Ui.MUTED);
        cloudStatus.setTextSize(12);
        cloudStatus.setPadding(0, dp(4), 0, dp(4));
        root.addView(cloudStatus);

        Button backupBtn = new Button(this);
        backupBtn.setText("Save settings & back up now");
        backupBtn.setOnClickListener(v -> {
            saveFields(); // persist URL/key/device first
            cloudStatus.setText("Last backup: backing up…");
            new Thread(() -> {
                String result;
                try {
                    result = new CloudSync(this).backupNow();
                } catch (Exception e) {
                    result = "Failed: " + e.getMessage();
                }
                final String r = result;
                runOnUiThread(() -> {
                    cloudStatus.setText("Last backup: " + r);
                    toast(r);
                });
            }).start();
        });
        root.addView(backupBtn);

        Button listBtn = new Button(this);
        listBtn.setText("List my trackers (for schema doc)");
        listBtn.setOnClickListener(v -> {
            String md = new Stopwatches(this).exportTrackerListMarkdown();
            EditText out = new EditText(this);
            darkEt(out);
            out.setText(md);
            out.setKeyListener(null); // read-only but selectable/copyable
            out.setTextSize(12);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int dpad = dp(16);
            box.setPadding(dpad, dpad, dpad, dpad);
            box.addView(out);
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Your trackers")
                    .setView(box)
                    .setPositiveButton("Copy", (d, w) -> {
                        android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("trackers", md));
                        toast("Copied — paste into your couples-app doc or to Claude");
                    })
                    .setNegativeButton("Close", null)
                    .show();
        });
        root.addView(listBtn);

        Button cloudSyncBtn = new Button(this);
        cloudSyncBtn.setText("Cloud Sync settings & annotations →");
        cloudSyncBtn.setOnClickListener(v ->
                startActivity(new Intent(this, CloudSyncActivity.class)));
        root.addView(cloudSyncBtn);

        // --- Step 6: backup / restore ---
        root.addView(section("Step 6 — Backup & restore"));
        root.addView(note("Save all your settings (and history) to a file so a new "
                + "phone can pick up where this one left off. The file contains your "
                + "tokens, so keep it private."));

        Button export = new Button(this);
        export.setText("Export settings to a file");
        export.setOnClickListener(v -> { saveFields(); exportSettings(); });
        root.addView(export);

        Button importBtn = new Button(this);
        importBtn.setText("Import settings from a file");
        importBtn.setOnClickListener(v -> importSettings());
        root.addView(importBtn);

        // --- Step 7: work-computer tracking relay ---
        root.addView(section("Step 7 — Work computer tracking"));
        root.addView(note("The browser extension on your work computer reports your "
                + "distracting-site minutes here. Pick how this app reads them. "
                + "'Exist' is recommended; switch to 'jsonbin' if your work network "
                + "blocks it; 'Off' if you're not using the extension."));

        final String[] modes = {"exist", "jsonbin", "off"};
        final String[] modeLabels = {"Exist (recommended)", "jsonbin (fallback)", "Off"};
        TextView modeView = new TextView(this);
 modeView.setTextColor(Ui.TEXT);
        modeView.setPadding(0, dp(4), 0, dp(4));
        Runnable showMode = () -> modeView.setText("Current: "
                + modeLabels[indexOf(modes, settings.getRelayMode())]);
        showMode.run();
        root.addView(modeView);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < modes.length; i++) {
            final String m = modes[i];
            Button mb = new Button(this);
            mb.setText(modeLabels[i]);
            mb.setOnClickListener(v -> { settings.setRelayMode(m); showMode.run();
                toast("Relay set to " + m + "."); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            mb.setLayoutParams(lp);
            modeRow.addView(mb);
        }
        root.addView(modeRow);

        root.addView(note("Exist relay: the attribute names the extension writes to."));
        EditText distractAttr = new EditText(this);
        darkEt(distractAttr);
        distractAttr.setHint("Distractions attribute (e.g. work_distractions)");
        distractAttr.setText(settings.getRelayDistractAttr());
        fieldSavers.add(() -> settings.setRelayDistractAttr(distractAttr.getText().toString().trim()));
        root.addView(distractAttr);

        EditText ytAttr = new EditText(this);
        darkEt(ytAttr);
        ytAttr.setHint("Work-YouTube attribute (e.g. work_youtube)");
        ytAttr.setText(settings.getRelayYoutubeAttr());
        fieldSavers.add(() -> settings.setRelayYoutubeAttr(ytAttr.getText().toString().trim()));
        root.addView(ytAttr);

        root.addView(note("jsonbin fallback: paste the Bin ID and Access Key from "
                + "your jsonbin.io account (only needed if using jsonbin mode)."));
        EditText binId = new EditText(this);
        darkEt(binId);
        binId.setHint("jsonbin Bin ID");
        binId.setText(settings.getJsonbinId());
        fieldSavers.add(() -> settings.setJsonbinId(binId.getText().toString().trim()));
        root.addView(binId);

        EditText binKey = new EditText(this);
        darkEt(binKey);
        binKey.setHint("jsonbin Access Key");
        binKey.setText(settings.getJsonbinKey());
        fieldSavers.add(() -> settings.setJsonbinKey(binKey.getText().toString().trim()));
        root.addView(binKey);

        // --- Step 8: Time together (with wife) ---
        root.addView(section("Step 8 — Time together"));
        root.addView(note("Shares your location with your private backend so it "
                + "can measure time you and your wife spend together. Turn this on, "
                + "paste the server details from your backend setup, and grant "
                + "location 'all the time' (Step 2). Your wife only needs the "
                + "OwnTracks iOS app pointed at the same server."));

        CheckBox locOn = new CheckBox(this);
        locOn.setText("Share my location for together-time");
        locOn.setChecked(settings.getLocationPublishEnabled());
        locOn.setOnCheckedChangeListener((v, on) -> settings.setLocationPublishEnabled(on));
        root.addView(locOn);

        EditText serverUrl = new EditText(this);
        darkEt(serverUrl);
        serverUrl.setHint("Backend server URL (e.g. https://together-time.onrender.com)");
        serverUrl.setText(settings.getTogetherServerUrl());
        fieldSavers.add(() -> settings.setTogetherServerUrl(serverUrl.getText().toString().trim()));
        root.addView(serverUrl);

        EditText secret = new EditText(this);
        darkEt(secret);
        secret.setHint("Shared secret (matches backend SHARED_SECRET)");
        secret.setText(settings.getTogetherSecret());
        fieldSavers.add(() -> settings.setTogetherSecret(secret.getText().toString().trim()));
        root.addView(secret);

        EditText deviceId = new EditText(this);
        darkEt(deviceId);
        deviceId.setHint("My device id (must match backend MY_DEVICE_ID, e.g. tj)");
        deviceId.setText(settings.getMyDeviceId());
        fieldSavers.add(() -> settings.setMyDeviceId(deviceId.getText().toString().trim()));
        root.addView(deviceId);

        root.addView(trackerAttrField("Together-time attribute (read from Exist)",
                () -> settings.getTogetherAttr(), s -> settings.setTogetherAttr(s), "social"));

        CheckBox awakeOn = new CheckBox(this);
        awakeOn.setText("Also post \"together awake\" (together minus my sleep) to Exist");
        awakeOn.setChecked(settings.getPostTogetherAwake());
        awakeOn.setOnCheckedChangeListener((v, on) -> settings.setPostTogetherAwake(on));
        root.addView(awakeOn);

        root.addView(trackerAttrField("Together-awake attribute",
                () -> settings.getTogetherAwakeAttr(), s -> settings.setTogetherAwakeAttr(s), "social"));

        Button testPing = new Button(this);
        testPing.setText("Send a test location ping now");
        testPing.setOnClickListener(v -> {
            saveFields();
            new LocationPublisher(this).publishOnce();
            toast("Test ping sent (if location is granted). Check backend /status.");
        });
        root.addView(testPing);

        return scroll;
    }

    /** A standalone attribute field with a Choose/create button (no detail row). */
    private View trackerAttrField(String label, Getter get, Setter set, String group) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(6));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(Ui.TEXT);
        box.addView(t);
        EditText attr = new EditText(this);
        darkEt(attr);
        attr.setText(get.get());
        fieldSavers.add(() -> set.set(attr.getText().toString().trim()));
        box.addView(attr);
        Button choose = new Button(this);
        choose.setText("Choose from Exist / create new…");
        choose.setOnClickListener(v -> new AttributePicker(this).choose(
                attr.getText().toString().trim(), group, name -> {
                    attr.setText(name);
                    set.set(name);
                }));
        box.addView(choose);
        return box;
    }

    private int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return arr.length - 1;
    }

    // ---- paste tokens directly ----
    private void pasteTokensDialog() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16); l.setPadding(p, p, p, p);

        TextView hint = new TextView(this);
 hint.setTextColor(Ui.TEXT);
        hint.setText("Paste the Access token and Refresh token from your Exist "
                + "app page. (Client ID & Secret are also needed so the app can "
                + "auto-refresh — set those in the other button if you haven't.)");
        hint.setTextSize(12);
        l.addView(hint);

        EditText access = new EditText(this);
        darkEt(access);
        access.setHint("Access token");
        access.setText(settings.getAccessToken());
        l.addView(access);

        EditText refresh = new EditText(this);
        darkEt(refresh);
        refresh.setHint("Refresh token");
        refresh.setText(settings.getRefreshToken());
        l.addView(refresh);

        new AlertDialog.Builder(this)
                .setTitle("Paste tokens directly")
                .setView(l)
                .setPositiveButton("Save", (d, w) -> {
                    settings.setTokensDirect(
                            access.getText().toString().trim(),
                            refresh.getText().toString().trim());
                    toast("Tokens saved.");
                    refreshStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- credential dialog ----
    private void editCredentials() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16); l.setPadding(p, p, p, p);

        EditText id = new EditText(this);
        darkEt(id);
        id.setHint("Client ID");
        id.setText(settings.getClientId());
        l.addView(id);

        EditText secret = new EditText(this);
        darkEt(secret);
        secret.setHint("Client Secret");
        secret.setText(settings.getClientSecret());
        l.addView(secret);

        new AlertDialog.Builder(this)
                .setTitle("Exist OAuth credentials")
                .setView(l)
                .setPositiveButton("Save", (d, w) -> {
                    settings.setClientId(id.getText().toString().trim());
                    settings.setClientSecret(secret.getText().toString().trim());
                    toast("Saved.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- OAuth: open browser, then paste code ----
    private void startOAuth() {
        if (settings.getClientId().isEmpty() || settings.getClientSecret().isEmpty()) {
            toast("Enter Client ID & Secret first.");
            return;
        }
        ExistApi api = new ExistApi(this);
        Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(api.buildAuthorizeUrl()));
        startActivity(browser);

        // Prompt for the code once they come back.
        statusView.postDelayed(this::promptForCode, 1500);
    }

    private void promptForCode() {
        EditText input = new EditText(this);
        darkEt(input);
        input.setHint("Paste the code from the address bar");
        new AlertDialog.Builder(this)
                .setTitle("Finish connecting")
                .setMessage("After you tapped Allow, your browser address bar shows "
                        + "…?code=XXXXX  — copy just the code part and paste it here.")
                .setView(input)
                .setPositiveButton("Connect", (d, w) -> {
                    String code = input.getText().toString().trim();
                    finishOAuth(code);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void finishOAuth(String code) {
        new Thread(() -> {
            boolean ok;
            try {
                ok = new ExistApi(this).exchangeCodeForToken(code);
            } catch (Exception e) {
                ok = false;
            }
            boolean finalOk = ok;
            runOnUiThread(() -> {
                toast(finalOk ? "Connected to Exist!" : "Connection failed. Check the code.");
                refreshStatus();
            });
        }).start();
    }

    // ---- tracker editor row ----
    private interface Getter { String get(); }
    private interface Setter { void set(String s); }

    private View trackerEditor(String title, Getter attrGet, Setter attrSet,
                               String detailLabel, Getter detGet, Setter detSet) {
        return trackerEditor(title, attrGet, attrSet, detailLabel, detGet, detSet, "custom");
    }

    private View trackerEditor(String title, Getter attrGet, Setter attrSet,
                               String detailLabel, Getter detGet, Setter detSet,
                               String suggestedGroup) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(Ui.ACCENT);
        box.addView(t);

        EditText attr = new EditText(this);
        darkEt(attr);
        attr.setHint("Exist attribute name (e.g. hospital_time)");
        attr.setText(attrGet.get());
        attr.setInputType(InputType.TYPE_CLASS_TEXT);
        fieldSavers.add(() -> attrSet.set(attr.getText().toString().trim()));
        box.addView(attr);

        Button choose = new Button(this);
        choose.setText("Choose from Exist / create new…");
        choose.setOnClickListener(v -> new AttributePicker(this).choose(
                attr.getText().toString().trim(), suggestedGroup, name -> {
                    attr.setText(name);
                    attrSet.set(name);
                }));
        box.addView(choose);

        EditText det = new EditText(this);
        darkEt(det);
        det.setHint(detailLabel);
        det.setText(detGet.get());
        fieldSavers.add(() -> detSet.set(det.getText().toString().trim()));
        box.addView(det);

        return box;
    }

    // ---- permissions ----
    private void requestLocation() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
    }

    private void requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 2);
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, new String[]{
                    "android.permission.POST_NOTIFICATIONS"}, 3);
        }
    }

    private void requestBluetooth() {
        if (Build.VERSION.SDK_INT >= 31) {
            ActivityCompat.requestPermissions(this, new String[]{
                    "android.permission.BLUETOOTH_CONNECT"}, 4);
        }
    }

    private void requestSleep() {
        if (!SleepReader.isAvailable(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Health Connect not available")
                    .setMessage("This phone doesn't have Health Connect set up. "
                            + "Install/open 'Health Connect' from the Play Store and make "
                            + "sure a sleep source (e.g. your watch or phone) writes sleep "
                            + "there, then try again. Sleep splitting is optional — the rest "
                            + "of the app works without it.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        try {
            sleepPermLauncher.launch(Collections.singleton(SleepReader.SLEEP_PERMISSION));
        } catch (Exception e) {
            toast("Couldn't open Health Connect permissions.");
        }
    }

    // ---- extra-metric post toggle row ----
    private View postToggle(String label, String metric, String defaultAttr) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(6));

        CheckBox cb = new CheckBox(this);
        cb.setText("Post \"" + label + "\" to Exist");
        cb.setChecked(settings.postEnabled(metric));
        cb.setOnCheckedChangeListener((v, on) -> settings.setPostEnabled(metric, on));
        box.addView(cb);

        EditText attr = new EditText(this);
        darkEt(attr);
        attr.setHint("Attribute name");
        attr.setText(settings.getAttrFor(metric, defaultAttr));
        fieldSavers.add(() -> settings.setAttrFor(metric, attr.getText().toString().trim()));
        box.addView(attr);

        Button choose = new Button(this);
        choose.setText("Choose from Exist / create new…");
        String grp = metric.startsWith("soc") || metric.startsWith("yt") ? "social"
                : metric.equals("sleep") ? "sleep"
                : metric.equals("screen") ? "media" : "productivity";
        choose.setOnClickListener(v -> new AttributePicker(this).choose(
                attr.getText().toString().trim(), grp, name -> {
                    attr.setText(name);
                    settings.setAttrFor(metric, name);
                }));
        box.addView(choose);

        return box;
    }

    /** A label above an input field, returned as one vertical block. */
    private View labeled(String label, EditText field) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(2));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(Ui.TEXT);
        box.addView(t);
        box.addView(field);
        return box;
    }

    /** A labeled minutes-input row for a dashboard color threshold. Blank = none. */
    private View thresholdRow(String label, String metric) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(6));

        TextView t = new TextView(this);
        t.setText(label + " (min):");
        t.setTextSize(13);
        t.setTextColor(Ui.TEXT);
        box.addView(t);

        EditText et = new EditText(this);
        darkEt(et);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setHint("blank = no color change");
        int cur = settings.getThreshold(metric);
        if (cur >= 0) et.setText(String.valueOf(cur));
        fieldSavers.add(() -> {
            String s = et.getText().toString().trim();
            if (s.isEmpty()) settings.setThreshold(metric, -1);
            else {
                try { settings.setThreshold(metric, Integer.parseInt(s)); }
                catch (NumberFormatException e) { settings.setThreshold(metric, -1); }
            }
        });
        box.addView(et);
        return box;
    }

    private void exportSettings() {
        try {
            exportLauncher.launch("exist-tracker-backup.json");
        } catch (Exception e) {
            toast("Could not open file picker.");
        }
    }

    private void importSettings() {
        try {
            importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
        } catch (Exception e) {
            toast("Could not open file picker.");
        }
    }

    // ---- extra-metric post toggle row END ----

    private void openUsageAccess() {
        startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
        toast("Find 'Exist Tracker' and turn it ON.");
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // ---- helpers ----
    private void refreshStatus() {
        String s = settings.isLoggedIn()
                ? "✓ Connected to Exist.\n" : "✗ Not connected to Exist yet.\n";
        s += "Last: " + settings.getLastStatus();
        statusView.setText(s);
    }

    private TextView header(String txt) {
        TextView t = new TextView(this);
        t.setText(txt); t.setTextSize(24); t.setTextColor(Ui.TEXT);
        return t;
    }
    private TextView section(String txt) {
        TextView t = new TextView(this);
        t.setText(txt); t.setTextSize(18); t.setPadding(0, dp(20), 0, dp(6));
        t.setTextColor(Ui.ACCENT);
        return t;
    }
    private TextView note(String txt) {
        TextView t = new TextView(this);
        t.setText(txt); t.setTextSize(13); t.setTextColor(Ui.MUTED);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }
    private Button permButton(String txt, View.OnClickListener l) {
        Button b = new Button(this); b.setText(txt); b.setOnClickListener(l); return b;
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    /** Style an EditText to be readable on the dark background. */
    private EditText darkEt(EditText e) {
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.MUTED);
        return e;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
