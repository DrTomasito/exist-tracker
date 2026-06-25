package com.existtracker.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

/**
 * The Counters tab. Holds two kinds of trackers:
 *   - Timers (stopwatches): tap to start/stop; logs elapsed minutes.
 *   - Counters (clickers): tap to +1; logs event counts.
 * Each can be reordered (up/down), pinned to the homepage, saved internally
 * only or also pushed to an Exist attribute, and edited/deleted.
 */
public class StopwatchActivity extends AppCompatActivity {

    private Stopwatches store;
    private LinearLayout root;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private Runnable tick;
    private final java.util.Map<String, TextView> liveLabels = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Stopwatches(this);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 28), p, p);
        scroll.addView(root);
        setContentView(scroll);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tick = new Runnable() {
            @Override public void run() {
                for (Stopwatches.SW s : store.getAll()) {
                    TextView tv = liveLabels.get(s.id);
                    if (tv != null && s.isRunning()) tv.setText(fmt(s.currentSessionSeconds()));
                }
                ticker.postDelayed(this, 1000);
            }
        };
        ticker.postDelayed(tick, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tick != null) ticker.removeCallbacks(tick);
    }

    private void rebuild() {
        root.removeAllViews();
        liveLabels.clear();

        root.addView(Ui.eyebrow(this, "Counters & timers"));
        root.addView(Ui.title(this, "Activity Counters"));
        TextView sub = Ui.label(this, "Timers measure how long you do something; "
                + "counters tally how many times. Pin favorites to your home screen.");
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        root.addView(sub);

        List<Stopwatches.SW> all = store.getAll();
        if (all.isEmpty()) {
            TextView empty = Ui.body(this, "Nothing yet. Add a timer (e.g. “Charting”, "
                    + "“Gaming”) or a counter (e.g. “Coffees”, “Pages read”) below.");
            empty.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            root.addView(empty);
        }

        for (int i = 0; i < all.size(); i++) {
            root.addView(trackerCard(all.get(i), i, all.size()));
        }

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        Button addTimer = new Button(this);
        addTimer.setText("＋ Timer");
        addTimer.setOnClickListener(v -> editDialog(null, "timer"));
        addTimer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addRow.addView(addTimer);
        Button addCounter = new Button(this);
        addCounter.setText("＋ Counter");
        addCounter.setOnClickListener(v -> editDialog(null, "counter"));
        addCounter.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addRow.addView(addCounter);
        root.addView(addRow);

        root.addView(spacer(Ui.dp(this, 8)));
        root.addView(Ui.eyebrow(this, "Recent sessions"));
        List<Stopwatches.LogEntry> log = store.getLog(20);
        if (log.isEmpty()) root.addView(Ui.label(this, "Saved sessions will appear here."));
        else for (Stopwatches.LogEntry e : log) root.addView(logRow(e));

        root.addView(spacer(Ui.dp(this, 10)));
        Button back = new Button(this);
        back.setText("Back to Dashboard");
        back.setOnClickListener(v -> finish());
        root.addView(back);
    }

    private View trackerCard(Stopwatches.SW s, int index, int total) {
        LinearLayout card = Ui.card(this);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int size = Ui.dp(this, 72);
        TextView circle = new TextView(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        int col = parseColor(s.color, Ui.ACCENT);
        g.setColor(s.isRunning() ? col : (s.isCounter() ? col : darken(col)));
        g.setStroke(Ui.dp(this, s.isRunning() ? 3 : 0), Color.WHITE);
        circle.setBackground(g);
        circle.setGravity(Gravity.CENTER);
        circle.setTextColor(Color.WHITE);
        if (s.isCounter()) { circle.setText("+1"); circle.setTextSize(20); }
        else { circle.setText(s.isRunning() ? "■" : "▶"); circle.setTextSize(22); }
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(size, size);
        clp.setMargins(0, 0, Ui.dp(this, 14), 0);
        circle.setLayoutParams(clp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(this);
        name.setText(s.name + (s.pinned ? "  📌" : ""));
        name.setTextColor(Ui.TEXT);
        name.setTextSize(18);
        textCol.addView(name);

        TextView live = new TextView(this);
        int todayTotal = store.getDailyTotal(s.id, Stopwatches.todayStr());
        if (s.isCounter()) {
            live.setText("Today: " + todayTotal + (s.pushToExist ? "" : "  · internal"));
            live.setTextColor(Ui.MUTED);
            live.setTextSize(15);
        } else if (s.isRunning()) {
            live.setText(fmt(s.currentSessionSeconds()));
            live.setTextColor(Ui.ACCENT);
            live.setTextSize(26);
            liveLabels.put(s.id, live);
        } else {
            live.setText("Today: " + fmt(todayTotal * 60L) + (s.pushToExist ? "" : "  · internal"));
            live.setTextColor(Ui.MUTED);
            live.setTextSize(15);
        }
        textCol.addView(live);

        LinearLayout arrows = new LinearLayout(this);
        arrows.setOrientation(LinearLayout.VERTICAL);
        arrows.addView(arrowBtn("▲", index > 0, () -> { store.move(s.id, -1); rebuild(); }));
        arrows.addView(arrowBtn("▼", index < total - 1, () -> { store.move(s.id, 1); rebuild(); }));

        row.addView(circle);
        row.addView(textCol);
        row.addView(arrows);
        card.addView(row);

        circle.setOnClickListener(v -> {
            if (s.isCounter()) {
                store.increment(s.id);
                toast("+1 " + s.name);
                rebuild();
            } else if (!s.isRunning()) {
                if (store.anyRunning()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Another timer is running")
                            .setMessage("You already have " + store.runningCount()
                                    + " running. Start “" + s.name + "” too?")
                            .setPositiveButton("Start anyway", (d, w) -> { store.start(s.id); rebuild(); })
                            .setNegativeButton("Cancel", null).show();
                } else { store.start(s.id); rebuild(); }
            }
        });

        if (!s.isCounter() && s.isRunning()) {
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setPadding(0, Ui.dp(this, 12), 0, 0);
            controls.addView(ctrlButton("Pause", () -> { store.pause(s.id); rebuild(); }));
            controls.addView(ctrlButton("Save", () -> { store.saveSession(s.id); toast("Saved."); rebuild(); }));
            controls.addView(ctrlButton("Cancel", () -> { store.cancelSession(s.id); rebuild(); }));
            card.addView(controls);
        } else if (!s.isCounter() && s.banked > 0) {
            TextView paused = Ui.label(this, "Paused at " + fmt(s.banked));
            paused.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(paused);
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setPadding(0, Ui.dp(this, 8), 0, 0);
            controls.addView(ctrlButton("Resume", () -> { store.start(s.id); rebuild(); }));
            controls.addView(ctrlButton("Save", () -> { store.saveSession(s.id); toast("Saved."); rebuild(); }));
            controls.addView(ctrlButton("Cancel", () -> { store.cancelSession(s.id); rebuild(); }));
            card.addView(controls);
        } else if (s.isCounter() && todayTotal > 0) {
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setPadding(0, Ui.dp(this, 8), 0, 0);
            controls.addView(ctrlButton("−1 (undo)", () -> { store.decrement(s.id); rebuild(); }));
            card.addView(controls);
        }

        name.setOnLongClickListener(v -> { editDialog(s, s.type); return true; });
        TextView editHint = Ui.label(this, "Long-press the name to edit, pin, or delete.");
        editHint.setTextSize(11);
        editHint.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(editHint);
        return card;
    }

    private View arrowBtn(String glyph, boolean enabled, Runnable action) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextSize(16);
        t.setTextColor(enabled ? Ui.TEXT : Color.parseColor("#33FFFFFF"));
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
        if (enabled) t.setOnClickListener(v -> action.run());
        return t;
    }

    private View ctrlButton(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private View logRow(Stopwatches.LogEntry e) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
        TextView t = new TextView(this);
        t.setText(e.name + " · " + e.date);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(14);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        TextView val = new TextView(this);
        val.setText(e.minutes + (e.minutes == 1 ? " (1)" : "m"));
        val.setTextColor(Ui.ACCENT);
        val.setTextSize(14);
        val.setPadding(0, 0, Ui.dp(this, 10), 0);
        row.addView(val);
        TextView del = new TextView(this);
        del.setText("✕");
        del.setTextColor(Ui.MUTED);
        del.setTextSize(16);
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Delete this entry and subtract it from that day's total?")
                .setPositiveButton("Delete", (d, w) -> { store.deleteLogEntry(e.ts); rebuild(); })
                .setNegativeButton("Keep", null).show());
        row.addView(del);
        return row;
    }

    private void editDialog(Stopwatches.SW existing, String type) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16); box.setPadding(p, p, p, p);

        TextView typeLabel = new TextView(this);
        typeLabel.setText("counter".equals(type) ? "Counter (tap = +1)" : "Timer (stopwatch)");
        typeLabel.setTextColor(Color.parseColor("#1565C0"));
        box.addView(typeLabel);

        EditText nameEt = new EditText(this);
        nameEt.setHint("counter".equals(type) ? "Name (e.g. Coffees)" : "Name (e.g. Charting)");
        nameEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (existing != null) nameEt.setText(existing.name);
        box.addView(nameEt);

        CheckBox pushCb = new CheckBox(this);
        pushCb.setText("Also push the daily total to Exist");
        pushCb.setChecked(existing == null || existing.pushToExist);
        box.addView(pushCb);

        EditText attrEt = new EditText(this);
        attrEt.setHint("Exist attribute (only if pushing)");
        if (existing != null) attrEt.setText(existing.attr);
        box.addView(attrEt);
        Button pick = new Button(this);
        pick.setText("Choose from Exist / create new…");
        pick.setOnClickListener(v -> new AttributePicker(this).choose(
                attrEt.getText().toString().trim(), "productivity",
                name -> attrEt.setText(name)));
        box.addView(pick);

        CheckBox pinCb = new CheckBox(this);
        pinCb.setText("Pin to home screen");
        pinCb.setChecked(existing != null && existing.pinned);
        box.addView(pinCb);

        final String[] colors = {"#1565C0", "#5BD1A0", "#F2B441", "#C62828", "#6A1B9A", "#00838F"};
        final String[] chosen = { existing != null ? existing.color : colors[0] };
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setPadding(0, Ui.dp(this, 10), 0, 0);
        for (String c : colors) {
            TextView sw = new TextView(this);
            GradientDrawable gg = new GradientDrawable();
            gg.setShape(GradientDrawable.OVAL);
            gg.setColor(parseColor(c, Ui.ACCENT));
            sw.setBackground(gg);
            int sz = Ui.dp(this, 34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            sw.setLayoutParams(lp);
            sw.setOnClickListener(v -> { chosen[0] = c; toast("Color set"); });
            swatches.addView(sw);
        }
        box.addView(swatches);

        AlertDialog.Builder bld = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New" : "Edit")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String nm = nameEt.getText().toString().trim();
                    String at = attrEt.getText().toString().trim();
                    boolean doPush = pushCb.isChecked();
                    boolean pin = pinCb.isChecked();
                    if (nm.isEmpty()) { toast("Enter a name."); return; }
                    if (doPush && at.isEmpty()) { toast("Pick an Exist attribute, or untick pushing."); return; }
                    if (existing == null) store.addTracker(nm, at, chosen[0], type, doPush, pin);
                    else store.updateTracker(existing.id, nm, at, chosen[0], doPush, pin);
                    rebuild();
                })
                .setNegativeButton("Cancel", null);
        if (existing != null) {
            bld.setNeutralButton("Delete", (d, w) ->
                    new AlertDialog.Builder(this)
                            .setMessage("Delete “" + existing.name + "”? Its Exist history stays.")
                            .setPositiveButton("Delete", (dd, ww) -> { store.deleteStopwatch(existing.id); rebuild(); })
                            .setNegativeButton("Keep", null).show());
        }
        bld.show();
    }

    private String fmt(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private int parseColor(String hex, int fallback) {
        try { return Color.parseColor(hex); } catch (Exception e) { return fallback; }
    }

    private int darken(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.55f;
        return Color.HSVToColor(hsv);
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        return v;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
