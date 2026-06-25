package com.existtracker.app;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Reads the work-computer minutes from a jsonbin.io "bin" — the fallback relay
 * if writing to Exist from the extension doesn't work in the user's
 * environment. The extension writes a small JSON object like:
 *   { "date": "2026-06-17", "distract": 47, "youtube": 12 }
 * and this reads it back. Returns null on any failure.
 */
public class RelayReader {

    public static class WorkData {
        public String date;
        public int distract;
        public int youtube;
    }

    /** Read the latest record from a jsonbin bin. */
    public static WorkData readJsonbin(String binId, String accessKey) {
        if (binId == null || binId.isEmpty()) return null;
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://api.jsonbin.io/v3/b/" + binId + "/latest");
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            if (accessKey != null && !accessKey.isEmpty()) {
                c.setRequestProperty("X-Access-Key", accessKey);
            }
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String resp = readAll(is);
            JSONObject o = new JSONObject(resp);
            // jsonbin wraps the stored value in a "record" field
            JSONObject record = o.optJSONObject("record");
            if (record == null) record = o;
            WorkData d = new WorkData();
            d.date = record.optString("date", "");
            d.distract = record.optInt("distract", 0);
            d.youtube = record.optInt("youtube", 0);
            return d;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
