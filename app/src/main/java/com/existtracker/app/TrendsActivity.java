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
