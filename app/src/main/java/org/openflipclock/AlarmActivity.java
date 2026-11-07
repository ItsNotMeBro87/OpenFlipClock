package org.openflipclock;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Alarm list (max 10) with an edit dialog: time, repeat days, label, sound. */
public class AlarmActivity extends Activity {
    private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private LinearLayout list;
    private Alarms.Alarm editing;
    private Button soundBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(this, 16);
        root.setPadding(p, p, p, p);
        sv.addView(root);
        root.addView(UI.button(this, "+ Add alarm", new View.OnClickListener() {
            public void onClick(View v) { addAlarm(); }
        }));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        TextView tip = UI.label(this, "Tip: on some phones, allow autostart and set battery usage for OpenFlipClock to "
                + "\"No restrictions\" so alarms always ring. Alarms use the system alarm sound by default and do not vibrate "
                + "unless you turn that on in Settings.", false);
        tip.setTextSize(12);
        tip.setPadding(0, UI.dp(this, 24), 0, 0);
        root.addView(tip);
        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
        if (Build.VERSION.SDK_INT >= 31 && !((AlarmManager) getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms()) {
            new AlertDialog.Builder(this).setTitle("Allow exact alarms")
                .setMessage("Android needs your permission to ring alarms on time.")
                .setPositiveButton("Open settings", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:" + getPackageName())));
                    }
                }).setNegativeButton("Later", null).show();
        }
        refresh();
    }

    private String timeText(Alarms.Alarm a) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, a.h);
        c.set(Calendar.MINUTE, a.m);
        boolean h24 = Prefs.i(this, "hfmt", android.text.format.DateFormat.is24HourFormat(this) ? 1 : 0) == 1;
        return new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()).format(c.getTime());
    }

    private String daysText(Alarms.Alarm a) {
        if (a.days == 0) return "Once";
        if (a.days == 127) return "Every day";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 7; i++) if (((a.days >> i) & 1) == 1) s.append(s.length() > 0 ? ", " : "").append(DAYS[i]);
        return s.toString();
    }

    private void refresh() {
        list.removeAllViews();
        for (final Alarms.Alarm a : Alarms.load(this)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, UI.dp(this, 10), 0, UI.dp(this, 10));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView t = new TextView(this);
            t.setText(timeText(a));
            t.setTextSize(32);
            t.setTextColor(a.on ? 0xFF212121 : 0xFF9E9E9E);
            TextView info = new TextView(this);
            info.setText(daysText(a) + (a.label.isEmpty() ? "" : "  ·  " + a.label));
            info.setTextSize(13);
            info.setTextColor(0xFF757575);
            left.addView(t);
            left.addView(info);
            row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup_WRAP(), 1));
            CheckBox on = new CheckBox(this);
            on.setChecked(a.on);
            on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton v, boolean isOn) {
                    a.on = isOn;
                    Alarms.put(AlarmActivity.this, a);
                    Alarms.schedule(AlarmActivity.this, a);
                    refresh();
                }
            });
            row.addView(on);
            left.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { edit(a, false); } });
            list.addView(row);
        }
    }

    private static int ViewGroup_WRAP() { return android.view.ViewGroup.LayoutParams.WRAP_CONTENT; }

    private void addAlarm() {
        List<Alarms.Alarm> l = Alarms.load(this);
        if (l.size() >= Alarms.MAX) { Toast.makeText(this, "Maximum " + Alarms.MAX + " alarms", Toast.LENGTH_SHORT).show(); return; }
        Alarms.Alarm a = new Alarms.Alarm();
        a.id = Alarms.newId(l);
        edit(a, true);
    }

    private String soundName(Alarms.Alarm a) {
        if (a.sound.isEmpty()) return "Default alarm sound";
        if (a.sound.equals("file")) return "Local file";
        try { return RingtoneManager.getRingtone(this, Uri.parse(a.sound)).getTitle(this); } catch (Exception e) { return "System sound"; }
    }

    private void edit(final Alarms.Alarm a, final boolean isNew) {
        editing = a;
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(this, 16);
        l.setPadding(p, p, p, p);
        final TimePicker tp = new TimePicker(this);
        tp.setIs24HourView(Prefs.i(this, "hfmt", android.text.format.DateFormat.is24HourFormat(this) ? 1 : 0) == 1);
        tp.setCurrentHour(a.h);
        tp.setCurrentMinute(a.m);
        l.addView(tp);
        final EditText label = new EditText(this);
        label.setHint("Label (optional)");
        label.setSingleLine(true);
        label.setText(a.label);
        l.addView(label);
        l.addView(UI.label(this, "Repeat (none selected = once)", false));
        LinearLayout dr = new LinearLayout(this);
        final CheckBox[] cb = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            cb[i] = new CheckBox(this);
            cb[i].setText(DAYS[i].substring(0, 1));
            cb[i].setChecked(((a.days >> i) & 1) == 1);
            dr.addView(cb[i], new LinearLayout.LayoutParams(0, ViewGroup_WRAP(), 1));
        }
        l.addView(dr);
        soundBtn = UI.button(this, "Sound: " + soundName(a), new View.OnClickListener() {
            public void onClick(View v) { pickSound(); }
        });
        l.addView(soundBtn);
        ScrollView sv = new ScrollView(this);
        sv.addView(l);

        AlertDialog.Builder bd = new AlertDialog.Builder(this).setTitle(isNew ? "New alarm" : "Edit alarm").setView(sv)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    a.h = tp.getCurrentHour();
                    a.m = tp.getCurrentMinute();
                    a.label = label.getText().toString().trim();
                    int days = 0;
                    for (int i = 0; i < 7; i++) if (cb[i].isChecked()) days |= 1 << i;
                    a.days = days;
                    a.on = true;
                    Alarms.put(AlarmActivity.this, a);
                    Alarms.schedule(AlarmActivity.this, a);
                    refresh();
                }
            }).setNegativeButton("Cancel", null);
        if (!isNew) bd.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                Alarms.remove(AlarmActivity.this, a.id);
                new File(getFilesDir(), "alarm_" + a.id + ".snd").delete();
                refresh();
            }
        });
        bd.show();
    }

    private void pickSound() {
        new AlertDialog.Builder(this).setTitle("Alarm sound")
            .setItems(new String[]{"Default alarm sound", "System sounds…", "Local file…"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) { editing.sound = ""; soundBtn.setText("Sound: " + soundName(editing)); }
                    else if (which == 1) {
                        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                        if (!editing.sound.isEmpty() && !editing.sound.equals("file"))
                            i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(editing.sound));
                        startActivityForResult(i, 11);
                    } else {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("audio/*");
                        startActivityForResult(Intent.createChooser(i, "Choose sound"), 12);
                    }
                }
            }).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (res != RESULT_OK || data == null || editing == null) return;
        if (req == 11) {
            Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            editing.sound = u == null ? "" : u.toString();
            if (soundBtn != null) soundBtn.setText("Sound: " + soundName(editing));
        } else if (req == 12 && data.getData() != null) {
            final Uri u = data.getData();
            final Alarms.Alarm a = editing;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        byte[] d = Img.readAll(getContentResolver().openInputStream(u));
                        java.io.FileOutputStream o = new java.io.FileOutputStream(new File(getFilesDir(), "alarm_" + a.id + ".snd"));
                        o.write(d);
                        o.close();
                        a.sound = "file";
                        runOnUiThread(new Runnable() { public void run() { if (soundBtn != null) soundBtn.setText("Sound: " + soundName(a)); } });
                    } catch (Exception e) {
                        runOnUiThread(new Runnable() { public void run() { Toast.makeText(AlarmActivity.this, "Could not import sound", Toast.LENGTH_LONG).show(); } });
                    }
                }
            }).start();
        }
    }
}
