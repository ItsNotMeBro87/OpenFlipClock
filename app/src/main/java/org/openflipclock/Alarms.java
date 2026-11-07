package org.openflipclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** Alarm storage (one JSON string in prefs) and scheduling through AlarmManager. Nothing runs while idle. */
final class Alarms {
    static final int MAX = 10;
    static final class Alarm { int id, h = 7, m = 0, days; boolean on = true; String label = "", sound = ""; }

    static List<Alarm> load(Context c) {
        List<Alarm> l = new ArrayList<Alarm>();
        try {
            JSONArray a = new JSONArray(Prefs.s(c, "alarms", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Alarm x = new Alarm();
                x.id = o.getInt("id"); x.h = o.getInt("h"); x.m = o.getInt("m"); x.days = o.getInt("days");
                x.on = o.getBoolean("on"); x.label = o.optString("label", ""); x.sound = o.optString("sound", "");
                l.add(x);
            }
        } catch (Exception ignored) { }
        return l;
    }

    static void save(Context c, List<Alarm> l) {
        JSONArray a = new JSONArray();
        try {
            for (Alarm x : l) {
                JSONObject o = new JSONObject();
                o.put("id", x.id); o.put("h", x.h); o.put("m", x.m); o.put("days", x.days);
                o.put("on", x.on); o.put("label", x.label); o.put("sound", x.sound);
                a.put(o);
            }
        } catch (Exception ignored) { }
        Prefs.put(c, "alarms", a.toString());
    }

    static Alarm get(Context c, int id) {
        for (Alarm a : load(c)) if (a.id == id) return a;
        return null;
    }

    static void put(Context c, Alarm a) {
        List<Alarm> l = load(c);
        boolean found = false;
        for (int i = 0; i < l.size(); i++) if (l.get(i).id == a.id) { l.set(i, a); found = true; }
        if (!found) l.add(a);
        save(c, l);
    }

    static void remove(Context c, int id) {
        List<Alarm> l = load(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == id) l.remove(i);
        save(c, l);
        cancel(c, id);
    }

    static int newId(List<Alarm> l) {
        for (int id = 1; id <= 99; id++) {
            boolean used = false;
            for (Alarm a : l) if (a.id == id) used = true;
            if (!used) return id;
        }
        return 99;
    }

    /** Next time this alarm should ring after "from". days bit0 = Sunday ... bit6 = Saturday, 0 = once. */
    static long next(Alarm a, long from) {
        Calendar cal = Calendar.getInstance();
        for (int d = 0; d < 8; d++) {
            cal.setTimeInMillis(from);
            cal.add(Calendar.DAY_OF_YEAR, d);
            cal.set(Calendar.HOUR_OF_DAY, a.h);
            cal.set(Calendar.MINUTE, a.m);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() > from && (a.days == 0 || ((a.days >> (cal.get(Calendar.DAY_OF_WEEK) - 1)) & 1) == 1))
                return cal.getTimeInMillis();
        }
        return from + 86400000L;
    }

    private static PendingIntent pi(Context c, int code, int id, boolean snooze) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("id", id).putExtra("snooze", snooze);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static AlarmManager am(Context c) { return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE); }

    private static void setClock(Context c, long t, PendingIntent p) {
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, AlarmActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { am(c).setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), p); }
        catch (SecurityException e) { am(c).set(AlarmManager.RTC_WAKEUP, t, p); }
    }

    static void schedule(Context c, Alarm a) {
        PendingIntent p = pi(c, a.id, a.id, false);
        if (!a.on) { am(c).cancel(p); return; }
        setClock(c, next(a, System.currentTimeMillis()), p);
    }

    static void cancel(Context c, int id) {
        am(c).cancel(pi(c, id, id, false));
        am(c).cancel(pi(c, 100 + id, id, true));
    }

    static void rescheduleAll(Context c) { for (Alarm a : load(c)) schedule(c, a); }

    /** Called when an alarm fires. Returns true if it should ring. */
    static boolean onFired(Context c, int id, boolean snooze) {
        Alarm a = get(c, id);
        if (a == null) return false;
        if (snooze) return true;
        if (!a.on) return false;
        if (a.days == 0) { a.on = false; put(c, a); } else schedule(c, a);
        return true;
    }

    static void snooze(Context c, int id, int minutes) {
        setClock(c, System.currentTimeMillis() + minutes * 60000L, pi(c, 100 + id, id, true));
    }

    static void cancelSnooze(Context c, int id) { am(c).cancel(pi(c, 100 + id, id, true)); }

    /** Time of the next enabled alarm as text, or "" when none is set (used for the bell on the clock). */
    static String nextText(Context c, boolean h24) {
        long best = Long.MAX_VALUE, now = System.currentTimeMillis();
        for (Alarm a : load(c)) if (a.on) best = Math.min(best, next(a, now));
        if (best == Long.MAX_VALUE) return "";
        return new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date(best));
    }
}
