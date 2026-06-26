package com.existtracker.app;

import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small helpers to give the app a calm, consistent card-based look without
 * pulling in layout XML. Palette: deep teal-navy with a warm amber accent —
 * chosen to feel like a quiet dashboard rather than a clinical chart tool.
 */
public class Ui {

    public static final int BG       = Color.parseColor("#0F1B2D"); // deep navy
    public static final int CARD     = Color.parseColor("#17263D"); // card navy
    public static final int CARD_ALT = Color.parseColor("#1E3251");
    public static final int TEXT     = Color.parseColor("#E8EEF6");
    public static final int MUTED    = Color.parseColor("#90A4C0");
    public static final int ACCENT   = Color.parseColor("#F2B441"); // warm amber
    public static final int GOOD     = Color.parseColor("#5BD1A0");
    public static final int WARN     = Color.parseColor("#F2785C");

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    /** A rounded card container. */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(c, 16));
        l.setBackground(bg);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView title(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(22);
        t.setLetterSpacing(0.01f);
        return t;
    }

    public static TextView eyebrow(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s.toUpperCase());
        t.setTextColor(ACCENT);
        t.setTextSize(11);
        t.setLetterSpacing(0.18f);
        return t;
    }

    public static TextView bigStat(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(30);
        t.setPadding(0, dp(c, 2), 0, 0);
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(13);
        return t;
    }

    public static TextView body(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(15);
        return t;
    }

    /** A horizontal row of two stat cells (used for at-a-glance numbers). */
    public static LinearLayout statRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(2f);
        return row;
    }

    public static LinearLayout statCell(Context c, String value, String label, int valueColor) {
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_ALT);
        bg.setCornerRadius(dp(c, 12));
        cell.setBackground(bg);
        int p = dp(c, 12);
        cell.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        cell.setLayoutParams(lp);

        TextView v = new TextView(c);
        v.setText(value);
        v.setTextColor(valueColor);
        v.setTextSize(24);
        cell.addView(v);

        TextView l = new TextView(c);
        l.setText(label);
        l.setTextColor(MUTED);
        l.setTextSize(12);
        cell.addView(l);
        return cell;
    }

    /**
     * A consistent navigation row for the top of every screen. Shows four
     * sections; the current one is highlighted and inert, the others launch
     * their screen. `current` is one of: "dashboard", "counters", "trends",
     * "settings".
     */
    public static LinearLayout navRow(final Activity a, String current) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(a, 4), 0, dp(a, 12));

        row.addView(navTab(a, "Home", "dashboard", current, DashboardActivity.class));
        row.addView(navTab(a, "Counters", "counters", current, StopwatchActivity.class));
        row.addView(navTab(a, "Trends", "trends", current, TrendsActivity.class));
        row.addView(navTab(a, "Settings", "settings", current, MainActivity.class));
        return row;
    }

    private static TextView navTab(final Activity a, String label, String key,
                                   String current, final Class<?> target) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(13);
        t.setPadding(dp(a, 6), dp(a, 8), dp(a, 6), dp(a, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(a, 3), 0, dp(a, 3), 0);
        t.setLayoutParams(lp);

        boolean isCurrent = key.equals(current);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(a, 10));
        if (isCurrent) {
            bg.setColor(ACCENT);
            t.setTextColor(BG);
        } else {
            bg.setColor(CARD_ALT);
            t.setTextColor(TEXT);
        }
        t.setBackground(bg);

        if (!isCurrent) {
            t.setOnClickListener(v -> {
                Intent i = new Intent(a, target);
                // Reuse an existing instance if it's already in the stack,
                // so we don't pile up screens as the user taps around.
                i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                a.startActivity(i);
            });
        }
        return t;
    }
}
