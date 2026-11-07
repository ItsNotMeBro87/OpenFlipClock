package org.openflipclock;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.*;
import java.io.File;

/** Rings an alarm: sound on the alarm stream, optional vibration, notification with Snooze / Dismiss. */
public class AlarmService extends Service {
    static final String A_START = "start", A_SNOOZE = "snooze", A_DISMISS = "dismiss";
    private MediaPlayer mp;
    private Vibrator vib;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int id;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        String act = i == null ? A_DISMISS : i.getAction();
        id = i == null ? 0 : i.getIntExtra("id", 0);
        if (A_SNOOZE.equals(act)) {
            Alarms.snooze(this, id, new int[]{5, 10, 15}[Math.min(2, Prefs.i(this, "al_snz", 1))]);
            finishRing();
        } else if (A_DISMISS.equals(act)) {
            Alarms.cancelSnooze(this, id);
            finishRing();
        } else {
            startRinging();
        }
        return START_NOT_STICKY;
    }

    private PendingIntent action(String a, int code) {
        Intent i = new Intent(this, AlarmService.class).setAction(a).putExtra("id", id);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return Build.VERSION.SDK_INT >= 26 ? PendingIntent.getForegroundService(this, code, i, f) : PendingIntent.getService(this, code, i, f);
    }

    private void startRinging() {
        Alarms.Alarm a = Alarms.get(this, id);
        if (a == null) a = new Alarms.Alarm();
        Notif.channel(this, "alarm", "Alarms", NotificationManager.IMPORTANCE_HIGH, true);
        Intent ring = new Intent(this, AlarmRingActivity.class).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent full = PendingIntent.getActivity(this, 0, ring, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Notif.builder(this, "alarm")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(a.label.isEmpty() ? "Alarm" : a.label)
                .setContentText("Tap to open")
                .setOngoing(true)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .addAction(0, "Snooze", action(A_SNOOZE, 1))
                .addAction(0, "Dismiss", action(A_DISMISS, 2));
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MAX);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(7, n);
        play(a);
        if (Prefs.b(this, "al_vib", false)) {
            vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) vib.vibrate(new long[]{0, 600, 600}, 0);
        }
        h.removeCallbacksAndMessages(null);
        h.postDelayed(new Runnable() { public void run() { finishRing(); } }, 5 * 60000L);   // auto-stop after 5 min
    }

    private void play(Alarms.Alarm a) {
        releasePlayer();
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            if ("file".equals(a.sound)) mp.setDataSource(new File(getFilesDir(), "alarm_" + a.id + ".snd").getPath());
            else mp.setDataSource(this, a.sound.isEmpty() ? defaultUri() : Uri.parse(a.sound));
            mp.setLooping(true);
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            releasePlayer();
            try {   // fall back to the default sound
                mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                mp.setDataSource(this, defaultUri());
                mp.setLooping(true);
                mp.prepare();
                mp.start();
            } catch (Exception ignored) { releasePlayer(); }
        }
    }

    private Uri defaultUri() {
        Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return u != null ? u : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }

    private void releasePlayer() {
        if (mp != null) { try { mp.stop(); } catch (Exception ignored) { } mp.release(); mp = null; }
    }

    private void finishRing() {
        h.removeCallbacksAndMessages(null);
        releasePlayer();
        if (vib != null) vib.cancel();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() { releasePlayer(); super.onDestroy(); }
}
