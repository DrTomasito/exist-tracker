package com.existtracker.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows weekly (Mon–Fri) average trends for the key metrics, with a
 * short plain-language inference under each chart.
 */
public class TrendsActivity extends AppCompatActivity {

    private Settings settings;
    private int rangeDays = 30;               // master-graph window; default ~1 month
    private LinearLayout masterContainer;     // holds the two overlay graphs

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        settings = new Settings(this);
        setContentView(build());
    }

    private View build() {
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView h = new TextView(this);
        h.setText("Weekly Trends");
        h.setTextSize(24);
        h.setTextColor(Ui.TEXT);
        h.setPadding(0, 0, 0, dp(4));
        root.addView(h);

        TextView sub = new TextView(this);
        sub.setText("Each point is one work week's average (Monday–Friday). "
                + "Charts fill in as the app posts each night.");
        sub.setTextSize(13);
        sub.setTextColor(Ui.MUTED);
        sub.setPadding(0, 0, 0, dp(12));
        root.addView(sub);
        root.addView(Ui.navRow(this, "trends"));

        // --- Master overlay graphs (range-selectable) ---
        root.addView(buildRangeSelector());
        masterContainer = new LinearLayout(this);
        masterContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(masterContainer);
        populateMasterGraphs();

        addMetric(root, "Work (Hospital) — weekly avg/day", "hospital",
                Color.parseColor("#1565C0"));
        addMetric(root, "YouTube — weekly avg/day", "youtube",
                Color.parseColor("#C62828"));
        addMetric(root, "Instagram + Facebook — weekly avg/day", "social",
                Color.parseColor("#6A1B9A"));

        // --- Today's location breakdown for app usage ---
        TextView bd = new TextView(this);
        bd.setText("Today's breakdown");
        bd.setTextSize(18);
        bd.setTextColor(Ui.ACCENT);
        bd.setPadding(0, dp(18), 0, dp(6));
        root.addView(bd);

        root.addView(breakdownLine("YouTube",
                settings.getYtWork(), settings.getYtHome(), settings.getYtAway()));
        root.addView(breakdownLine("Instagram + Facebook",
                settings.getSocWork(), settings.getSocHome(), settings.getSocAway()));

        int home = settings.getHomeMin();
        int sleep = Math.max(0, settings.getSleepMin());
        int awake = Math.max(0, home - sleep);
        TextView homeLine = new TextView(this);
        if (settings.getSleepMin() < 0 || sleep == 0) {
            homeLine.setText("Home: " + fmt(home) + " total. (Connect Health Connect "
                    + "sleep to split awake vs asleep.)");
        } else {
            homeLine.setText("Home: " + fmt(home) + " total → " + fmt(awake)
                    + " awake, " + fmt(sleep) + " asleep.");
        }
        homeLine.setTextSize(13);
        homeLine.setTextColor(Ui.MUTED);
        homeLine.setPadding(dp(4), dp(2), dp(4), dp(6));
        root.addView(homeLine);

        TextView screenLine = new TextView(this);
        screenLine.setText("Phone screen in use today: " + fmt(settings.getScreenMin()) + ".");
        screenLine.setTextSize(13);
        screenLine.setTextColor(Ui.MUTED);
        screenLine.setPadding(dp(4), 0, dp(4), dp(10));
        root.addView(screenLine);

        // --- Trend charts for the new metrics ---
        addMetric(root, "Phone screen time — weekly avg/day", "screen",
                Color.parseColor("#00838F"));
        addMetric(root, "Screen time at home — weekly avg/day", "screen_home",
                Color.parseColor("#5BD1A0"));
        addMetric(root, "Home (awake) — weekly avg/day", "home_awake",
                Color.parseColor("#2E7D32"));
        addMetric(root, "YouTube at Work — weekly avg/day", "yt_work",
                Color.parseColor("#EF6C00"));
        addMetric(root, "YouTube at Home — weekly avg/day", "yt_home",
                Color.parseColor("#AD1457"));
        addMetric(root, "Social at Work — weekly avg/day", "soc_work",
                Color.parseColor("#4527A0"));
        addMetric(root, "Social at Home — weekly avg/day", "soc_home",
                Color.parseColor("#00695C"));
        addMetric(root, "Time together — weekly avg/day", "together",
                Color.parseColor("#5BD1A0"));
        addMetric(root, "Time together awake — weekly avg/day", "together_awake",
                Color.parseColor("#F2B441"));

        // Church attendance — days per month (count of days with any church time).
        addChurchPanel(root);

        // Inference summaries — counts over the last 30 / 90 days.
        addInferencePanel(root);

        // Stopwatch trends (one chart per stopwatch that has history).
        Stopwatches sw = new Stopwatches(this);
        for (Stopwatches.SW s : sw.getAll()) {
            java.util.TreeMap<String, Integer> hist = sw.getHistory(s.id);
            if (hist.isEmpty()) continue;
            // Counters: weekly totals (times per week). Timers: weekly avg/day.
            // Scales shown on dashboard, skip here.
            boolean counter = s.isCounter();
            if (s.isScale()) continue;
            java.util.List<Trends.Week> weeks = counter
                    ? Trends.weeklyTotals(hist) : Trends.weeklyWorkdayAverages(hist);
            java.util.List<String> labels = new java.util.ArrayList<>();
            java.util.List<Double> values = new java.util.ArrayList<>();
            for (Trends.Week w : weeks) { labels.add(w.mondayDate); values.add(w.avgMinutes); }
            int color = Color.parseColor("#5BD1A0");
            try { color = Color.parseColor(s.color); } catch (Exception ignored) {}
            LineChartView chart = new LineChartView(this);
            chart.setData(s.name + (counter ? " — per week" : " — weekly avg/day"),
                    color, labels, values);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
            lp.setMargins(0, dp(10), 0, dp(2));
            chart.setLayoutParams(lp);
            root.addView(chart);
            TextView ins = new TextView(this);
            ins.setText(Trends.describeTrend(weeks, s.name));
            ins.setTextSize(13);
            ins.setTextColor(Ui.MUTED);
            ins.setPadding(dp(4), 0, dp(4), dp(16));
            root.addView(ins);
        }

        Button back = new Button(this);
        back.setText("Back");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        return scroll;
    }

    /** Row of range buttons (1mo / 3mo / 6mo / 1yr / All) that redraw the
     *  master overlay graphs without rebuilding the whole screen. */
    private View buildRangeSelector() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        int[] days = {30, 90, 180, 365, 3650};
        String[] names = {"1mo", "3mo", "6mo", "1yr", "All"};
        for (int i = 0; i < days.length; i++) {
            final int dval = days[i];
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setTextColor(rangeDays == dval ? Ui.BG : Ui.TEXT);
            b.setBackgroundColor(rangeDays == dval ? Ui.ACCENT : Ui.CARD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                rangeDays = dval;
                // Refresh the selector (to recolor the active button) + graphs.
                LinearLayout parent = (LinearLayout) ((View) v.getParent());
                refreshRangeButtons(parent);
                populateMasterGraphs();
            });
            row.addView(b);
        }
        return row;
    }

    private void refreshRangeButtons(LinearLayout row) {
        int[] days = {30, 90, 180, 365, 3650};
        for (int i = 0; i < row.getChildCount() && i < days.length; i++) {
            Button b = (Button) row.getChildAt(i);
            boolean active = rangeDays == days[i];
            b.setTextColor(active ? Ui.BG : Ui.TEXT);
            b.setBackgroundColor(active ? Ui.ACCENT : Ui.CARD);
        }
    }

    /** Build the two overlay graphs into masterContainer for the current range. */
    private void populateMasterGraphs() {
        masterContainer.removeAllViews();

        // Graph 1 — "big time" metrics: work + home + time together.
        MultiLineChartView g1 = new MultiLineChartView(this);
        g1.setTitle("Big-picture time — " + rangeLabel());
        g1.setXLabels(addSeriesTo(g1, new String[]{"hospital", "home", "together"},
                new String[]{"Work", "Home", "Together"},
                new int[]{Ui.ACCENT, Color.parseColor("#5B9BD1"), Ui.GOOD}));
        g1.setLayoutParams(chartParams());
        masterContainer.addView(g1);

        // Graph 2 — "small time" metrics: social + youtube + screen + distractions.
        MultiLineChartView g2 = new MultiLineChartView(this);
        g2.setTitle("Distractions — " + rangeLabel());
        g2.setXLabels(addSeriesTo(g2,
                new String[]{"social", "youtube", "screen", "screen_home", "work_distract"},
                new String[]{"Social", "YouTube", "Screen", "Screen@Home", "Work distract"},
                new int[]{Ui.WARN, Color.parseColor("#F2B441"), Ui.MUTED,
                        Color.parseColor("#5BD1A0"), Color.parseColor("#E86A6A")}));
        g2.setLayoutParams(chartParams());
        masterContainer.addView(g2);
    }

    /** Add the given metrics as series to a chart; returns the shared x-labels. */
    private java.util.List<String> addSeriesTo(MultiLineChartView chart,
            String[] metrics, String[] names, int[] colors) {
        chart.clearSeries();
        java.util.List<String> labels = null;
        for (int i = 0; i < metrics.length; i++) {
            Trends.RangeSeries rs = Trends.seriesOverRange(
                    settings.getHistory(metrics[i]), rangeDays);
            if (labels == null) labels = rs.labels; // all share the same window
            chart.addSeries(new MultiLineChartView.Series(names[i], colors[i], rs.values));
        }
        return labels != null ? labels : new java.util.ArrayList<>();
    }

    private LinearLayout.LayoutParams chartParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(10));
        return lp;
    }

    private String rangeLabel() {
        switch (rangeDays) {
            case 30: return "last month";
            case 90: return "last 3 months";
            case 180: return "last 6 months";
            case 365: return "last year";
            default: return "all time";
        }
    }

    /** Church attendance: count of days per month with any church time logged.
     *  (We don't show time-at-church anywhere; just how many days/month.) */
    private void addChurchPanel(LinearLayout root) {
        java.util.TreeMap<String, Integer> hist = settings.getHistory("church");
        // Bucket by YYYY-MM, counting days with >0 minutes.
        java.util.TreeMap<String, Integer> perMonth = new java.util.TreeMap<>();
        for (java.util.Map.Entry<String, Integer> e : hist.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            String ym = e.getKey().length() >= 7 ? e.getKey().substring(0, 7) : e.getKey();
            perMonth.put(ym, perMonth.getOrDefault(ym, 0) + 1);
        }
        if (perMonth.isEmpty()) return; // nothing yet → hide panel

        LinearLayout card = Ui.card(this);
        card.addView(Ui.eyebrow(this, "Church — days per month"));
        // Show most recent months last; simple labeled rows.
        for (java.util.Map.Entry<String, Integer> e : perMonth.entrySet()) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setPadding(dp(4), dp(2), dp(4), dp(2));
            TextView m = new TextView(this);
            m.setText(monthLabel(e.getKey()));
            m.setTextColor(Ui.MUTED); m.setTextSize(13);
            m.setLayoutParams(new LinearLayout.LayoutParams(dp(90),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView v = new TextView(this);
            v.setText(e.getValue() + (e.getValue() == 1 ? " day" : " days"));
            v.setTextColor(Ui.TEXT); v.setTextSize(13);
            line.addView(m); line.addView(v);
            card.addView(line);
        }
        root.addView(card);
    }

    /** "2026-06" -> "Jun 2026". */
    private String monthLabel(String ym) {
        String[] names = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        try {
            String[] p = ym.split("-");
            int mi = Integer.parseInt(p[1]) - 1;
            if (mi >= 0 && mi < 12) return names[mi] + " " + p[0];
        } catch (Exception ignored) {}
        return ym;
    }

    /** Summary of the hardwired inferences as counts over recent windows.
     *  Most useful: how many weekdays home for dinner. */
    private void addInferencePanel(LinearLayout root) {
        // (key, label) pairs we want to show as "X of Y days" tallies.
        String[][] items = {
                {"home_for_dinner", "Home for dinner (by 6:05pm, weekdays)"},
                {"rounds", "Made rounds (weekdays)"},
                {"choir", "Choir day (Sundays)"},
                {"late_church", "Late to church (Sundays)"},
                {"weekend_out", "Out & about (weekends)"},
        };
        // Build only if there's at least one inference recorded.
        boolean any = false;
        for (String[] it : items)
            if (!settings.getInferenceHistory(it[0]).isEmpty()) { any = true; break; }
        if (!any) return;

        LinearLayout card = Ui.card(this);
        card.addView(Ui.eyebrow(this, "Patterns — last 30 days"));
        for (String[] it : items) {
            java.util.TreeMap<String, Integer> hist = settings.getInferenceHistory(it[0]);
            if (hist.isEmpty()) continue;
            String cutoff = dateNDaysAgoStr(30);
            int trueCount = 0, total = 0;
            for (java.util.Map.Entry<String, Integer> e : hist.entrySet()) {
                if (e.getKey().compareTo(cutoff) < 0) continue;
                total++;
                if (e.getValue() != null && e.getValue() == 1) trueCount++;
            }
            if (total == 0) continue;
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setPadding(dp(4), dp(3), dp(4), dp(3));
            TextView lbl = new TextView(this);
            lbl.setText(it[1]);
            lbl.setTextColor(Ui.MUTED); lbl.setTextSize(13);
            lbl.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView val = new TextView(this);
            val.setText(trueCount + " / " + total);
            val.setTextColor(Ui.TEXT); val.setTextSize(13);
            line.addView(lbl); line.addView(val);
            card.addView(line);
        }
        root.addView(card);
    }

    private String dateNDaysAgoStr(int n) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.DAY_OF_MONTH, -n);
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private void addMetric(LinearLayout root, String title, String metric, int color) {
        java.util.TreeMap<String, Integer> hist = settings.getHistory(metric);
        List<Trends.Week> weeks = Trends.weeklyWorkdayAverages(hist);

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (Trends.Week w : weeks) {
            labels.add(w.mondayDate);
            values.add(w.avgMinutes);
        }

        LineChartView chart = new LineChartView(this);
        chart.setData(title, color, labels, values);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
        lp.setMargins(0, dp(10), 0, dp(2));
        chart.setLayoutParams(lp);
        root.addView(chart);

        TextView insight = new TextView(this);
        String label = title.split(" —")[0];
        insight.setText(Trends.describeTrend(weeks, label));
        insight.setTextSize(13);
        insight.setTextColor(Ui.MUTED);
        insight.setPadding(dp(4), 0, dp(4), dp(16));
        root.addView(insight);
    }

    private View breakdownLine(String name, int work, int home, int away) {
        int total = work + home + away;
        TextView t = new TextView(this);
        String pctW = total > 0 ? Math.round(100.0 * work / total) + "%" : "0%";
        String pctH = total > 0 ? Math.round(100.0 * home / total) + "%" : "0%";
        String pctA = total > 0 ? Math.round(100.0 * away / total) + "%" : "0%";
        t.setText(name + ": " + fmt(total) + " today  ·  Work " + fmt(work) + " (" + pctW
                + "), Home " + fmt(home) + " (" + pctH + "), Away " + fmt(away) + " (" + pctA + ")");
        t.setTextSize(13);
        t.setTextColor(Ui.MUTED);
        t.setPadding(dp(4), dp(2), dp(4), dp(2));
        return t;
    }

    private String fmt(int minutes) {
        if (minutes < 60) return minutes + "m";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
