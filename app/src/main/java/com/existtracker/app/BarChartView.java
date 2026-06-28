package com.existtracker.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.List;

/**
 * Simple vertical bar chart. Bars can be labelled, and values can be shown
 * either as minutes/hours or as clock times (for arrival/departure).
 */
public class BarChartView extends View {

    private List<String> labels;
    private List<Double> values; // minute-of-day or minutes
    private String title = "";
    private int barColor = Color.parseColor("#1565C0");
    private boolean clockMode = false; // true => values are minute-of-day

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context ctx) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;
        bar.setStyle(Paint.Style.FILL);
        text.setColor(Color.parseColor("#90A4C0"));
        text.setTextSize(11 * d);
        text.setTextAlign(Paint.Align.CENTER);
        valText.setColor(Color.parseColor("#E8EEF6"));
        valText.setTextSize(10 * d);
        valText.setTextAlign(Paint.Align.CENTER);
        titlePaint.setColor(Color.parseColor("#E8EEF6"));
        titlePaint.setTextSize(15 * d);
        titlePaint.setFakeBoldText(true);
        grid.setColor(Color.parseColor("#2A3B55"));
        grid.setStrokeWidth(1 * d);
        setMinimumHeight((int) (200 * d));
    }

    public void setData(String title, int color, boolean clockMode,
                        List<String> labels, List<Double> values) {
        this.title = title;
        this.barColor = color;
        this.bar.setColor(color);
        this.clockMode = clockMode;
        this.labels = labels;
        this.values = values;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float padL = 16 * d, padR = 16 * d, padT = 30 * d, padB = 28 * d;
        float plotW = w - padL - padR, plotH = h - padT - padB;

        c.drawText(title, padL, 18 * d, titlePaint);

        if (values == null || values.isEmpty()) {
            c.drawText("No data yet.", padL, padT + plotH / 2, text);
            return;
        }

        double maxV = 1, minV = clockMode ? Double.MAX_VALUE : 0;
        boolean any = false;
        for (double v : values) {
            if (v < 0) continue;
            any = true;
            maxV = Math.max(maxV, v);
            minV = Math.min(minV, v);
        }
        if (!any) {
            c.drawText("No data yet.", padL, padT + plotH / 2, text);
            return;
        }
        // For clock mode, give some headroom below the earliest time.
        double base = clockMode ? Math.max(0, minV - 60) : 0;
        double span = Math.max(1, maxV - base);

        int n = values.size();
        float slot = plotW / n;
        float barW = slot * 0.6f;

        for (int i = 0; i < n; i++) {
            double v = values.get(i);
            float cx = padL + slot * i + slot / 2;
            if (v >= 0) {
                float bh = (float) ((v - base) / span * plotH);
                float top = padT + plotH - bh;
                RectF r = new RectF(cx - barW / 2, top, cx + barW / 2, padT + plotH);
                c.drawRoundRect(r, 4 * d, 4 * d, bar);
                String vl = clockMode ? Trends.minToClock(v)
                        : (Math.round(v) + "m");
                c.drawText(vl, cx, top - 4 * d, valText);
            }
            if (labels != null && i < labels.size()) {
                c.drawText(labels.get(i), cx, padT + plotH + 18 * d, text);
            }
        }
    }
}
