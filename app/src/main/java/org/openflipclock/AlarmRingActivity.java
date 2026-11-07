package org.openflipclock;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen ringing screen shown over the lock screen. */
public class AlarmRingActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        final int id = getIntent().getIntExtra("id", 0);
        Alarms.Alarm a = Alarms.get(this, id);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(0xFF000000);
        int p = UI.dp(this, 24);
        l.setPadding(p, p, p, p);

        TextView t = new TextView(this);
        t.setText(new SimpleDateFormat(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date()));
        t.setTextSize(64);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        TextView lab = new TextView(this);
        lab.setText(a == null || a.label.isEmpty() ? "Alarm" : a.label);
        lab.setTextSize(22);
        lab.setTextColor(0xFFBBBBBB);
        lab.setGravity(Gravity.CENTER);
        lab.setPadding(0, 0, 0, UI.dp(this, 40));
        l.addView(lab);

        Button snooze = UI.button(this, "Snooze", new View.OnClickListener() {
            public void onClick(View v) { send(AlarmService.A_SNOOZE, id); }
        });
        Button dismiss = UI.button(this, "Dismiss", new View.OnClickListener() {
            public void onClick(View v) { send(AlarmService.A_DISMISS, id); }
        });
        snooze.setTextSize(20);
        dismiss.setTextSize(20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.dp(this, 280), UI.dp(this, 64));
        lp.setMargins(0, UI.dp(this, 8), 0, UI.dp(this, 8));
        l.addView(snooze, lp);
        l.addView(dismiss, lp);
        setContentView(l);
    }

    private void send(String action, int id) {
        startService(new Intent(this, AlarmService.class).setAction(action).putExtra("id", id));
        finish();
    }

    @Override public void onBackPressed() { }   // must choose Snooze or Dismiss
}
