package com.existtracker.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Manages the user's stopwatches: their definitions, whatever is currently
 * running, the per-day accumulated totals, and a browsable session log.
 *
 * Running state is stored as a "running_since" wall-clock timestamp, so a
 * stopwatch keeps counting correctly even while the app is backgrounded —
 * elapsed time is always (now - running_since) + any banked paused time.
 * A phone restart clears running state (acceptable per design).
 */
public class Stopwatches {

    private static final String FILE = "exist_stopwatches";
    private final SharedPreferences prefs;

    public Stopwatches(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---------- Stopwatch definitions ----------
    /**
     * A stopwatch definition. Fields:
     *  id        - stable unique id
     *  name      - display name ("Charting")
     *  attr      - Exist attribute to post the daily total to
     *  color     - hex color for the button
     *  runSince  - 0 if not running; else epoch ms when the current run started
     *  banked    - seconds accumulated in the current (paused) session so far
     */
    public static class SW {
        public String id, name, attr, color;
        public String type = "timer";   // "timer" (stopwatch) or "counter" (clicker)
        public boolean pushToExist = true;  // also post the daily total to Exist?
        public boolean pinned = false;      // show on the homepage?
        public int order = 0;               // sort order in the tab/home
        public int valueType = 3;           // Exist value type: 3=duration(min), 0=integer
        public long runSince;   // timer only: 0 = stopped
        public long banked;     // timer only: seconds banked while paused
        public boolean isRunning() { return runSince > 0; }
        public boolean isCounter() { return "counter".equals(type); }
        public boolean isScale() { return "scale".equals(type); }
        /** Current live session seconds (banked + time since last resume). */
        public long currentSessionSeconds() {
            long live = runSince > 0 ? (System.currentTimeMillis() - runSince) / 1000 : 0;
            return banked + live;
        }
    }

    public List<SW> getAll() {
        List<SW> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("defs", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                SW s = new SW();
                s.id = o.getString("id");
                s.name = o.getString("name");
                s.attr = o.optString("attr", "");
                s.color = o.optString("color", "#1565C0");
                s.type = o.optString("type", "timer");
                s.pushToExist = o.optBoolean("pushToExist", true);
                s.pinned = o.optBoolean("pinned", false);
                s.order = o.optInt("order", i);
                s.valueType = o.optInt("valueType",
                        "counter".equals(s.type) ? 0 : ("scale".equals(s.type) ? 8 : 3));
                s.runSince = o.optLong("runSince", 0);
                s.banked = o.optLong("banked", 0);
                out.add(s);
            }
        } catch (Exception ignored) {}
        // Keep them in the user's chosen order.
        out.sort((a, b) -> Integer.compare(a.order, b.order));
        return out;
    }

    private void saveAll(List<SW> list) {
        JSONArray arr = new JSONArray();
        for (SW s : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name);
                o.put("attr", s.attr);
                o.put("color", s.color);
                o.put("type", s.type);
                o.put("pushToExist", s.pushToExist);
                o.put("pinned", s.pinned);
                o.put("order", s.order);
                o.put("valueType", s.valueType);
                o.put("runSince", s.runSince);
                o.put("banked", s.banked);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        prefs.edit().putString("defs", arr.toString()).apply();
    }

    public SW get(String id) {
        for (SW s : getAll()) if (s.id.equals(id)) return s;
        return null;
    }

    public void addStopwatch(String name, String attr, String color) {
        addTracker(name, attr, color, "timer", true, false);
    }

    public void addTracker(String name, String attr, String color, String type,
                           boolean pushToExist, boolean pinned) {
        addTracker(name, attr, color, type, pushToExist, pinned,
                "counter".equals(type) ? 0 : ("scale".equals(type) ? 8 : 3));
    }

    public void addTracker(String name, String attr, String color, String type,
                           boolean pushToExist, boolean pinned, int valueType) {
        List<SW> list = getAll();
        SW s = new SW();
        s.id = "sw_" + System.currentTimeMillis();
        s.name = name;
        s.attr = attr;
        s.color = color;
        s.type = type;
        s.pushToExist = pushToExist;
        s.pinned = pinned;
        s.valueType = valueType;
        s.order = list.size();
        s.runSince = 0;
        s.banked = 0;
        list.add(s);
        saveAll(list);
    }

