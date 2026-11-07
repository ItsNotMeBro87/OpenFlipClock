package org.openflipclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends Activity {
    private LinearLayout root;
    private static final String[] DATE_FMT = {"yyyy/MM/dd", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "d MMM yyyy", "EEE, d MMM"};
    /** Runtime state / user data that "Reset to default" must not wipe. */
    private static final List<String> KEEP = Arrays.asList("wh_key", "alarms", "tm_pstate", "tm_pend", "tm_ppaused", "tm_prem",
            "tm_round", "tm_plong", "tm_fstate", "tm_fstart", "tm_fend", "tm_ftotal", "tm_today", "tm_day");
    private final Runnable rec = new Runnable() { public void run() { recreate(); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(this, 16);
        root.setPadding(p, p, p, p);
        sv.addView(root);
        setContentView(sv);
        build();
    }

    private void add(View v) { root.addView(v); }

    private void build() {
        final int look = Prefs.i(this, "look", 0);

        // ---- Clock ----
        add(UI.label(this, "Clock", true));
        boolean sys24 = android.text.format.DateFormat.is24HourFormat(this);
        add(UI.choiceRow(this, "Time format", new String[]{"12-hour (AM/PM)", "24-hour"}, "hfmt", sys24 ? 1 : 0, null));
        add(UI.check(this, "Show day and date", "date", true, rec));
        if (Prefs.b(this, "date", true)) {
            String[] labels = new String[DATE_FMT.length];
            for (int i = 0; i < labels.length; i++) labels[i] = new SimpleDateFormat(DATE_FMT[i], Locale.getDefault()).format(new Date());
            add(UI.choiceRow(this, "Date format", labels, "datefmt_i", 0, new UI.IntCb() {
                public void run(int v) { Prefs.put(SettingsActivity.this, "datefmt", DATE_FMT[v]); }
            }));
        }
        add(UI.check(this, "Show seconds", "sec", true));
        add(UI.check(this, "Show battery percentage", "bat", false));
        add(UI.sliderRow(this, "FlipClock size", "size_clock", 100, 100, "%"));

        // ---- Appearance: only the options that belong to the chosen appearance are shown ----
        add(UI.label(this, "Appearance", true));
        add(UI.choiceRow(this, "Appearance", new String[]{"Dark", "Light", "Custom color", "Wallhaven", "Local image"}, "look", 0, new UI.IntCb() {
            public void run(int v) {
                if (v == Prefs.A_WALLHAVEN && !new File(getFilesDir(), "bg_wh.jpg").exists()) openWallhaven();
                if (v == Prefs.A_LOCAL && !new File(getFilesDir(), "bg_local.jpg").exists()) pickImage();
                recreate();
            }
        }));
        if (look == Prefs.A_CUSTOM) add(UI.colorRow(this, "Background color", "col_bg"));
        if (look == Prefs.A_WALLHAVEN)
            add(UI.button(this, "Browse Wallhaven…", new View.OnClickListener() { public void onClick(View v) { openWallhaven(); } }));
        if (look == Prefs.A_LOCAL)
            add(UI.button(this, "Choose local image…", new View.OnClickListener() { public void onClick(View v) { pickImage(); } }));
        if (look >= Prefs.A_WALLHAVEN)
            add(UI.choiceRow(this, "Wallpaper position", new String[]{"Scaled and Cropped", "Scaled", "Scaled, Keep Proportions", "Centered", "Tiled"}, "pos", 0, null));
        add(UI.colorRow(this, "Clock (number) color", "col_digit"));
        add(UI.colorRow(this, "Flipboard color", "col_tile"));
        if (look >= Prefs.A_CUSTOM) add(UI.sliderRow(this, "Flipboard transparency", "trans", 75, 0, "%"));
        if (look >= Prefs.A_WALLHAVEN) add(UI.sliderRow(this, "Flipboard background blur", "blur", 24, 8, ""));

        // ---- Timers ----
        add(UI.label(this, "Pomodoro / Flowmodoro", true));
        add(UI.sliderRow(this, "Pomodoro / Flowmodoro size", "size_timer", 100, 100, "%"));
        add(UI.choiceRow(this, "Flowmodoro break length", new String[]{"1/3 of flow time", "1/5 of flow time", "1/8 of flow time"}, "tm_ratio", 1, null));
        add(UI.check(this, "Auto-start focus after a Pomodoro break", "tm_auto", false));
        add(UI.check(this, "Long break after every 4 Pomodoro rounds", "tm_longon", true, rec));
        if (Prefs.b(this, "tm_longon", true)) add(UI.sliderRow(this, "Long break length", "tm_longmin", 45, 15, " min"));

        // ---- Alarm ----
        add(UI.label(this, "Alarm", true));
        add(UI.button(this, "Open alarms…", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, AlarmActivity.class)); }
        }));
        add(UI.check(this, "Vibrate when alarm rings", "al_vib", false));
        add(UI.choiceRow(this, "Snooze length", new String[]{"5 minutes", "10 minutes", "15 minutes"}, "al_snz", 1, null));

        // ---- Display ----
        add(UI.label(this, "Display", true));
        add(UI.choiceRow(this, "Orientation", new String[]{"Automatic", "Landscape", "Portrait"}, "orient", 0, null));
        add(UI.check(this, "Keep screen on", "keepon", true));
        add(UI.check(this, "Fullscreen", "fullscreen", true));
        add(UI.check(this, "Pixel shift (protects AMOLED from burn-in)", "shift", true));
        add(UI.sliderRow(this, "Display corner radius", "disp_radius", 60, 0, " dp"));
        add(UI.sliderRow(this, "Screen brightness", "bright", 100, 100, "%"));
        add(UI.check(this, "Night dimming schedule", "dim_on", false, rec));
        if (Prefs.b(this, "dim_on", false)) {
            add(UI.timeRow(this, "Dim from", "dim_from", 1320));
            add(UI.timeRow(this, "Dim until", "dim_to", 360));
            add(UI.sliderRow(this, "Dimmed brightness", "dim_level", 50, 10, "%"));
        }
        add(UI.button(this, "Set up screensaver (Android settings)…", new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
                catch (Exception e) { startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); }
            }
        }));
        TextView hint = UI.label(this, "Choose OpenFlipClock as your screensaver there. It shows the clock while charging or docked.", false);
        hint.setTextSize(12);
        add(hint);

        // ---- Reset ----
        add(UI.label(this, "Maintenance", true));
        add(UI.button(this, "Reset to default", new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this).setTitle("Reset all settings?")
                    .setMessage("Colors, appearance, timers and display options go back to defaults. Your alarms and Wallhaven API key are kept.")
                    .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { resetDefaults(); }
                    }).setNegativeButton("Cancel", null).show();
            }
        }));
        add(UI.button(this, "Clear Wallhaven API key", new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.put(SettingsActivity.this, "wh_key", "");
                Toast.makeText(SettingsActivity.this, "API key cleared", Toast.LENGTH_SHORT).show();
            }
        }));

        TextView v = UI.label(this, "OpenFlipClock " + UI.VERSION, false);
        v.setPadding(0, UI.dp(this, 28), 0, 0);
        v.setAlpha(.6f);
        add(v);
    }

    private void resetDefaults() {
        SharedPreferences sp = Prefs.sp(this);
        SharedPreferences.Editor e = sp.edit();
        for (String k : new ArrayList<String>(sp.getAll().keySet())) if (!KEEP.contains(k)) e.remove(k);
        e.putLong("rev", System.nanoTime()).apply();
        recreate();
    }

    private void openWallhaven() { startActivity(new Intent(this, WallhavenActivity.class)); }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Choose image"), 1);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req != 1 || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        Toast.makeText(this, "Importing…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                try {
                    Img.importBytes(SettingsActivity.this, Img.readAll(getContentResolver().openInputStream(u)), "bg_local.jpg");
                    Prefs.put(SettingsActivity.this, "look", Prefs.A_LOCAL);
                    runOnUiThread(new Runnable() { public void run() { recreate(); } });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(SettingsActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        }).start();
    }

    @Override protected void onRestart() { super.onRestart(); recreate(); } // refresh after Wallhaven / image picker
}
