package com.existtracker.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The home screen: a calm, card-based summary of today plus the two charts
 * the user most wanted — when they leave work (trend) and average leave time
 * by weekday. Everything here reads from local history; no network needed.
 */
public class DashboardActivity extends AppCompatActivity {

    private Settings settings;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        settings = new Settings(this);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        setContentView(build());
    }

    @Override protected void onResume() { super.onResume(); setContentView(build()); }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 28), p, p);
        scroll.addView(root);

        // Header
        root.addView(Ui.eyebrow(this, "Your day, measured"));
        root.addView(Ui.title(this, "Dashboard"));
        TextView status = Ui.label(this, settings.isLoggedIn()
                ? "Synced to Exist · " + settings.getLastStatus()
                : "Not connected to Exist yet — open Settings.");
        status.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        root.addView(status);

        // Consistent navigation across screens.
        root.addView(Ui.navRow(this, "dashboard"));

        // --- Today at a glance: work in/out ---
        LinearLayout workCard = Ui.card(this);
        workCard.addView(Ui.eyebrow(this, "Today at work"));
        int arr = settings.getArrivalToday();
        int dep = settings.getDepartureToday();
        LinearLayout inOut = Ui.statRow(this);
        inOut.addView(Ui.statCell(this, Trends.minToClock(arr), "Arrived", Ui.GOOD));
        inOut.addView(Ui.statCell(this, dep < 0 ? "—" : Trends.minToClock(dep),
                "Last seen", Ui.ACCENT));
        workCard.addView(inOut);
        TextView hint = Ui.label(this, dep < 0
                ? "Departure fills in through the day as your phone leaves the hospital WiFi."
                : "“Last seen” becomes your departure time once you've left for the evening.");
        hint.setPadding(Ui.dp(this,4), Ui.dp(this,6), Ui.dp(this,4), 0);
        workCard.addView(hint);
        root.addView(workCard);

        // --- Got home from work (yesterday + this week) ---
        root.addView(buildHomeByCard());

        // --- Pinned counters/timers (configurable in the Counters tab) ---
        addPinnedTrackers(root);

        // --- Today's time buckets ---
        LinearLayout glance = Ui.card(this);
        glance.addView(Ui.eyebrow(this, "Today so far"));
        LinearLayout r1 = Ui.statRow(this);
        r1.addView(Ui.statCell(this, hm(settings.getHospitalMin()), "At work", Ui.TEXT));
        r1.addView(Ui.statCell(this, hm(settings.getHomeMin()), "At home", Ui.TEXT));
        glance.addView(r1);
        LinearLayout r2 = Ui.statRow(this);
        int ytTotal = settings.getYoutubeMin() + settings.getWorkYoutubeMin();
        r2.addView(Ui.statCell(this, hm(ytTotal), "YouTube (all)", Ui.WARN));
        r2.addView(Ui.statCell(this, hm(settings.getSocialMin()), "Insta+FB", Ui.WARN));
        glance.addView(r2);
        LinearLayout r3 = Ui.statRow(this);
        r3.addView(Ui.statCell(this, hm(settings.getScreenMin()), "Screen on", Ui.TEXT));
        r3.addView(Ui.statCell(this, hm(settings.getWorkDistractMin()), "Work distractions", Ui.WARN));
        glance.addView(r3);
        root.addView(glance);

        // --- YouTube where? ---
        LinearLayout yt = Ui.card(this);
        yt.addView(Ui.eyebrow(this, "Where you watch YouTube"));
        int yw = settings.getYtWork() + settings.getWorkYoutubeMin();
        int yh = settings.getYtHome();
        int ya = settings.getYtAway();
        yt.addView(Ui.body(this, "Work " + hm(yw) + "  ·  Home " + hm(yh)
                + "  ·  Out " + hm(ya)));
        yt.addView(barProportion(yw, yh, ya));
        root.addView(yt);

        // --- Time together (with wife) ---
        LinearLayout tog = Ui.card(this);
        tog.addView(Ui.eyebrow(this, "Time together"));
        int together = settings.getTogetherMin();
        int sleepMin = Math.max(0, settings.getSleepMin());
        int togetherAwake = Math.max(0, together - sleepMin);
        LinearLayout togRow = Ui.statRow(this);
        togRow.addView(Ui.statCell(this, hm(together), "Together today", Ui.GOOD));
        togRow.addView(Ui.statCell(this, hm(togetherAwake), "Together awake", Ui.ACCENT));
        tog.addView(togRow);
        TextView togHint = Ui.label(this, together == 0
                ? "Fills in after the backend calculates the day. Needs location sharing set up (Settings → Step 8)."
                : "“Awake” subtracts your sleep, since you sleep similar hours.");
        togHint.setPadding(Ui.dp(this,4), Ui.dp(this,6), Ui.dp(this,4), 0);
        tog.addView(togHint);
        root.addView(tog);

        // --- HERO: departure-time trend ---
        LinearLayout depCard = Ui.card(this);
        depCard.addView(Ui.eyebrow(this, "Are you leaving work earlier?"));
        TreeMap<String, Integer> depHist = settings.getDepartureHistory();
        List<Trends.Week> depWeeks = Trends.weeklyTimeOfDayAverages(depHist);
        List<String> labels = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        for (Trends.Week w : depWeeks) { labels.add(w.mondayDate); vals.add(w.avgMinutes); }
        LineChartView depChart = new LineChartView(this);
        depChart.setData("Avg departure by work-week", Ui.ACCENT, labels, vals, true);
        depChart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 220)));
        depCard.addView(depChart);
        depCard.addView(trendSentence(depWeeks));
        root.addView(depCard);

        // --- Departure by weekday (bars) ---
        LinearLayout dow = Ui.card(this);
        dow.addView(Ui.eyebrow(this, "Typical departure by weekday"));
        double[] byDay = Trends.averageByWeekday(depHist);
        List<String> dl = new ArrayList<>();
        List<Double> dv = new ArrayList<>();
        String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri"};
        for (int i = 0; i < 5; i++) { dl.add(names[i]); dv.add(byDay[i]); }
        BarChartView bars = new BarChartView(this);
        bars.setData("", Ui.ACCENT, true, dl, dv);
        bars.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 200)));
        dow.addView(bars);
        root.addView(dow);

        // --- New dashboard panels (order: week totals, scales, time-since) ---
        addThisWeekPanel(root);
        addScalePanel(root);
        addTimeSincePanel(root);

        // --- Navigation buttons ---
        root.addView(navButton("📈  All trends & charts", () ->
                startActivity(new Intent(this, TrendsActivity.class))));
        root.addView(navButton("⚙️  Settings & connections", () ->
                startActivity(new Intent(this, MainActivity.class))));

        return scroll;
    }

    // Proportional 3-segment bar for work/home/away.
    private View barProportion(int work, int home, int away) {
        int total = Math.max(1, work + home + away);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setWeightSum(1f);
        int h = Ui.dp(this, 12);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h);
        outer.setMargins(0, Ui.dp(this, 10), 0, 0);
        bar.setLayoutParams(outer);
        addSeg(bar, work, total, Ui.WARN);
        addSeg(bar, home, total, Ui.ACCENT);
        addSeg(bar, away, total, Ui.MUTED);
        return bar;
    }

    private void addSeg(LinearLayout bar, int v, int total, int color) {
        if (v <= 0) return;
        View seg = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        seg.setBackground(g);
        seg.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, (float) v / total));
        bar.addView(seg);
    }

    private TextView trendSentence(List<Trends.Week> weeks) {
        TextView t = Ui.label(this, Trends.describeTrend(weeks, "Departure time"));
        t.setPadding(Ui.dp(this,4), Ui.dp(this,8), Ui.dp(this,4), 0);
        return t;
    }

    /** Sum of a metric's daily-history values for the current week (Mon-based),
     *  plus an optional live today value not yet in history. */
    private int weekSum(Settings s, String metric, int todayLive) {
        java.util.TreeMap<String, Integer> hist = s.getHistory(metric);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        // Find this week's Monday.
        int dow = cal.get(java.util.Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
        int back = (dow == java.util.Calendar.SUNDAY) ? 6 : dow - java.util.Calendar.MONDAY;
        java.util.Calendar monday = (java.util.Calendar) cal.clone();
        monday.add(java.util.Calendar.DAY_OF_MONTH, -back);
        String today = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH));
        int sum = 0;
        for (java.util.Map.Entry<String, Integer> e : hist.entrySet()) {
            String d = e.getKey();
            // include dates >= monday and < today (today comes from live value)
            if (d.compareTo(mondayStr(monday)) >= 0 && d.compareTo(today) < 0) {
                sum += e.getValue();
            }
        }
        return sum + todayLive;
    }

    private String mondayStr(java.util.Calendar monday) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                monday.get(java.util.Calendar.YEAR), monday.get(java.util.Calendar.MONTH) + 1,
                monday.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private String fmtCal(java.util.Calendar c) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private String todayKey() {
        return fmtCal(java.util.Calendar.getInstance());
    }

    private String dateNDaysAgo(int n) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.DAY_OF_MONTH, -n);
        return fmtCal(c);
    }

    /** Date keys Monday..Sunday for the current week (ISO-style, Monday first). */
    private java.util.List<String> thisWeekDateKeys() {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.Calendar c = java.util.Calendar.getInstance();
        // Move back to Monday of this week.
        int dow = c.get(java.util.Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
        int back = (dow == java.util.Calendar.SUNDAY) ? 6 : dow - java.util.Calendar.MONDAY;
        c.add(java.util.Calendar.DAY_OF_MONTH, -back);
        for (int i = 0; i < 7; i++) {
            out.add(fmtCal(c));
            c.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    /** "Got home" card: yesterday's arrival time (reference) + this week's list.
     *  All SSID-derived (no GPS). Shows "—" for days with no captured arrival. */
    private View buildHomeByCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.eyebrow(this, "Got home from work"));

        java.util.TreeMap<String, Integer> hist = settings.getHomeArrivalHistory();

        // Yesterday's arrival as the headline reference.
        String yKey = dateNDaysAgo(1);
        Integer yMin = hist.get(yKey);
        // If today's already captured (rare to look mid-evening), show it too.
        int todayMin = settings.getHomeArrivalToday();

        LinearLayout row = Ui.statRow(this);
        row.addView(Ui.statCell(this,
                yMin != null ? Trends.minToClock(yMin) : "—",
                "Yesterday", Ui.GOOD));
        row.addView(Ui.statCell(this,
                todayMin >= 0 ? Trends.minToClock(todayMin) : "—",
                "Today", Ui.ACCENT));
        card.addView(row);

        // This week (Mon–today): one compact line per day with a time or dash.
        TextView wk = Ui.label(this, "This week");
        wk.setPadding(Ui.dp(this,4), Ui.dp(this,10), 0, Ui.dp(this,2));
        card.addView(wk);

        String[] dayNames = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
        java.util.List<String> weekKeys = thisWeekDateKeys(); // Mon..Sun
        boolean any = false;
        for (int i = 0; i < weekKeys.size(); i++) {
            String k = weekKeys.get(i);
            Integer m = hist.get(k);
            if (m == null && k.equals(todayKey()) && todayMin >= 0) m = todayMin;
            if (m != null) any = true;
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setPadding(Ui.dp(this,4), Ui.dp(this,2), Ui.dp(this,4), Ui.dp(this,2));
            TextView dn = new TextView(this);
            dn.setText(dayNames[i]);
            dn.setTextColor(Ui.MUTED);
            dn.setTextSize(13);
            dn.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,44),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView tv = new TextView(this);
            tv.setText(m != null ? Trends.minToClock(m) : "—");
            tv.setTextColor(m != null ? Ui.TEXT : Ui.MUTED);
            tv.setTextSize(13);
            line.addView(dn);
            line.addView(tv);
            card.addView(line);
        }

        TextView note = Ui.label(this, any
                ? "Detected when your phone joins home WiFi after leaving work."
                : "Fills in once you head home from work on a tracked day.");
        note.setTextSize(11);
        note.setPadding(Ui.dp(this,4), Ui.dp(this,6), Ui.dp(this,4), 0);
        card.addView(note);
        return card;
    }

    /** "This Week So Far" — weekly totals for key time metrics + each timer. */
    private void addThisWeekPanel(LinearLayout parent) {
        Settings s = settings;
        LinearLayout card = Ui.card(this);
        card.addView(Ui.eyebrow(this, "This week so far"));
        card.addView(weekRow("Time at work", weekSum(s, "hospital", s.getHospitalMin())));
        card.addView(weekRow("Social media", weekSum(s, "social", s.getSocialMin())));
        card.addView(weekRow("Work distractions", weekSum(s, "work_distract", s.getWorkDistractMin())));
        // Each timer (duration trackers) — sum this week from its history.
        Stopwatches store = new Stopwatches(this);
        for (Stopwatches.SW sw : store.getAll()) {
            if (sw.isScale() || sw.isCounter()) continue; // timers only here
            int wk = swWeekSum(store, sw.id);
            if (wk > 0) card.addView(weekRow(sw.name, wk));
        }
        parent.addView(card);
    }

    private int swWeekSum(Stopwatches store, String id) {
        java.util.TreeMap<String, Integer> hist = store.getHistory(id);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int dow = cal.get(java.util.Calendar.DAY_OF_WEEK);
        int back = (dow == java.util.Calendar.SUNDAY) ? 6 : dow - java.util.Calendar.MONDAY;
        java.util.Calendar monday = (java.util.Calendar) cal.clone();
        monday.add(java.util.Calendar.DAY_OF_MONTH, -back);
        int sum = 0;
        for (java.util.Map.Entry<String, Integer> e : hist.entrySet()) {
            if (e.getKey().compareTo(mondayStr(monday)) >= 0) sum += e.getValue();
        }
        return sum;
    }

    private View weekRow(String label, int minutes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Ui.TEXT);
        l.setTextSize(14);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);
        TextView v = new TextView(this);
        v.setText(hm(minutes));
        v.setTextColor(Ui.ACCENT);
        v.setTextSize(14);
        row.addView(v);
        return row;
    }

    /** "Scales" — mini line graph of scale trackers over last 14 days. */
    private void addScalePanel(LinearLayout parent) {
        Stopwatches store = new Stopwatches(this);
        java.util.List<Stopwatches.SW> scales = new java.util.ArrayList<>();
        for (Stopwatches.SW sw : store.getAll()) if (sw.isScale()) scales.add(sw);
        if (scales.isEmpty()) return;

        LinearLayout card = Ui.card(this);
        card.addView(Ui.eyebrow(this, "Scales (last 14 days)"));
        for (Stopwatches.SW sw : scales) {
            TextView nm = new TextView(this);
            int today = store.getDailyTotal(sw.id, Stopwatches.todayStr());
            nm.setText(sw.name + (today >= 1 ? "  —  today " + today + "/9" : "  —  not set today"));
            nm.setTextColor(Ui.TEXT);
            nm.setTextSize(14);
            nm.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 2));
            card.addView(nm);
            // Simple sparkline of last 14 days.
            java.util.TreeMap<String, Integer> hist = store.getHistory(sw.id);
            java.util.List<Double> vals = new java.util.ArrayList<>();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            for (int i = 13; i >= 0; i--) {
                java.util.Calendar c = (java.util.Calendar) cal.clone();
                c.add(java.util.Calendar.DAY_OF_MONTH, -i);
                String d = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                        c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
                        c.get(java.util.Calendar.DAY_OF_MONTH));
                Integer val = hist.get(d);
                vals.add(val == null ? 0.0 : (double) val);
            }
            LineChartView chart = new LineChartView(this);
            int color = Ui.ACCENT;
            try { color = Color.parseColor(sw.color); } catch (Exception ignored) {}
            java.util.List<String> labels = new java.util.ArrayList<>();
            for (int i = 0; i < vals.size(); i++) labels.add("");
            chart.setData(sw.name, color, labels, vals);
            chart.setScaleMode(true);
            chart.setReferenceLine(5, Ui.MUTED); // neutral midpoint
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 120));
            chart.setLayoutParams(lp);
            card.addView(chart);
        }
        parent.addView(card);
    }

    /** "Time Since" — for each counter, days since last logged + count this month. */
    private void addTimeSincePanel(LinearLayout parent) {
        Stopwatches store = new Stopwatches(this);
        java.util.List<Stopwatches.SW> counters = new java.util.ArrayList<>();
        for (Stopwatches.SW sw : store.getAll()) if (sw.isCounter()) counters.add(sw);
        if (counters.isEmpty()) return;

        LinearLayout card = Ui.card(this);
        card.addView(Ui.eyebrow(this, "Time since"));
        for (Stopwatches.SW sw : counters) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            TextView l = new TextView(this);
            l.setText(sw.name);
            l.setTextColor(Ui.TEXT);
            l.setTextSize(14);
            l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(l);
            TextView v = new TextView(this);
            int days = store.daysSinceLastLog(sw.id);
            int month = store.countInLastDays(sw.id, 30);
            String daysTxt = days < 0 ? "never" : (days == 0 ? "today" : days + "d ago");
            v.setText(daysTxt + "  ·  " + month + "/30d");
            v.setTextColor(Ui.MUTED);
            v.setTextSize(13);
            row.addView(v);
            card.addView(row);
        }
        parent.addView(card);
    }

    /** Show pinned counters/timers as tappable items, acting directly on tap. */
    private void addPinnedTrackers(LinearLayout parent) {
        Stopwatches store = new Stopwatches(this);
        java.util.List<Stopwatches.SW> pinned = store.getPinned();
        if (pinned.isEmpty()) return;

        LinearLayout card = Ui.card(this);
        card.addView(Ui.eyebrow(this, "Quick counters"));

        for (Stopwatches.SW s : pinned) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));

            // round action button
            int size = Ui.dp(this, 52);
            TextView circle = new TextView(this);
            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            int col;
            try { col = android.graphics.Color.parseColor(s.color); } catch (Exception e) { col = Ui.ACCENT; }
            g.setColor(col);
            if (s.isRunning()) g.setStroke(Ui.dp(this, 3), android.graphics.Color.WHITE);
            circle.setBackground(g);
            circle.setGravity(android.view.Gravity.CENTER);
            circle.setTextColor(android.graphics.Color.WHITE);
            circle.setTextSize(16);
            circle.setText(s.isCounter() ? "+1" : (s.isRunning() ? "■" : "▶"));
            android.widget.LinearLayout.LayoutParams clp =
                    new android.widget.LinearLayout.LayoutParams(size, size);
            clp.setMargins(0, 0, Ui.dp(this, 12), 0);
            circle.setLayoutParams(clp);
            row.addView(circle);

            LinearLayout tc = new LinearLayout(this);
            tc.setOrientation(android.widget.LinearLayout.VERTICAL);
            tc.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView nm = new TextView(this);
            nm.setText(s.name);
            nm.setTextColor(Ui.TEXT);
            nm.setTextSize(16);
            tc.addView(nm);
            TextView val = new TextView(this);
            int today = store.getDailyTotal(s.id, Stopwatches.todayStr());
            if (s.isCounter()) val.setText("Today: " + today);
            else if (s.isRunning()) val.setText("Running… " + hm((int) (s.currentSessionSeconds() / 60)));
            else val.setText("Today: " + hm(today));
            val.setTextColor(s.isRunning() ? Ui.ACCENT : Ui.MUTED);
            val.setTextSize(13);
            tc.addView(val);
            row.addView(tc);

            circle.setOnClickListener(v -> {
                if (s.isCounter()) {
                    store.increment(s.id);
                    Toast.makeText(this, "+1 " + s.name, Toast.LENGTH_SHORT).show();
                } else if (s.isRunning()) {
                    store.saveSession(s.id);
                    Toast.makeText(this, "Saved " + s.name, Toast.LENGTH_SHORT).show();
                } else {
                    store.start(s.id);
                    Toast.makeText(this, "Started " + s.name, Toast.LENGTH_SHORT).show();
                }
                recreate(); // refresh dashboard to show new state
            });

            card.addView(row);
        }

        TextView mng = Ui.label(this, "Manage these in the Counters tab.");
        mng.setTextSize(11);
        mng.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(mng);
        parent.addView(card);
    }

    private View navButton(String text, Runnable action) {        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Ui.TEXT);
        b.setTextSize(16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.CARD_ALT);
        bg.setCornerRadius(Ui.dp(this, 14));
        b.setBackground(bg);
        int p = Ui.dp(this, 16);
        b.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private String hm(int minutes) {
        if (minutes <= 0) return "0m";
        if (minutes < 60) return minutes + "m";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