    public void updateStopwatch(String id, String name, String attr, String color) {
        List<SW> list = getAll();
        for (SW s : list) if (s.id.equals(id)) { s.name = name; s.attr = attr; s.color = color; }
        saveAll(list);
    }

    public void updateTracker(String id, String name, String attr, String color,
                              boolean pushToExist, boolean pinned) {
        List<SW> list = getAll();
        for (SW s : list) if (s.id.equals(id)) {
            s.name = name; s.attr = attr; s.color = color;
            s.pushToExist = pushToExist; s.pinned = pinned;
        }
        saveAll(list);
    }

    public void updateTracker(String id, String name, String attr, String color,
                              boolean pushToExist, boolean pinned, int valueType) {
        List<SW> list = getAll();
        for (SW s : list) if (s.id.equals(id)) {
            s.name = name; s.attr = attr; s.color = color;
            s.pushToExist = pushToExist; s.pinned = pinned; s.valueType = valueType;
        }
        saveAll(list);
    }

    public void deleteStopwatch(String id) {
        List<SW> list = getAll();
        list.removeIf(s -> s.id.equals(id));
        // renumber order
        for (int i = 0; i < list.size(); i++) list.get(i).order = i;
        saveAll(list);
    }

    /** Move a tracker up or down in the ordering. */
    public void move(String id, int delta) {
        List<SW> list = getAll(); // already sorted by order
        int idx = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(id)) { idx = i; break; }
        if (idx < 0) return;
        int target = idx + delta;
        if (target < 0 || target >= list.size()) return;
        SW tmp = list.get(idx); list.set(idx, list.get(target)); list.set(target, tmp);
        for (int i = 0; i < list.size(); i++) list.get(i).order = i;
        saveAll(list);
    }

    public List<SW> getPinned() {
        List<SW> out = new ArrayList<>();
        for (SW s : getAll()) if (s.pinned) out.add(s);
        return out;
    }

    /** Increment a counter by 1 for today (and log it). */
    public void increment(String id) {
        SW s = get(id);
        if (s == null) return;
        addToDailyTotal(id, todayStr(), 1);
        appendLog(id, s.name, todayStr(), 1);
    }

    /** Decrement a counter by 1 for today (won't go below 0), for mistaps. */
    public void decrement(String id) {
        SW s = get(id);
        if (s == null) return;
        int cur = getDailyTotal(id, todayStr());
        if (cur <= 0) return;
        prefs.edit().putInt("total_" + id + "_" + todayStr(), cur - 1).apply();
    }

    /** Set today's scale value (1-9) for a scale tracker, replacing any prior
     * value for today. Stored directly, not accumulated. */
    public void setScaleValue(String id, int value) {
        SW s = get(id);
        if (s == null) return;
        if (value < 1) value = 1;
        if (value > 9) value = 9;
        prefs.edit().putInt("total_" + id + "_" + todayStr(), value).apply();
        // Replace today's log entry rather than stacking duplicates.
        replaceTodayLog(id, s.name, todayStr(), value);
    }

    // ---------- Run controls ----------
    public void start(String id) {
        List<SW> list = getAll();
        for (SW s : list) if (s.id.equals(id) && s.runSince == 0) {
            s.runSince = System.currentTimeMillis();
        }
        saveAll(list);
    }

    public void pause(String id) {
        List<SW> list = getAll();
        for (SW s : list) if (s.id.equals(id) && s.runSince > 0) {
            s.banked += (System.currentTimeMillis() - s.runSince) / 1000;
            s.runSince = 0;
        }
        saveAll(list);
    }

    /** Stop and SAVE the session: add to today's total + append to log, reset. */
    public void saveSession(String id) {
        List<SW> list = getAll();
        for (SW s : list) if (s.id.equals(id)) {
            long secs = s.currentSessionSeconds();
            int mins = (int) Math.round(secs / 60.0);
            if (mins > 0) {
                addToDailyTotal(s.id, todayStr(), mins);
                appendLog(s.id, s.name, todayStr(), mins);
            }
            s.runSince = 0;
            s.banked = 0;
        }
        saveAll(list);
    }

    /** Cancel the current session without saving anything. */
    public void cancelSession(String id) {
        List<SW> list = getAll();
        for (SW s : list) if (s.id.equals(id)) { s.runSince = 0; s.banked = 0; }
        saveAll(list);
    }

    public boolean anyRunning() {
        for (SW s : getAll()) if (s.isRunning()) return true;
        return false;
    }

    public int runningCount() {
        int n = 0;
        for (SW s : getAll()) if (s.isRunning()) n++;
        return n;
    }

    // ---------- Daily totals ----------
    // Stored per stopwatch per day: key "total_<id>_<date>" = minutes.
    public int getDailyTotal(String id, String date) {
        return prefs.getInt("total_" + id + "_" + date, 0);
    }

    private void addToDailyTotal(String id, String date, int mins) {
        prefs.edit().putInt("total_" + id + "_" + date,
                getDailyTotal(id, date) + mins).apply();
    }

    /** History for a stopwatch as date->minutes (sorted). */
    public java.util.TreeMap<String, Integer> getHistory(String id) {
        java.util.TreeMap<String, Integer> out = new java.util.TreeMap<>();
        String prefix = "total_" + id + "_";
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                Object v = prefs.getAll().get(key);
                if (v instanceof Integer) out.put(key.substring(prefix.length()), (Integer) v);
            }
        }
        return out;
    }

    // ---------- Session log ----------
    public static class LogEntry {
        public String id, name, date;
        public int minutes;
        public long ts;
    }

    public List<LogEntry> getLog(int limit) {
        List<LogEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("log", "[]"));
            for (int i = arr.length() - 1; i >= 0 && out.size() < limit; i--) {
                JSONObject o = arr.getJSONObject(i);
                LogEntry e = new LogEntry();
                e.id = o.optString("id");
                e.name = o.optString("name");
                e.date = o.optString("date");
                e.minutes = o.optInt("minutes");
                e.ts = o.optLong("ts");
                out.add(e);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void appendLog(String id, String name, String date, int minutes) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("log", "[]"));
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("name", name); o.put("date", date);
            o.put("minutes", minutes); o.put("ts", System.currentTimeMillis());
            arr.put(o);
            // Keep the log from growing without bound: cap at 500 entries.
            while (arr.length() > 500) arr.remove(0);
            prefs.edit().putString("log", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void deleteLogEntry(long ts) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("log", "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optLong("ts") == ts) {
                    // also subtract from that day's total
                    String id = o.optString("id");
                    String date = o.optString("date");
                    int mins = o.optInt("minutes");
                    int cur = getDailyTotal(id, date);
                    prefs.edit().putInt("total_" + id + "_" + date,
                            Math.max(0, cur - mins)).apply();
                } else {
                    keep.put(o);
                }
            }
            prefs.edit().putString("log", keep.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Replace today's log entry for a tracker (used by scale: one value/day). */
    private void replaceTodayLog(String id, String name, String date, int value) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("log", "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (id.equals(o.optString("id")) && date.equals(o.optString("date"))) continue;
                keep.put(o);
            }
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("name", name); o.put("date", date);
            o.put("minutes", value); o.put("ts", System.currentTimeMillis());
            keep.put(o);
            while (keep.length() > 500) keep.remove(0);
            prefs.edit().putString("log", keep.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Days since this tracker was last logged, or -1 if never. */
    public int daysSinceLastLog(String id) {
        String last = lastLogDate(id);
        if (last == null) return -1;
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long then = f.parse(last).getTime();
            long now = f.parse(todayStr()).getTime();
            return (int) Math.round((now - then) / (1000.0 * 60 * 60 * 24));
        } catch (Exception e) { return -1; }
    }

    /** The most recent date string this tracker was logged, or null. */
    public String lastLogDate(String id) {
        String best = null;
        try {
            JSONArray arr = new JSONArray(prefs.getString("log", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (id.equals(o.optString("id"))) {
                    String d = o.optString("date");
                    if (best == null || d.compareTo(best) > 0) best = d;
                }
            }
        } catch (Exception ignored) {}
        return best;
    }

    /** Number of times this counter was logged in the last n days (inclusive). */
    public int countInLastDays(String id, int n) {
        int total = 0;
        java.util.TreeMap<String, Integer> hist = getHistory(id);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int i = 0; i < n; i++) {
            String d = String.format(Locale.US, "%04d-%02d-%02d",
                    cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH));
            Integer v = hist.get(d);
            if (v != null) total += v;
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }
        return total;
    }

    public static String todayStr() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
