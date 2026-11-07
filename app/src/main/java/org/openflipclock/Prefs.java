package org.openflipclock;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny SharedPreferences wrapper. Settings writes bump "rev" so the clock knows to reload. */
final class Prefs {
    static final int A_DARK = 0, A_LIGHT = 1, A_CUSTOM = 2, A_WALLHAVEN = 3, A_LOCAL = 4, A_PEXELS = 5, A_BING = 6;

    /** Cached background file for an image-based appearance, or null. */
    static String bgFile(int look) {
        switch (look) {
            case A_WALLHAVEN: return "bg_wh.jpg";
            case A_LOCAL: return "bg_local.jpg";
            case A_PEXELS: return "bg_px.jpg";
            case A_BING: return "bg_bing.jpg";
            default: return null;
        }
    }

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("ofc", Context.MODE_PRIVATE);
    }
    static boolean b(Context c, String k, boolean d) { return sp(c).getBoolean(k, d); }
    static int i(Context c, String k, int d) { return sp(c).getInt(k, d); }
    static long l(Context c, String k, long d) { return sp(c).getLong(k, d); }
    static String s(Context c, String k, String d) { return sp(c).getString(k, d); }
    static long rev(Context c) { return l(c, "rev", 0); }

    static void put(Context c, String k, Object v) {
        SharedPreferences.Editor e = sp(c).edit();
        if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
        else if (v instanceof Integer) e.putInt(k, (Integer) v);
        else if (v instanceof Long) e.putLong(k, (Long) v);
        else e.putString(k, (String) v);
        // runtime state (timers, mode, last wallpaper update) must not trigger a background rebuild
        if (!(k.equals("wh_last") || k.equals("mode") || k.equals("alarms") || k.equals("bing_day") || k.startsWith("tm_"))) e.putLong("rev", System.nanoTime());
        e.apply();
    }
}
