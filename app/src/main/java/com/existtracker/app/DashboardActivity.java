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

        // --- Navigation buttons ---
        root.addView(navButton("⏱️  Activity timers", () ->
                startActivity(new Intent(this, StopwatchActivity.class))));
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
