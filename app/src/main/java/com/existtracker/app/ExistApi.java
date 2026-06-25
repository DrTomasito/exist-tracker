package com.existtracker.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Talks to the Exist.io v2 API.
 * Writing data requires OAuth2 (the simple token is read-only),
 * so this exchanges a code for tokens, refreshes them, creates
 * attributes, and posts daily values.
 */
public class ExistApi {

    // Exist requires the redirect URI to be HTTPS. We send the user back to
    // their own Exist apps page; after they tap "Allow", the browser address
    // bar will show ...?code=XXXXXX which they paste into this app.
    public static final String REDIRECT_URI = "https://exist.io/account/apps/";
    private static final String BASE = "https://exist.io";

    private final Settings settings;

    public ExistApi(Context ctx) {
        this.settings = new Settings(ctx);
    }

    /** The URL we send the user to in order to grant access. */
    public String buildAuthorizeUrl() {
        // Request read+write for the groups this app touches, plus manual_*
        // so it can create/own custom attributes. Broad but appropriate for a
        // personal app that reads existing attributes and writes to several
        // groups (location, social, media, productivity, sleep).
        String scopes = "manual_read+manual_write"
                + "+location_read+location_write"
                + "+social_read+social_write"
                + "+media_read+media_write"
                + "+productivity_read+productivity_write"
                + "+sleep_read+sleep_write"
                + "+activity_read+activity_write";
        return BASE + "/oauth2/authorize/?response_type=code"
                + "&client_id=" + enc(settings.getClientId())
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&scope=" + scopes;
    }

    /** Exchange the one-time code returned in the redirect for tokens. */
    public boolean exchangeCodeForToken(String code) throws IOException {
        String body = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&client_id=" + enc(settings.getClientId())
                + "&client_secret=" + enc(settings.getClientSecret())
                + "&redirect_uri=" + enc(REDIRECT_URI);
        String resp = post(BASE + "/oauth2/access_token", body,
                "application/x-www-form-urlencoded", null);
        return storeTokens(resp);
    }

    /** Use the refresh token to get a fresh access token. */
    public boolean refresh() throws IOException {
        if (settings.getRefreshToken().isEmpty()) return false;
        String body = "grant_type=refresh_token"
                + "&refresh_token=" + enc(settings.getRefreshToken())
                + "&client_id=" + enc(settings.getClientId())
                + "&client_secret=" + enc(settings.getClientSecret());
        String resp = post(BASE + "/oauth2/access_token", body,
                "application/x-www-form-urlencoded", null);
        return storeTokens(resp);
    }

