package org.openflipclock;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny SharedPreferences wrapper. Every write bumps "rev" so the clock knows to reload. */
final class Prefs {
    static final int A_DARK = 0, A_LIGHT = 1, A_PRESET0 = 2, A_WALLHAVEN = 7, A_LOCAL = 8;

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
        if (!k.equals("wh_last")) e.putLong("rev", System.nanoTime());
        e.apply();
    }
}
