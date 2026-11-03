package org.openflipclock;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends Activity {
    private LinearLayout root;
    private static final String[] DATE_FMT = {"yyyy/MM/dd", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "d MMM yyyy", "EEE, d MMM"};

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
        // ---- Clock ----
        add(UI.label(this, "Clock", true));
        add(UI.label(this, "Time format", false));
        boolean sys24 = android.text.format.DateFormat.is24HourFormat(this);
        add(UI.spinner(this, new String[]{"12-hour (AM/PM)", "24-hour"}, "hfmt", sys24 ? 1 : 0, null));
        add(UI.check(this, "Show day and date", "date", true));
        String[] labels = new String[DATE_FMT.length];
        for (int i = 0; i < labels.length; i++) labels[i] = new SimpleDateFormat(DATE_FMT[i], Locale.getDefault()).format(new Date());
        add(UI.label(this, "Date format", false));
        add(UI.spinner(this, labels, "datefmt_i", 0, new UI.IntCb() {
            public void run(int v) { Prefs.put(SettingsActivity.this, "datefmt", DATE_FMT[v]); }
        }));
        add(UI.check(this, "Show seconds", "sec", true));
        add(UI.check(this, "Show battery percentage", "bat", false));

        // ---- Appearance ----
        add(UI.label(this, "Appearance", true));
        String[] ap = new String[9];
        ap[0] = "Dark"; ap[1] = "Light";
        for (int i = 0; i < 5; i++) ap[2 + i] = "Prebuilt " + (i + 1) + ": " + Scenes.NAMES[i];
        ap[7] = "Wallhaven"; ap[8] = "Local image";
        add(UI.spinner(this, ap, "appearance", 0, new UI.IntCb() {
            public void run(int v) {
                if (v == Prefs.A_WALLHAVEN && !new File(getFilesDir(), "bg_wh.jpg").exists()) openWallhaven();
                if (v == Prefs.A_LOCAL && !new File(getFilesDir(), "bg_local.jpg").exists()) pickImage();
            }
        }));
        add(UI.button(this, "Browse Wallhaven…", new View.OnClickListener() { public void onClick(View v) { openWallhaven(); } }));
        add(UI.button(this, "Choose local image…", new View.OnClickListener() { public void onClick(View v) { pickImage(); } }));
        add(UI.label(this, "Wallpaper position", false));
        add(UI.spinner(this, new String[]{"Scaled and Cropped", "Scaled", "Scaled, Keep Proportions", "Centered", "Tiled"}, "pos", 0, null));
        add(UI.seek(this, "Flipboard transparency", "trans", 75, 0, "%"));
        add(UI.seek(this, "Flipboard background blur", "blur", 24, 8, ""));

        // ---- Display ----
        add(UI.label(this, "Display", true));
        add(UI.label(this, "Orientation", false));
        add(UI.spinner(this, new String[]{"Automatic", "Landscape", "Portrait"}, "orient", 0, null));
        add(UI.check(this, "Keep screen on", "keepon", true));
        add(UI.check(this, "Fullscreen", "fullscreen", true));
        add(UI.check(this, "Pixel shift (protects AMOLED from burn-in)", "shift", true));

        TextView v = UI.label(this, "OpenFlipClock v0.1.0 Beta", false);
        v.setPadding(0, UI.dp(this, 28), 0, 0);
        v.setAlpha(.6f);
        add(v);
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
                    Prefs.put(SettingsActivity.this, "appearance", Prefs.A_LOCAL);
                    runOnUiThread(new Runnable() { public void run() { recreate(); } });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(SettingsActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        }).start();
    }

    @Override protected void onRestart() { super.onRestart(); recreate(); } // refresh after Wallhaven picks a wallpaper
}
