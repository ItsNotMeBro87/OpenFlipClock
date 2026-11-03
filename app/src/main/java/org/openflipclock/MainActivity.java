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
import android.view.WindowManager;

public class MainActivity extends Activity implements FlipClockView.Host {
    private FlipClockView v;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        v = new FlipClockView(this);
        v.host = this;
        setContentView(v);
    }

    @Override public void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }

    @Override protected void onResume() {
        super.onResume();
        applyWindow();
        if (v.rev != Prefs.rev(this)) v.reload();
        v.start();
        scheduleAuto();
    }

    @Override protected void onPause() {
        super.onPause();
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
        if (Prefs.i(this, "appearance", 0) != Prefs.A_WALLHAVEN || !Prefs.b(this, "wh_auto", false)) return;
        long interval = Math.max(5, Prefs.l(this, "wh_interval", 30)) * 60000L;
        long delay = Math.max(1500, Prefs.l(this, "wh_last", 0) + interval - System.currentTimeMillis());
        h.postDelayed(autoRun, delay);
    }
}
