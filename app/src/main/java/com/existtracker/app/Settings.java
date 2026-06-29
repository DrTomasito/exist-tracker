package com.existtracker.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores all configuration and the running daily counters.
 * Everything is kept in a simple key/value file on the phone.
 */
public class Settings {

    private static final String FILE = "exist_tracker_prefs";
    private final SharedPreferences prefs;
    private final Context context;

    public Settings(Context ctx) {
        context = ctx.getApplicationContext();
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ----- OAuth credentials -----
    public String getClientId()      { return prefs.getString("client_id", ""); }
    public String getClientSecret()  { return prefs.getString("client_secret", ""); }
    public String getAccessToken()   { return prefs.getString("access_token", ""); }
    public String getRefreshToken()  { return prefs.getString("refresh_token", ""); }

    public void setClientId(String v)     { prefs.edit().putString("client_id", v).apply(); }
    public void setClientSecret(String v) { prefs.edit().putString("client_secret", v).apply(); }
    public void setTokens(String access, String refresh) {
        prefs.edit().putString("access_token", access)
                    .putString("refresh_token", refresh).apply();
    }
    public boolean isLoggedIn() { return getAccessToken().length() > 0; }

    // Whether the user WANTS tracking on (toggled from the dashboard). Defaults
    // to true so tracking runs by default; set false only by the STOP action.
    public boolean isTrackingDesired() { return prefs.getBoolean("tracking_desired", true); }
    public void setTrackingDesired(boolean on) { prefs.edit().putBoolean("tracking_desired", on).apply(); }

    // ----- The five trackers. Each has: an attribute name, and the
    //       SSID / package / device value used to detect it. -----

    // Defaults match the user's original request.
    public String getHospitalAttr() { return prefs.getString("hospital_attr", "hospital_time"); }
    public String getHospitalSsid() { return prefs.getString("hospital_ssid", "NCHS-GUEST"); }

    public String getHomeAttr()     { return prefs.getString("home_attr", "home_time"); }
    public String getHomeSsid()     { return prefs.getString("home_ssid", "Gilligan"); }

    public String getChurchSsid()   { return prefs.getString("church_ssid", "GablesUCC_Guest"); }
    public void setChurchSsid(String v) { prefs.edit().putString("church_ssid", v).apply(); }
    public String getChurchAttr()   { return prefs.getString("church_attr", "time_at_church"); }
    public void setChurchAttr(String v) { prefs.edit().putString("church_attr", v).apply(); }

    public String getYoutubeAttr()  { return prefs.getString("youtube_attr", "youtube_time"); }
    public String getYoutubePkgs()  { return prefs.getString("youtube_pkgs", "com.google.android.youtube"); }

    public String getSocialAttr()   { return prefs.getString("social_attr", "social_media_time"); }
    public String getSocialPkgs()   { return prefs.getString("social_pkgs",
            "com.instagram.android,com.facebook.katana"); }

    public String getDrivingAttr()  { return prefs.getString("driving_attr", "time_driving"); }
    public String getDrivingDevice(){ return prefs.getString("driving_device", "Handsfreelink"); }

    public void setHospitalAttr(String v) { prefs.edit().putString("hospital_attr", v).apply(); }
    public void setHospitalSsid(String v) { prefs.edit().putString("hospital_ssid", v).apply(); }
    public void setHomeAttr(String v)     { prefs.edit().putString("home_attr", v).apply(); }
    public void setHomeSsid(String v)     { prefs.edit().putString("home_ssid", v).apply(); }
    public void setYoutubeAttr(String v)  { prefs.edit().putString("youtube_attr", v).apply(); }
    public void setYoutubePkgs(String v)  { prefs.edit().putString("youtube_pkgs", v).apply(); }
    public void setSocialAttr(String v)   { prefs.edit().putString("social_attr", v).apply(); }
    public void setSocialPkgs(String v)   { prefs.edit().putString("social_pkgs", v).apply(); }
    public void setDrivingAttr(String v)  { prefs.edit().putString("driving_attr", v).apply(); }
    public void setDrivingDevice(String v){ prefs.edit().putString("driving_device", v).apply(); }

    // ----- Daily running counters, in whole minutes -----
    public int getHospitalMin() { return prefs.getInt("c_hospital", 0); }
    public int getHomeMin()     { return prefs.getInt("c_home", 0); }
    public int getYoutubeMin()  { return prefs.getInt("c_youtube", 0); }
    public int getSocialMin()   { return prefs.getInt("c_social", 0); }
    public int getDrivingMin()  { return prefs.getInt("c_driving", 0); }
    public int getChurchMin()   { return prefs.getInt("c_church", 0); }
    public int getScreenHome()  { return prefs.getInt("c_screen_home", 0); }

    public void addHospital(int m){ prefs.edit().putInt("c_hospital", getHospitalMin()+m).apply(); }
    public void addHome(int m)    { prefs.edit().putInt("c_home", getHomeMin()+m).apply(); }
    public void addYoutube(int m) { prefs.edit().putInt("c_youtube", getYoutubeMin()+m).apply(); }
    public void addSocial(int m)  { prefs.edit().putInt("c_social", getSocialMin()+m).apply(); }
    // Authoritative daily totals (set from the OS usage stats, not accumulated).
    public void setYoutube(int m) { prefs.edit().putInt("c_youtube", Math.max(0, m)).apply(); }
    public void setSocial(int m)  { prefs.edit().putInt("c_social", Math.max(0, m)).apply(); }
    public void addDriving(int m) { prefs.edit().putInt("c_driving", getDrivingMin()+m).apply(); }
    public void addChurch(int m)  { prefs.edit().putInt("c_church", getChurchMin()+m).apply(); }
    public void addScreenHome(int m) { prefs.edit().putInt("c_screen_home", getScreenHome()+m).apply(); }

    // Church arrival = first time connected to church WiFi today (minute of day).
    // Captured for backend inferences (late-to-church, choir) — not shown in app.
    public int getChurchArrivalToday() { return prefs.getInt("church_arrival_today", -1); }
    public void setChurchArrivalToday(int minOfDay) {
        prefs.edit().putInt("church_arrival_today", minOfDay).apply();
    }
    public void saveChurchArrival(String date, int minOfDay) {
        prefs.edit().putInt("churcharr_" + date, minOfDay).apply();
    }
    public java.util.TreeMap<String, Integer> getChurchArrivalHistory() { return scan("churcharr_"); }

    public void clearCounters() {
        prefs.edit().putInt("c_hospital", 0).putInt("c_home", 0)
                .putInt("c_youtube", 0).putInt("c_social", 0)
                .putInt("c_driving", 0)
                // new split + device counters
                .putInt("c_yt_work", 0).putInt("c_yt_home", 0).putInt("c_yt_away", 0)
                .putInt("c_soc_work", 0).putInt("c_soc_home", 0).putInt("c_soc_away", 0)
                .putInt("c_screen", 0)
                .putInt("work_distract", 0).putInt("work_youtube", 0)
                .putInt("c_together", 0)
                .putInt("c_church", 0)
                .putInt("c_screen_home", 0)
                .apply();
        clearArrivalDeparture();
        clearHomeArrival();
        prefs.edit().putInt("church_arrival_today", -1).apply();
    }

    // ----- App-usage split by location (work / home / away), minutes -----
    public int getYtWork() { return prefs.getInt("c_yt_work", 0); }
    public int getYtHome() { return prefs.getInt("c_yt_home", 0); }
    public int getYtAway() { return prefs.getInt("c_yt_away", 0); }
    public int getSocWork(){ return prefs.getInt("c_soc_work", 0); }
    public int getSocHome(){ return prefs.getInt("c_soc_home", 0); }
    public int getSocAway(){ return prefs.getInt("c_soc_away", 0); }

    public void addYtBucket(String b, int m)  { String k="c_yt_"+b;  prefs.edit().putInt(k, prefs.getInt(k,0)+m).apply(); }
    public void addSocBucket(String b, int m) { String k="c_soc_"+b; prefs.edit().putInt(k, prefs.getInt(k,0)+m).apply(); }

    // ----- Screen unlocked / in-use minutes -----
    public int getScreenMin() { return prefs.getInt("c_screen", 0); }
    public void addScreen(int m) { prefs.edit().putInt("c_screen", getScreenMin()+m).apply(); }

    // attribute name for screen time (Exist "media" or custom group)
    public String getScreenAttr() { return prefs.getString("screen_attr", "screen_time"); }
    public void setScreenAttr(String v) { prefs.edit().putString("screen_attr", v).apply(); }

    // ----- Sleep (read from Health Connect), minutes asleep last night -----
    public int getSleepMin() { return prefs.getInt("c_sleep", 0); }
    public void setSleepMin(int m) { prefs.edit().putInt("c_sleep", m).apply(); }
    // Read sleep FROM Exist (Health Connect already syncs there) instead of
    // reading Health Connect directly. When on, uses the attribute name below.
    public boolean getSleepFromExist() { return prefs.getBoolean("sleep_from_exist", false); }
    public void setSleepFromExist(boolean b) { prefs.edit().putBoolean("sleep_from_exist", b).apply(); }
    public String getSleepExistAttr() { return prefs.getString("sleep_exist_attr", "time_asleep"); }
    public void setSleepExistAttr(String v) { prefs.edit().putString("sleep_exist_attr", v).apply(); }

    // ----- Per-metric "also post to Exist" toggles (default off for new ones) -----
    public boolean postEnabled(String metric) { return prefs.getBoolean("post_" + metric, false); }
    public void setPostEnabled(String metric, boolean on) { prefs.edit().putBoolean("post_" + metric, on).apply(); }
    // attribute names for the new postable metrics
    public String getAttrFor(String metric, String dflt) { return prefs.getString("attr_" + metric, dflt); }
    public void setAttrFor(String metric, String v) { prefs.edit().putString("attr_" + metric, v).apply(); }

    // ----- Per-metric dashboard color thresholds (in MINUTES). -----
    // A threshold of -1 (default) means "no threshold set" → never recolors.
    // Direction: most are "stay under" limits (turn warning when value >= limit).
    // "Together" metrics are "reach at least" goals (warning when value < goal,
    // i.e. you haven't hit your target yet) — handled by getThresholdColorBelow.
    public int getThreshold(String metric) { return prefs.getInt("thresh_" + metric, -1); }
    public void setThreshold(String metric, int minutes) {
        prefs.edit().putInt("thresh_" + metric, minutes).apply();
    }

    // ----- Cloud backup (Supabase) config -----
    public String getCloudUrl()    { return prefs.getString("cloud_url", ""); }
    public String getCloudKey()    { return prefs.getString("cloud_key", ""); }
    public String getCloudDevice() { return prefs.getString("cloud_device", "tj-pixel"); }
    public boolean getCloudAuto()  { return prefs.getBoolean("cloud_auto", false); }
    public String getCloudLastStatus() { return prefs.getString("cloud_last", "Never"); }
    public void setCloudUrl(String v)    { prefs.edit().putString("cloud_url", v).apply(); }
    public void setCloudKey(String v)    { prefs.edit().putString("cloud_key", v).apply(); }
    public void setCloudDevice(String v) { prefs.edit().putString("cloud_device", v).apply(); }
    public void setCloudAuto(boolean b)  { prefs.edit().putBoolean("cloud_auto", b).apply(); }
    public void setCloudLastStatus(String v) { prefs.edit().putString("cloud_last", v).apply(); }

    // ----- Per-tracker annotations (private context describing what a tracker
    // really measures + inferences it enables). Keyed by tracker id or metric
    // name. Travels to the cloud to inform the couples app / AI. -----
    public String getAnnotation(String key) { return prefs.getString("annot_" + key, ""); }
    public void setAnnotation(String key, String v) {
        prefs.edit().putString("annot_" + key, v).apply();
    }
    // All annotation keys currently stored (so cloud sync can enumerate them).
    public java.util.List<String> getAnnotationKeys() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String k : prefs.getAll().keySet())
            if (k.startsWith("annot_")) out.add(k.substring("annot_".length()));
        return out;
    }

    // ----- Hardwired inference toggles (each can be turned off). Default ON. -----
    public boolean inferenceEnabled(String key) { return prefs.getBoolean("infer_" + key, true); }
    public void setInferenceEnabled(String key, boolean on) {
        prefs.edit().putBoolean("infer_" + key, on).apply();
    }

    // ----- Inference RESULTS: a dated 0/1 flag per inference key, stored as
    // history so they count over time and sync to the cloud. Key form:
    // "inf_<key>_<date>" = 1 (true) or 0 (false). Only days where the inference
    // was evaluated get a row (e.g. weekday-only ones skip weekends). -----
    public void saveInference(String key, String date, boolean value) {
        prefs.edit().putInt("inf_" + key + "_" + date, value ? 1 : 0).apply();
    }
    public java.util.TreeMap<String, Integer> getInferenceHistory(String key) {
        return scan("inf_" + key + "_");
    }

    // For app-usage: remember total foreground seconds seen so far today,
    // so we count only the delta each tick.
    public long getUsageBaseline(String key) { return prefs.getLong("usage_" + key, -1); }
    public void setUsageBaseline(String key, long v) { prefs.edit().putLong("usage_" + key, v).apply(); }
    public void clearUsageBaselines() {
        prefs.edit().remove("usage_youtube").remove("usage_social").apply();
    }

    // ----- Work arrival / departure times (minutes-since-midnight) -----
    public int getArrivalToday()   { return prefs.getInt("arrival_today", -1); }
    public int getDepartureToday() { return prefs.getInt("departure_today", -1); }
    public void setArrivalToday(int minOfDay) { prefs.edit().putInt("arrival_today", minOfDay).apply(); }
    public void setDepartureToday(int minOfDay){ prefs.edit().putInt("departure_today", minOfDay).apply(); }
    public void clearArrivalDeparture() {
        prefs.edit().putInt("arrival_today", -1).putInt("departure_today", -1).apply();
    }
    public void saveArrival(String date, int minOfDay)   { prefs.edit().putInt("arr_" + date, minOfDay).apply(); }
    public void saveDeparture(String date, int minOfDay) { prefs.edit().putInt("dep_" + date, minOfDay).apply(); }
    public java.util.TreeMap<String, Integer> getArrivalHistory()   { return scan("arr_"); }
    public java.util.TreeMap<String, Integer> getDepartureHistory() { return scan("dep_"); }

    // ----- "Got home from work" arrival time (minutes-since-midnight) -----
    // Detected via home WiFi connect after leaving work that day. SSID-only.
    public int getHomeArrivalToday() { return prefs.getInt("home_arrival_today", -1); }
    public void setHomeArrivalToday(int minOfDay) { prefs.edit().putInt("home_arrival_today", minOfDay).apply(); }
    // Latch: have we left work since arriving today? (gates home-arrival capture)
    public boolean getLeftWorkToday() { return prefs.getBoolean("left_work_today", false); }
    public void setLeftWorkToday(boolean v) { prefs.edit().putBoolean("left_work_today", v).apply(); }
    public void clearHomeArrival() {
        prefs.edit().putInt("home_arrival_today", -1).putBoolean("left_work_today", false).apply();
    }
    public void saveHomeArrival(String date, int minOfDay) { prefs.edit().putInt("homearr_" + date, minOfDay).apply(); }
    public java.util.TreeMap<String, Integer> getHomeArrivalHistory() { return scan("homearr_"); }

    private java.util.TreeMap<String, Integer> scan(String prefix) {
        java.util.TreeMap<String, Integer> out = new java.util.TreeMap<>();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                Object v = prefs.getAll().get(key);
                if (v instanceof Integer) out.put(key.substring(prefix.length()), (Integer) v);
            }
        }
        return out;
    }

    // ----- Work-computer minutes pulled from the relay (extension) -----
    public int getWorkDistractMin() { return prefs.getInt("work_distract", 0); }
    public void setWorkDistractMin(int m) { prefs.edit().putInt("work_distract", m).apply(); }
    public int getWorkYoutubeMin() { return prefs.getInt("work_youtube", 0); }
    public void setWorkYoutubeMin(int m) { prefs.edit().putInt("work_youtube", m).apply(); }

    public String getRelayMode() { return prefs.getString("relay_mode", "off"); } // exist | jsonbin | off
    public void setRelayMode(String m) { prefs.edit().putString("relay_mode", m).apply(); }
    public String getRelayDistractAttr() { return prefs.getString("relay_distract_attr", "work_distractions"); }
    public void setRelayDistractAttr(String v) { prefs.edit().putString("relay_distract_attr", v).apply(); }
    public String getRelayYoutubeAttr() { return prefs.getString("relay_youtube_attr", "work_youtube"); }
    public void setRelayYoutubeAttr(String v) { prefs.edit().putString("relay_youtube_attr", v).apply(); }
    public String getJsonbinId() { return prefs.getString("jsonbin_id", ""); }
    public void setJsonbinId(String v) { prefs.edit().putString("jsonbin_id", v).apply(); }
    public String getJsonbinKey() { return prefs.getString("jsonbin_key", ""); }
    public void setJsonbinKey(String v) { prefs.edit().putString("jsonbin_key", v).apply(); }

    // ----- Location publishing (OwnTracks-compatible) for "together time" -----
    public boolean getLocationPublishEnabled() { return prefs.getBoolean("loc_pub_enabled", false); }
    public void setLocationPublishEnabled(boolean b) { prefs.edit().putBoolean("loc_pub_enabled", b).apply(); }
    public String getTogetherServerUrl() { return prefs.getString("together_url", ""); }
    public void setTogetherServerUrl(String v) { prefs.edit().putString("together_url", v).apply(); }
    public String getTogetherSecret() { return prefs.getString("together_secret", ""); }
    public void setTogetherSecret(String v) { prefs.edit().putString("together_secret", v).apply(); }
    public String getMyDeviceId() { return prefs.getString("my_device_id", "tj"); }
    public void setMyDeviceId(String v) { prefs.edit().putString("my_device_id", v).apply(); }

    // Together-time values pulled back from the backend / Exist for display.
    public int getTogetherMin() { return prefs.getInt("c_together", 0); }
    public void setTogetherMin(int m) { prefs.edit().putInt("c_together", m).apply(); }
    public String getTogetherAttr() { return prefs.getString("together_attr", "time_together"); }
    public void setTogetherAttr(String v) { prefs.edit().putString("together_attr", v).apply(); }
    // Whether to also post "together awake" (together minus my sleep) to Exist.
    public boolean getPostTogetherAwake() { return prefs.getBoolean("post_together_awake", false); }
    public void setPostTogetherAwake(boolean b) { prefs.edit().putBoolean("post_together_awake", b).apply(); }
    public String getTogetherAwakeAttr() { return prefs.getString("together_awake_attr", "time_together_awake"); }
    public void setTogetherAwakeAttr(String v) { prefs.edit().putString("together_awake_attr", v).apply(); }

    public String getLastStatus() { return prefs.getString("last_status", "Not posted yet."); }
    public void setLastStatus(String v) { prefs.edit().putString("last_status", v).apply(); }

    // ----- Direct token entry (skip the browser OAuth dance) -----
    // Store tokens the user pasted, plus their client id/secret (needed so the
    // app can refresh the access token automatically when it expires).
    public void setTokensDirect(String access, String refresh) {
        setTokens(access, refresh);
    }

    // ----- Daily history -----
    // We save one record per day per metric so the app can chart trends.
    // Stored as a key like "hist_hospital_2026-05-28" = minutes (int).
    public void saveHistory(String metric, String date, int minutes) {
        prefs.edit().putInt("hist_" + metric + "_" + date, minutes).apply();
    }

    /**
     * Return all saved daily values for a metric, sorted by date (oldest first).
     */
    public java.util.TreeMap<String, Integer> getHistory(String metric) {
        java.util.TreeMap<String, Integer> out = new java.util.TreeMap<>();
        String prefix = "hist_" + metric + "_";
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                String date = key.substring(prefix.length());
                // Guard: only accept an exact date suffix (YYYY-MM-DD). This
                // prevents metric "home" from also matching "home_awake_<date>"
                // (whose suffix would be "awake_<date>", not a valid date).
                if (!isDateStr(date)) continue;
                out.put(date, prefs.getInt(key, 0));
            }
        }
        return out;
    }

    /** True if s is exactly YYYY-MM-DD (10 chars, digits + hyphens). */
    private boolean isDateStr(String s) {
        if (s == null || s.length() != 10) return false;
        for (int i = 0; i < 10; i++) {
            char c = s.charAt(i);
            if (i == 4 || i == 7) { if (c != '-') return false; }
            else if (c < '0' || c > '9') return false;
        }
        return true;
    }

    // ----- Export / import all settings (for moving to a new phone) -----
    // Exports configuration + history as JSON text. We deliberately include
    // tokens so a restore is complete; treat the backup file as sensitive.
    public String exportToJson() {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            for (java.util.Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                Object v = e.getValue();
                // We only persist primitives; record type so import is exact.
                if (v instanceof String)  o.put("S|" + e.getKey(), v);
                else if (v instanceof Integer) o.put("I|" + e.getKey(), v);
                else if (v instanceof Boolean) o.put("B|" + e.getKey(), v);
                else if (v instanceof Long)    o.put("L|" + e.getKey(), v);
            }
            // Also include the stopwatches/counters/scales prefs (trackers, daily
            // totals, session log WITH notes) so a backup is complete. Keys are
            // prefixed with "SW::" so import can route them to the right file.
            SharedPreferences sw = context.getSharedPreferences(
                    "exist_stopwatches", Context.MODE_PRIVATE);
            for (java.util.Map.Entry<String, ?> e : sw.getAll().entrySet()) {
                Object v = e.getValue();
                String k = "SW::" + e.getKey();
                if (v instanceof String)  o.put("S|" + k, v);
                else if (v instanceof Integer) o.put("I|" + k, v);
                else if (v instanceof Boolean) o.put("B|" + k, v);
                else if (v instanceof Long)    o.put("L|" + k, v);
            }
            return o.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    public boolean importFromJson(String json) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            SharedPreferences.Editor ed = prefs.edit();
            SharedPreferences swPrefs = context.getSharedPreferences(
                    "exist_stopwatches", Context.MODE_PRIVATE);
            SharedPreferences.Editor swEd = swPrefs.edit();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String tagged = it.next();
                String type = tagged.substring(0, 1);
                String key = tagged.substring(2);
                // Route stopwatch-prefixed keys to the stopwatches file.
                SharedPreferences.Editor target = ed;
                if (key.startsWith("SW::")) { target = swEd; key = key.substring(4); }
                switch (type) {
                    case "S": target.putString(key, o.getString(tagged)); break;
                    case "I": target.putInt(key, o.getInt(tagged)); break;
                    case "B": target.putBoolean(key, o.getBoolean(tagged)); break;
                    case "L": target.putLong(key, o.getLong(tagged)); break;
                }
            }
            ed.apply();
            swEd.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
