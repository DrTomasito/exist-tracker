package com.existtracker.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns daily history into per-work-week (Monday–Friday) averages.
 * Each week is labelled by the date of its Monday.
 */
public class Trends {

    public static class Week {
        public String mondayDate;   // e.g. "2026-05-25"
        public double avgMinutes;    // average across the weekdays that have data
        public int daysCounted;
    }

    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    /**
     * Group a metric's daily values into work weeks and average the
     * Monday–Friday values within each week.
     */
    public static List<Week> weeklyWorkdayAverages(TreeMap<String, Integer> history) {
        // Bucket: Monday-of-week -> list of weekday values
        TreeMap<String, List<Integer>> buckets = new TreeMap<>();

        for (Map.Entry<String, Integer> e : history.entrySet()) {
            Calendar c = parse(e.getKey());
            if (c == null) continue;
            int dow = c.get(Calendar.DAY_OF_WEEK);
            // Skip weekends (Saturday=7, Sunday=1)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) continue;

            String monday = mondayOf(c);
            buckets.computeIfAbsent(monday, k -> new ArrayList<>()).add(e.getValue());
        }

        List<Week> weeks = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> b : buckets.entrySet()) {
            Week w = new Week();
            w.mondayDate = b.getKey();
            int sum = 0;
            for (int v : b.getValue()) sum += v;
            w.daysCounted = b.getValue().size();
            w.avgMinutes = w.daysCounted == 0 ? 0 : (double) sum / w.daysCounted;
            weeks.add(w);
        }
        return weeks; // already chronological because TreeMap sorts keys
    }

    /** Weekly totals: sum of ALL days (incl. weekends) per week. Good for
     *  counters where you want "times per week" rather than a daily average.
     *  The sum is placed in avgMinutes so existing chart code can reuse it. */
    public static List<Week> weeklyTotals(TreeMap<String, Integer> history) {
        TreeMap<String, List<Integer>> buckets = new TreeMap<>();
        for (Map.Entry<String, Integer> e : history.entrySet()) {
            Calendar c = parse(e.getKey());
            if (c == null) continue;
            String monday = mondayOf(c);
            buckets.computeIfAbsent(monday, k -> new ArrayList<>()).add(e.getValue());
        }
        List<Week> weeks = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> b : buckets.entrySet()) {
            Week w = new Week();
            w.mondayDate = b.getKey();
            int sum = 0;
            for (int v : b.getValue()) sum += v;
            w.daysCounted = b.getValue().size();
            w.avgMinutes = sum; // total for the week
            weeks.add(w);
        }
        return weeks;
    }

    /** A short human sentence describing the trend direction. */
    public static String describeTrend(List<Week> weeks, String label) {
        if (weeks.size() < 2) {
            return "Not enough weeks yet to show a trend for " + label
                    + ". Keep tracking — trends appear after a couple of weeks.";
        }
        double first = weeks.get(0).avgMinutes;
        double last = weeks.get(weeks.size() - 1).avgMinutes;
        double recentAvg = 0;
        int n = Math.min(4, weeks.size());
        for (int i = weeks.size() - n; i < weeks.size(); i++) recentAvg += weeks.get(i).avgMinutes;
        recentAvg /= n;

        String dir;
        double change = last - first;
        if (Math.abs(change) < 3) dir = "holding steady";
        else if (change < 0) dir = "trending down";
        else dir = "trending up";

        return label + " is " + dir + ". Recent " + n + "-week average: "
                + Math.round(recentAvg) + " min/day ("
                + String.format(Locale.US, "%.1f", recentAvg / 60.0) + " hrs).";
    }

    private static Calendar parse(String date) {        try {
            Calendar c = Calendar.getInstance();
            c.setTime(FMT.parse(date));
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    private static String mondayOf(Calendar c) {
        Calendar m = (Calendar) c.clone();
        // Move back to Monday
        while (m.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            m.add(Calendar.DAY_OF_MONTH, -1);
        }
        return FMT.format(m.getTime());
    }

    /** Weekly average of a minute-of-day value (e.g. departure time), Mon–Fri. */
    public static List<Week> weeklyTimeOfDayAverages(TreeMap<String, Integer> history) {
        // Same bucketing as weeklyWorkdayAverages but values are minute-of-day.
        return weeklyWorkdayAverages(history);
    }

    /** Average minute-of-day per weekday (Mon..Fri). Index 0=Mon..4=Fri; -1 if none. */
    public static double[] averageByWeekday(TreeMap<String, Integer> history) {
        double[] sum = new double[5];
        int[] count = new int[5];
        for (Map.Entry<String, Integer> e : history.entrySet()) {
            Calendar c = parse(e.getKey());
            if (c == null) continue;
            int dow = c.get(Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
            int idx = dow - Calendar.MONDAY;        // Mon=0..Fri=4
            if (idx < 0 || idx > 4) continue;
            sum[idx] += e.getValue();
            count[idx]++;
        }
        double[] avg = new double[5];
        for (int i = 0; i < 5; i++) avg[i] = count[i] == 0 ? -1 : sum[i] / count[i];
        return avg;
    }

    /** Convert minutes-since-midnight to a clock string like "5:42 PM". */
    public static String minToClock(double minOfDay) {
        if (minOfDay < 0) return "—";
        int total = (int) Math.round(minOfDay);
        int h = total / 60, m = total % 60;
        String ampm = h < 12 ? "AM" : "PM";
        int h12 = h % 12; if (h12 == 0) h12 = 12;
        return String.format(Locale.US, "%d:%02d %s", h12, m, ampm);
    }
}