    private boolean storeTokens(String resp) {
        try {
            JSONObject o = new JSONObject(resp);
            if (o.has("access_token")) {
                settings.setTokens(o.getString("access_token"),
                        o.optString("refresh_token", settings.getRefreshToken()));
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Make sure an attribute exists and is owned by us.
     * group is "location" or "social". value_type 0 = integer.
     * Safe to call repeatedly; Exist ignores duplicates.
     */
    public void ensureAttribute(String name, String label, String group) throws IOException {
        JSONArray arr = new JSONArray();
        try {
            JSONObject a = new JSONObject();
            a.put("label", label);
            a.put("group", group);
            a.put("value_type", 0); // integer (minutes)
            a.put("manual", false);
            arr.put(a);
        } catch (Exception ignored) {}
        // Also try to acquire ownership in case it already exists
        postAuthed(BASE + "/api/2/attributes/create/?success_objects=1",
                arr.toString());
        // Acquire ownership (no error if already owned)
        JSONArray own = new JSONArray();
        try {
            JSONObject a = new JSONObject();
            a.put("name", name);
            own.put(a);
        } catch (Exception ignored) {}
        postAuthed(BASE + "/api/2/attributes/acquire/", own.toString());
    }

    /** Post one day's value (whole minutes) for an attribute. */
    public String updateValue(String name, String date, int value) throws IOException {
        JSONArray arr = new JSONArray();
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("date", date);
            o.put("value", value);
            arr.put(o);
        } catch (Exception ignored) {}
        return postAuthed(BASE + "/api/2/attributes/update/", arr.toString());
    }

    /**
     * Read today's value for an attribute (used to pull work-computer minutes
     * that the browser extension wrote to Exist). Returns 0 if unavailable.
     */
    public int readTodayValue(String name) throws IOException {
        String url = BASE + "/api/2/attributes/values/?attribute=" + enc(name) + "&limit=1";
        String resp = getAuthed(url);
        try {
            JSONObject o = new JSONObject(resp);
            JSONArray results = o.optJSONArray("results");
            if (results != null && results.length() > 0) {
                JSONObject first = results.getJSONObject(0);
                return first.optInt("value", 0);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * A lightweight description of an Exist attribute for the picker UI.
     */
    public static class AttrInfo {
        public String name;       // machine name, e.g. "hospital_time"
        public String label;      // human label, e.g. "Hospital Time"
        public String groupName;  // e.g. "social"
        public String groupLabel; // e.g. "Social"
        public boolean owned;     // true if this app can already write to it
    }

    /** List ALL of the user's attributes (across services), with group info. */
    public java.util.List<AttrInfo> listAllAttributes() throws IOException {
        java.util.List<AttrInfo> out = new java.util.ArrayList<>();
        String url = BASE + "/api/2/attributes/?limit=100";
        // Page through results.
        while (url != null) {
            String resp = getAuthed(url);
            try {
                JSONObject o = new JSONObject(resp);
                JSONArray results = o.optJSONArray("results");
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject a = results.getJSONObject(i);
                        AttrInfo info = new AttrInfo();
                        info.name = a.optString("name", "");
                        info.label = a.optString("label", info.name);
                        JSONObject g = a.optJSONObject("group");
                        if (g != null) {
                            info.groupName = g.optString("name", "");
                            info.groupLabel = g.optString("label", info.groupName);
                        }
                        out.add(info);
                    }
                }
                url = o.isNull("next") ? null : o.optString("next", null);
                if (url != null && url.isEmpty()) url = null;
            } catch (Exception e) {
                url = null;
            }
        }
        // Mark which ones we own (can write to).
        java.util.Set<String> ownedNames = listOwnedNames();
        for (AttrInfo a : out) a.owned = ownedNames.contains(a.name);
        return out;
    }

    /** Names of attributes this app currently owns. */
    public java.util.Set<String> listOwnedNames() throws IOException {
        java.util.Set<String> set = new java.util.HashSet<>();
        String resp = getAuthed(BASE + "/api/2/attributes/owned/?limit=100");
        try {
            JSONObject o = new JSONObject(resp);
            JSONArray results = o.optJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    set.add(results.getJSONObject(i).optString("name", ""));
                }
            }
        } catch (Exception ignored) {}
        return set;
    }

    /**
     * Create a brand-new custom attribute with a label, group, and integer type,
     * then acquire ownership so we can write to it. Returns the machine name
     * Exist assigned (derived from the label), or null on failure.
     */
    public String createCustomAttribute(String label, String groupName) throws IOException {
        JSONArray arr = new JSONArray();
        try {
            JSONObject a = new JSONObject();
            a.put("label", label);
            a.put("group", groupName);
            a.put("value_type", 0); // integer (minutes)
            a.put("manual", true);
            arr.put(a);
        } catch (Exception ignored) {}
        String resp = postAuthed(BASE + "/api/2/attributes/create/?success_objects=1",
                arr.toString());
        // Parse out the created name from the success array.
        try {
            JSONObject o = new JSONObject(resp);
            JSONArray success = o.optJSONArray("success");
            if (success != null && success.length() > 0) {
                String name = success.getJSONObject(0).optString("name", null);
                if (name != null) {
                    // Acquire ownership (create usually grants it, but be safe).
                    JSONArray own = new JSONArray();
                    JSONObject ao = new JSONObject();
                    ao.put("name", name);
                    own.put(ao);
                    postAuthed(BASE + "/api/2/attributes/acquire/", own.toString());
                    return name;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getAuthed(String url) throws IOException {        return getAuthed(url, true);
    }

    private String getAuthed(String url, boolean allowRetry) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Authorization", "Bearer " + settings.getAccessToken());
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String resp = readAll(is);
            if (code == 401 && allowRetry && refresh()) {
                return getAuthed(url, false); // retry once after refresh
            }
            return resp;
        } finally {
            c.disconnect();
        }
    }

    // ---- HTTP helpers ----

    private String postAuthed(String url, String jsonBody) throws IOException {
        String resp = post(url, jsonBody, "application/json",
                "Bearer " + settings.getAccessToken());
        // If unauthorized, refresh once and retry.
        if (resp != null && resp.contains("\"error\"") && resp.contains("auth")) {
            if (refresh()) {
                resp = post(url, jsonBody, "application/json",
                        "Bearer " + settings.getAccessToken());
            }
        }
        return resp;
    }

    private String post(String urlStr, String body, String contentType,
                        String authHeader) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            if (authHeader != null) c.setRequestProperty("Authorization", authHeader);
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = c.getOutputStream()) { os.write(out); }
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? c.getInputStream() : c.getErrorStream();
            return readAll(is);
        } finally {
            c.disconnect();
        }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}
