package org.openflipclock;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Lets Pomodoro/Flowmodoro phase ends notify you while the app is closed (one OS alarm per timer). */
final class TimerAlerts {
    private static PendingIntent pi(Context c, int code, String text) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("kind", "timer").putExtra("text", text);
        return PendingIntent.getBroadcast(c, 500 + code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void set(Context c, int code, long when, String text) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pi(c, code, text);
        if (when <= System.currentTimeMillis()) { am.cancel(p); return; }
        try {
            if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
            else am.setExact(AlarmManager.RTC_WAKEUP, when, p);
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
            else am.set(AlarmManager.RTC_WAKEUP, when, p);
        }
    }

    private static void cancel(Context c, int code) {
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(pi(c, code, ""));
    }

    /** Call after every timer state change. */
    static void sync(Context c) {
        int s = Timers.pState(c);
        if (s != 0 && !Timers.pPaused(c))
            set(c, 1, Prefs.l(c, "tm_pend", 0), s == 1 ? "Focus finished. Time for a break." : "Break over. Ready to focus?");
        else cancel(c, 1);
        if (Timers.fState(c) == 2) set(c, 2, Prefs.l(c, "tm_fend", 0), "Flowmodoro break time is over.");
        else cancel(c, 2);
    }

    static void notifyNow(Context c, Intent i) {
        if (MainActivity.visible) return;   // the app already beeps when it is on screen
        Notif.channel(c, "timer", "Timers", NotificationManager.IMPORTANCE_HIGH, false);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Notif.builder(c, "timer")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("OpenFlipClock")
                .setContentText(i.getStringExtra("text"))
                .setContentIntent(open)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) b.setDefaults(Notification.DEFAULT_SOUND).setPriority(Notification.PRIORITY_HIGH);
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(4242, b.build());
    }
}
