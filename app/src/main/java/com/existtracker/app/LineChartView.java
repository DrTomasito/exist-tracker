package com.existtracker.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A small, self-contained line chart. No external libraries.
 * Feed it labels (week starts) and values (avg minutes); it draws a
 * smooth-ish line with axis labels and dots, scaled nicely.
 */
public class LineChartView extends View {

    private List<String> labels = new ArrayList<>();
    private List<Double> values = new ArrayList<>();
    private String title = "";
    private int lineColor = Color.parseColor("#1565C0");
    private boolean clockMode = false;
    private boolean scaleMode = false;       // 1-9 scale display
    private double refLineY = Double.NaN;     // optional horizontal reference line
    private int refLineColor = Color.parseColor("#90A4C0");

    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LineChartView(Context ctx) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;

        axis.setColor(Color.parseColor("#90A4C0"));   // muted blue-grey
        axis.setStrokeWidth(2 * d);

        grid.setColor(Color.parseColor("#2A3B55"));    // subtle dark gridline
        grid.setStrokeWidth(1 * d);

        line.setColor(lineColor);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3 * d);
        line.setStrokeJoin(Paint.Join.ROUND);

        dot.setColor(lineColor);
        dot.setStyle(Paint.Style.FILL);

        text.setColor(Color.parseColor("#90A4C0"));    // light muted labels
        text.setTextSize(11 * d);

        titlePaint.setColor(Color.parseColor("#E8EEF6")); // light title
        titlePaint.setTextSize(15 * d);
        titlePaint.setFakeBoldText(true);

        setMinimumHeight((int) (200 * d));
    }

    public void setData(String title, int color, List<String> labels, List<Double> values) {
        setData(title, color, labels, values, false);
    }

    public void setData(String title, int color, List<String> labels,
                        List<Double> values, boolean clockMode) {
        this.title = title;
        this.lineColor = color;
        this.line.setColor(color);
        this.dot.setColor(color);
        this.labels = labels;
        this.values = values;
        this.clockMode = clockMode;
        invalidate();
    }

    /** Display as a 1-9 scale (y-axis 1..9) instead of time. */
    public void setScaleMode(boolean on) {
        this.scaleMode = on;
        invalidate();
    }

    /** Draw a dashed horizontal reference line at the given y data-value. */
    public void setReferenceLine(double y, int color) {
        this.refLineY = y;
        this.refLineColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();

        float padL = 44 * d, padR = 12 * d, padT = 30 * d, padB = 34 * d;
        float plotW = w - padL - padR;
        float plotH = h - padT - padB;

        c.drawText(title, padL, 18 * d, titlePaint);

        if (values == null || values.size() < 1) {
            c.drawText("No data yet — trends appear after nightly posts.",
                    padL, padT + plotH / 2, text);
            return;
        }

        double maxV = 1, minV = Double.MAX_VALUE;
        for (double v : values) { maxV = Math.max(maxV, v); minV = Math.min(minV, v); }

        double base, niceMax;
        if (clockMode) {
            // Scale to roughly an hour below earliest and above latest.
            base = Math.max(0, Math.floor((minV - 60) / 60) * 60);
            niceMax = Math.ceil((maxV + 30) / 60) * 60;
        } else if (scaleMode) {
            base = 1; niceMax = 9; // fixed 1-9 scale
        } else {
            base = 0;
            niceMax = niceCeil(maxV);
        }
        double span = Math.max(1, niceMax - base);

        // horizontal gridlines + y labels
        int lines = 4;
        for (int i = 0; i <= lines; i++) {
            float y = padT + plotH * i / lines;
            c.drawLine(padL, y, padL + plotW, y, grid);
            double val = base + span * (lines - i) / lines;
            String lbl = clockMode ? Trends.minToClock(val)
                    : (scaleMode ? String.format(Locale.US, "%.0f", val)
                    : String.format(Locale.US, "%.1fh", val / 60.0));
            c.drawText(lbl, 4 * d, y + 4 * d, text);
        }

        // optional dashed reference line (e.g. neutral 5 on a scale)
        if (!Double.isNaN(refLineY) && refLineY >= base && refLineY <= niceMax) {
            Paint refPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            refPaint.setColor(refLineColor);
            refPaint.setStrokeWidth(1.5f * d);
            refPaint.setStyle(Paint.Style.STROKE);
            refPaint.setPathEffect(new android.graphics.DashPathEffect(
                    new float[]{6 * d, 5 * d}, 0));
            float ry = padT + (float) (plotH * (niceMax - refLineY) / span);
            c.drawLine(padL, ry, padL + plotW, ry, refPaint);
        }

        // axes
        c.drawLine(padL, padT, padL, padT + plotH, axis);
        c.drawLine(padL, padT + plotH, padL + plotW, padT + plotH, axis);

        int n = values.size();
        float stepX = n > 1 ? plotW / (n - 1) : 0;

        Path path = new Path();
        for (int i = 0; i < n; i++) {
            float x = padL + (n > 1 ? stepX * i : plotW / 2);
            float y = padT + plotH * (float) (1 - (values.get(i) - base) / span);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        c.drawPath(path, line);

        // dots + x labels (show first, last, and a few between)
        for (int i = 0; i < n; i++) {
            float x = padL + (n > 1 ? stepX * i : plotW / 2);
            float y = padT + plotH * (float) (1 - (values.get(i) - base) / span);
            c.drawCircle(x, y, 4 * d, dot);

            boolean showLabel = (i == 0 || i == n - 1
                    || (n > 6 && i % (n / 5 + 1) == 0));
            if (showLabel && i < labels.size()) {
                String lab = shortDate(labels.get(i));
                float tw = text.measureText(lab);
                c.drawText(lab, Math.min(x - tw / 2, w - tw - 2 * d),
                        padT + plotH + 20 * d, text);
            }
        }
    }

    private double niceCeil(double v) {
        if (v <= 60) return Math.ceil(v / 15.0) * 15;
        if (v <= 240) return Math.ceil(v / 30.0) * 30;
        return Math.ceil(v / 60.0) * 60;
    }

    private String shortDate(String iso) {
        // "2026-05-25" -> "5/25"
        try {
            String[] p = iso.split("-");
            return Integer.parseInt(p[1]) + "/" + Integer.parseInt(p[2]);
        } catch (Exception e) { return iso; }
    }
}
