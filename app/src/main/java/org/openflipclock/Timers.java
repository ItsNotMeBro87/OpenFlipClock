package org.openflipclock;

import android.content.Context;

/** Pomodoro + Flowmodoro logic. State lives in prefs as timestamps, so nothing runs in the background. */
final class Timers {
    static final int[][] PRE = {{25, 5}, {50, 10}, {90, 20}};
    static final int[] DEN = {3, 5, 8};

    static int focusMin(Context c) { int p = Prefs.i(c, "tm_preset", 0); return p < 3 ? PRE[p][0] : Prefs.i(c, "tm_focus", 25); }
    static int breakMin(Context c) { int p = Prefs.i(c, "tm_preset", 0); return p < 3 ? PRE[p][1] : Prefs.i(c, "tm_break", 5); }

    // ---------------- today's focus total ----------------
    private static int dayNum() {
        long n = System.currentTimeMillis();
        return (int) ((n + java.util.TimeZone.getDefault().getOffset(n)) / 86400000L);
    }
    static long todayMs(Context c) { return Prefs.i(c, "tm_day", -1) == dayNum() ? Prefs.l(c, "tm_today", 0) : 0; }
    static void addToday(Context c, long ms) {
        if (ms <= 0) return;
        long t = todayMs(c) + ms;
        Prefs.put(c, "tm_day", dayNum());
        Prefs.put(c, "tm_today", t);
    }

    static boolean pLong(Context c) { return Prefs.b(c, "tm_plong", false); }

    // ---------------- Pomodoro: state 0 idle, 1 focus, 2 break ----------------
    static int pState(Context c) { return Prefs.i(c, "tm_pstate", 0); }
    static boolean pPaused(Context c) { return Prefs.b(c, "tm_ppaused", false); }

    static long pRemaining(Context c, long now) {
        if (pState(c) == 0) return focusMin(c) * 60000L;
        if (pPaused(c)) return Prefs.l(c, "tm_prem", 0);
        return Prefs.l(c, "tm_pend", 0) - now;
    }

    static void pStartPause(Context c, long now) {
        if (pState(c) == 0) {
            Prefs.put(c, "tm_pstate", 1);
            Prefs.put(c, "tm_pend", now + focusMin(c) * 60000L);
            Prefs.put(c, "tm_ppaused", false);
        } else if (pPaused(c)) {
            Prefs.put(c, "tm_pend", now + Prefs.l(c, "tm_prem", 0));
            Prefs.put(c, "tm_ppaused", false);
        } else {
            Prefs.put(c, "tm_prem", Prefs.l(c, "tm_pend", now) - now);
            Prefs.put(c, "tm_ppaused", true);
        }
    }

    static void pSkip(Context c, long now) {
        int s = pState(c);
        if (s == 1) {
            addToday(c, focusMin(c) * 60000L - Math.max(0, pRemaining(c, now)));
            Prefs.put(c, "tm_pstate", 2);
            Prefs.put(c, "tm_plong", false);
            Prefs.put(c, "tm_pend", now + breakMin(c) * 60000L);
            Prefs.put(c, "tm_ppaused", false);
        } else if (s == 2) {
            Prefs.put(c, "tm_pstate", 0);
            Prefs.put(c, "tm_ppaused", false);
        }
    }

    static void pStop(Context c, long now) {
        if (pState(c) == 1) addToday(c, focusMin(c) * 60000L - Math.max(0, pRemaining(c, now)));
        Prefs.put(c, "tm_pstate", 0);
        Prefs.put(c, "tm_ppaused", false);
        Prefs.put(c, "tm_round", 0);
    }

    /** Moves focus -> break -> idle when a phase has run out. Returns true if something changed. */
    static boolean pAdvance(Context c, long now) {
        int s = pState(c);
        if (s == 0 || pPaused(c)) return false;
        long end = Prefs.l(c, "tm_pend", 0);
        if (now < end) return false;
        if (s == 1) {
            addToday(c, focusMin(c) * 60000L);
            int r = Prefs.i(c, "tm_round", 0) + 1;
            Prefs.put(c, "tm_round", r);
            boolean lng = Prefs.b(c, "tm_longon", true) && r % 4 == 0;
            Prefs.put(c, "tm_plong", lng);
            long be = end + (lng ? Prefs.i(c, "tm_longmin", 15) : breakMin(c)) * 60000L;
            if (now < be) { Prefs.put(c, "tm_pstate", 2); Prefs.put(c, "tm_pend", be); }
            else Prefs.put(c, "tm_pstate", 0);
        } else {
            Prefs.put(c, "tm_pstate", 0);
            if (Prefs.b(c, "tm_auto", false)) pStartPause(c, now);
        }
        return true;
    }

    // ---------------- Flowmodoro: state 0 idle, 1 flow, 2 break ----------------
    static int fState(Context c) { return Prefs.i(c, "tm_fstate", 0); }
    static long fElapsed(Context c, long now) { return now - Prefs.l(c, "tm_fstart", now); }
    static long fBreakRem(Context c, long now) { return Prefs.l(c, "tm_fend", now) - now; }

    /** Break length = flow time / ratio (1/3, 1/5 or 1/8), at least 1 minute. */
    static void fStartBreak(Context c, long now) {
        if (fState(c) == 1) {
            long flow = fElapsed(c, now);
            addToday(c, flow);
            Prefs.put(c, "tm_ftotal", Prefs.l(c, "tm_ftotal", 0) + flow);
            long bm = Math.max(60000L, flow / DEN[Math.min(2, Prefs.i(c, "tm_ratio", 1))]);
            Prefs.put(c, "tm_fend", now + bm);
            Prefs.put(c, "tm_fstate", 2);
        } else {
            Prefs.put(c, "tm_fstart", now);
            Prefs.put(c, "tm_fstate", 1);
        }
    }

    /** Ends the session and returns total flow time in ms. */
    static long fEnd(Context c, long now) {
        long running = fState(c) == 1 ? fElapsed(c, now) : 0;
        addToday(c, running);
        long total = Prefs.l(c, "tm_ftotal", 0) + running;
        Prefs.put(c, "tm_fstate", 0);
        Prefs.put(c, "tm_ftotal", 0L);
        return total;
    }
}
