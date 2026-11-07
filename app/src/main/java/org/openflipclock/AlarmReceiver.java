package org.openflipclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Receives alarm / timer-alert broadcasts and boot/time-change events (to reschedule). */
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String act = i.getAction();
        if (act != null) {   // boot, time change, timezone change, app update
            Alarms.rescheduleAll(c);
            TimerAlerts.sync(c);
            return;
        }
        if ("timer".equals(i.getStringExtra("kind"))) { TimerAlerts.notifyNow(c, i); return; }
        int id = i.getIntExtra("id", 0);
        if (!Alarms.onFired(c, id, i.getBooleanExtra("snooze", false))) return;
        Intent s = new Intent(c, AlarmService.class).setAction(AlarmService.A_START).putExtra("id", id);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
    }
}
