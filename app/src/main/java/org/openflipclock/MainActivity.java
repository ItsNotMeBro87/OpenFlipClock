package org.openflipclock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import java.util.Calendar;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity implements FlipClockView.Host {
    private FlipClockView v;
    private ScrollView drawer;
    private View scrim;
    private boolean open;
    private int dw;
    static boolean visible;
    private final TextView[] items = new TextView[6];
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dw = Math.min(UI.dp(this, 260), (int) (getResources().getDisplayMetrics().widthPixels * 0.8f));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        v = new FlipClockView(this);
        v.host = this;
        root.addView(v);
        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { closeDrawer(); } });
        root.addView(scrim);
        buildDrawer(root);
        setContentView(root);
    }

    private void buildDrawer(FrameLayout root) {
        LinearLayout col = new LinearLayout(this);   // the drawer scrolls, so every item is reachable in landscape
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, UI.dp(this, 40), 0, UI.dp(this, 16));
        drawer = new ScrollView(this);
        drawer.setBackgroundColor(0xFA1A1A1A);
        drawer.addView(col);
        TextView t = new TextView(this);
        t.setText("OpenFlipClock");
        t.setTextSize(20);
        t.setTextColor(0xFFFFFFFF);
        t.setPadding(UI.dp(this, 24), 0, 0, 0);
        col.addView(t);
        TextView ver = new TextView(this);
        ver.setText(UI.VERSION);
        ver.setTextSize(13);
        ver.setTextColor(0xFF9E9E9E);
        ver.setPadding(UI.dp(this, 24), UI.dp(this, 2), 0, UI.dp(this, 16));
        col.addView(ver);
        String[] names = {"FlipClock", "Pomodoro", "Flowmodoro", "Alarm", "Settings", "Credit"};
        for (int i = 0; i < 6; i++) {
            TextView it = new TextView(this);
            it.setText(names[i]);
            it.setTextSize(17);
            it.setTextColor(0xFFFFFFFF);
            it.setPadding(UI.dp(this, 24), UI.dp(this, 16), UI.dp(this, 24), UI.dp(this, 16));
            final int idx = i;
            it.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { select(idx); } });
            items[i] = it;
            col.addView(it);
        }
        root.addView(drawer, new FrameLayout.LayoutParams(dw, ViewGroup.LayoutParams.MATCH_PARENT));
        drawer.setTranslationX(-dw);
    }

    private void select(int i) {
        closeDrawer();
        if (i <= 2) v.setMode(i);
        else if (i == 3) startActivity(new Intent(this, AlarmActivity.class));
        else if (i == 4) startActivity(new Intent(this, SettingsActivity.class));
        else startActivity(new Intent(this, CreditActivity.class));
    }

    @Override public void openDrawer() {
        if (open) return;
        open = true;
        int mode = Prefs.i(this, "mode", 0);
        for (int k = 0; k < 6; k++) items[k].setBackgroundColor(k == mode ? 0x33FFFFFF : 0);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(160);
        drawer.animate().translationX(0).setDuration(180);
    }

    private void closeDrawer() {
        if (!open) return;
        open = false;
        scrim.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
            public void run() { scrim.setVisibility(View.GONE); }
        });
        drawer.animate().translationX(-dw).setDuration(180);
    }

    @Override public void onBackPressed() {
        if (open) closeDrawer();
        else if (Prefs.i(this, "mode", 0) != 0) v.setMode(0);
        else super.onBackPressed();
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        applyWindow();
        if (v.rev != Prefs.rev(this)) v.reload();
        v.start();
        scheduleAuto();
        brightRun.run();
        checkBing();
    }

    @Override protected void onPause() {
        super.onPause();
        visible = false;
        v.stop();
        h.removeCallbacksAndMessages(null);
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) applyWindow();
    }

    private void applyWindow() {
        if (Prefs.b(this, "keepon", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int o = Prefs.i(this, "orient", 0);
        setRequestedOrientation(o == 1 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : o == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        View d = getWindow().getDecorView();
        if (Prefs.b(this, "fullscreen", true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    // ---- Bing daily: refresh the picture once a day when the clock is opened (optional) ----
    private void checkBing() {
        if (Prefs.i(this, "look", 0) != Prefs.A_BING || !Prefs.b(this, "bing_auto", false)) return;
        final Context c = getApplicationContext();
        final int today = Timers.dayNum();
        if (Prefs.i(c, "bing_day", -1) == today) return;
        Prefs.put(c, "bing_day", today);
        new Thread(new Runnable() {
            public void run() {
                try {
                    ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (Prefs.b(c, "wh_wifi", true) && cm.isActiveNetworkMetered()) { Prefs.put(c, "bing_day", -1); return; }
                    Sources.bingAuto(c);
                } catch (Exception e) { Prefs.put(c, "bing_day", -1); return; }
                h.post(new Runnable() { public void run() { v.reload(); v.rev = Prefs.rev(c); } });
            }
        }).start();
    }

    // ---- Brightness + optional night dimming schedule (re-checked once a minute while on screen) ----
    private final Runnable brightRun = new Runnable() {
        public void run() {
            int lvl = Prefs.i(MainActivity.this, "bright", 100);
            boolean dim = false;
            if (Prefs.b(MainActivity.this, "dim_on", false)) {
                Calendar cal = Calendar.getInstance();
                int m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
                int a = Prefs.i(MainActivity.this, "dim_from", 1320), b = Prefs.i(MainActivity.this, "dim_to", 360);
                dim = a <= b ? (m >= a && m < b) : (m >= a || m < b);
            }
            if (dim) lvl = Prefs.i(MainActivity.this, "dim_level", 10);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = (!dim && lvl >= 100) ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : Math.max(0.01f, lvl / 100f);
            getWindow().setAttributes(lp);
            h.postDelayed(this, 60000);
        }
    };

    // ---- Wallhaven auto-update: only runs while this screen is visible. No service, no alarms. ----
    private final Runnable autoRun = new Runnable() {
        public void run() {
            final Context c = getApplicationContext();
            new Thread(new Runnable() {
                public void run() {
                    Prefs.put(c, "wh_last", System.currentTimeMillis());
                    try {
                        boolean wifiOnly = Prefs.b(c, "wh_wifi", true);
                        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
                        if (!(wifiOnly && cm.isActiveNetworkMetered())) Wallhaven.autoUpdate(c);
                    } catch (Exception ignored) { }
                    h.post(new Runnable() {
                        public void run() { v.reload(); v.rev = Prefs.rev(c); scheduleAuto(); }
                    });
                }
            }).start();
        }
    };

    private void scheduleAuto() {
        h.removeCallbacks(autoRun);
        if (Prefs.i(this, "look", 0) != Prefs.A_WALLHAVEN || !Prefs.b(this, "wh_auto", false)) return;
        long interval = Math.max(5, Prefs.l(this, "wh_interval", 30)) * 60000L;
        long delay = Math.max(1500, Prefs.l(this, "wh_last", 0) + interval - System.currentTimeMillis());
        h.postDelayed(autoRun, delay);
    }
}
