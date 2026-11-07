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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements FlipClockView.Host {
    private FlipClockView v;
    private LinearLayout drawer;
    private View scrim;
    private boolean open;
    private int dw;
    private final TextView[] items = new TextView[5];
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
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(0xFA1A1A1A);
        drawer.setPadding(0, UI.dp(this, 40), 0, 0);
        TextView t = new TextView(this);
        t.setText("OpenFlipClock");
        t.setTextSize(20);
        t.setTextColor(0xFFFFFFFF);
        t.setPadding(UI.dp(this, 24), 0, 0, UI.dp(this, 16));
        drawer.addView(t);
        String[] names = {"FlipClock", "Pomodoro", "Flowmodoro", "Alarm  (v0.2.1)", "Settings"};
        for (int i = 0; i < 5; i++) {
            TextView it = new TextView(this);
            it.setText(names[i]);
            it.setTextSize(17);
            it.setTextColor(i == 3 ? 0xFF777777 : 0xFFFFFFFF);
            it.setPadding(UI.dp(this, 24), UI.dp(this, 16), UI.dp(this, 24), UI.dp(this, 16));
            final int idx = i;
            it.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { select(idx); } });
            items[i] = it;
            drawer.addView(it);
        }
        root.addView(drawer, new FrameLayout.LayoutParams(dw, ViewGroup.LayoutParams.MATCH_PARENT));
        drawer.setTranslationX(-dw);
    }

    private void select(int i) {
        closeDrawer();
        if (i <= 2) v.setMode(i);
        else if (i == 3) Toast.makeText(this, "Alarms arrive in v0.2.1 Beta", Toast.LENGTH_SHORT).show();
        else startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override public void openDrawer() {
        if (open) return;
        open = true;
        int mode = Prefs.i(this, "mode", 0);
        for (int k = 0; k < 5; k++) items[k].setBackgroundColor(k == mode ? 0x33FFFFFF : 0);
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
        if (Prefs.i(this, "look", 0) != Prefs.A_WALLHAVEN || !Prefs.b(this, "wh_auto", false)) return;
        long interval = Math.max(5, Prefs.l(this, "wh_interval", 30)) * 60000L;
        long delay = Math.max(1500, Prefs.l(this, "wh_last", 0) + interval - System.currentTimeMillis());
        h.postDelayed(autoRun, delay);
    }
}
