package org.openflipclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.*;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** Clock, Pomodoro and Flowmodoro are all drawn by this one View. It redraws only when something changes. */
public class FlipClockView extends View {
    public interface Host { void openDrawer(); }

    public Host host;
    public long rev = -1;
    public boolean dream;   // screensaver mode: always the plain clock
    private static final int FLIP_MS = 420;
    private static final int B_PRESET = 1, B_START = 2, B_SKIP = 3, B_STOP = 4, B_FLOW = 5, B_FEND = 6;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp;
    private int look, pos, blurR, hfmt, mode, ovBg, ovTile, ovDigit;
    private boolean showDate, showSec, showBat, shift;
    private float tileAlpha = 1f, cornerR, sizeClock = 1f, sizeTimer = 1f;
    private String bellText = "";
    private final Path cornerPath = new Path();
    private final Paint blackP = new Paint();
    private SimpleDateFormat fmtDate, fmtDay, fmtSmall;
    private int tileColor = 0xFF212121, digitColor = 0xFFB0B0B0, hudColor = 0xFFFFFFFF, drawDigit = 0xFFB0B0B0;
    private Bitmap src, bg, blurBmp;

    private String t1Now = "", t2Now = "", t1Old = "", t2Old = "", u1 = "", u2 = "", ampm = "", sec = "",
            dateS = "", dayS = "", smallTime = "", minKey = "", stateLabel = "";
    private int stateColor = 0xFFFFFFFF, rounds;
    private boolean overrun, overrunBeeped;
    private long flipStart = 0;
    private boolean f1, f2;
    private int batPct = -1;
    private boolean charging, batRegistered, menuVisible;
    private int shiftDx, shiftDy;
    private float downX, downY;
    private boolean edge, swiped;

    private final Paint digit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tileP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imgP = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF hr = new RectF(), mr = new RectF(), full = new RectF(), menuRect = new RectF(), dstR = new RectF();
    private final Rect srcR = new Rect();
    private final RectF[] btn = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final int[] btnId = new int[4];
    private int nBtn;
    private final Path clipPath = new Path();
    private float corner, digitOff, splitGap;

    private final Runnable tickR = new Runnable() { public void run() { tick(); } };
    private final Runnable hideMenu = new Runnable() { public void run() { menuVisible = false; invalidate(); } };
    private final BroadcastReceiver batR = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            int l = i.getIntExtra("level", -1), s = i.getIntExtra("scale", 100);
            if (l >= 0 && s > 0) batPct = l * 100 / s;
            int st = i.getIntExtra("status", -1);
            charging = st == 2 || st == 5;
            invalidate();
        }
    };

    public FlipClockView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        digit.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        digit.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hudP.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnP.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        blackP.setColor(0xFF000000);
        blackP.setAntiAlias(true);
    }

    // ------------------------------------------------------------ config
    private void readPrefs() {
        Context c = getContext();
        look = Prefs.i(c, "look", 0);
        mode = Prefs.i(c, "mode", 0);
        hfmt = Prefs.i(c, "hfmt", DateFormat.is24HourFormat(c) ? 1 : 0);
        showDate = Prefs.b(c, "date", true);
        showSec = Prefs.b(c, "sec", true);
        showBat = Prefs.b(c, "bat", false);
        shift = Prefs.b(c, "shift", true);
        pos = Prefs.i(c, "pos", 0);
        tileAlpha = 1f - Prefs.i(c, "trans", 0) / 100f;
        blurR = Prefs.i(c, "blur", 8);
        ovBg = Prefs.i(c, "col_bg", 0);
        ovTile = Prefs.i(c, "col_tile", 0);
        ovDigit = Prefs.i(c, "col_digit", 0);
        try { fmtDate = new SimpleDateFormat(Prefs.s(c, "datefmt", "yyyy/MM/dd"), Locale.getDefault()); }
        catch (IllegalArgumentException e) { fmtDate = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()); }
        fmtDay = new SimpleDateFormat("EEEE", Locale.getDefault());
        fmtSmall = new SimpleDateFormat(hfmt == 1 ? "HH:mm" : "h:mm a", Locale.getDefault());
        cornerR = Prefs.i(c, "disp_radius", 0) * dp;
        sizeClock = Math.max(0.5f, Prefs.i(c, "size_clock", 100) / 100f);
        sizeTimer = Math.max(0.5f, Prefs.i(c, "size_timer", 100) / 100f);
        if (dream) mode = 0;
        minKey = "";
    }

    public void reload() {
        readPrefs();
        rev = Prefs.rev(getContext());
        final Context c = getContext().getApplicationContext();
        final int a = look;
        new Thread(new Runnable() {
            public void run() {
                Bitmap s = null;
                if (a == Prefs.A_WALLHAVEN || a == Prefs.A_LOCAL) {
                    File f = new File(c.getFilesDir(), a == Prefs.A_WALLHAVEN ? "bg_wh.jpg" : "bg_local.jpg");
                    if (f.exists()) s = Img.decodeFile(f, Img.reqLong(c));
                }
                final Bitmap fs = s;
                post(new Runnable() { public void run() { src = fs; t1Now = ""; rebuild(); tick(); } });
            }
        }).start();
    }

    public void setMode(int m) {
        mode = m;
        Prefs.put(getContext(), "mode", m);
        t1Now = "";
        overrunBeeped = false;
        doLayout(getWidth(), getHeight());
        tick();
    }

    public void start() {
        if (showBat && !batRegistered) {
            Intent i = getContext().registerReceiver(batR, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            batRegistered = true;
            if (i != null) batR.onReceive(getContext(), i);
        }
        minKey = "";
        tick();
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        if (batRegistered) {
            try { getContext().unregisterReceiver(batR); } catch (Exception ignored) { }
            batRegistered = false;
        }
    }

    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    // ------------------------------------------------------------ layout
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void doLayout(int w, int h) {
        if (w <= 0 || h <= 0) return;
        boolean land = w > h;
        float inset = cornerR * 0.29f;
        menuRect.set(8 * dp + inset, 8 * dp + inset, 56 * dp + inset, 56 * dp + inset);
        cornerPath.reset();
        if (cornerR > 0) {
            cornerPath.moveTo(0, 0);
            cornerPath.lineTo(cornerR, 0);
            cornerPath.arcTo(new RectF(0, 0, 2 * cornerR, 2 * cornerR), 270, -90);
            cornerPath.close();
        }
        float mg = Math.min(w, h) * (mode == 0 ? 0.05f : 0.035f), gap = mg;
        float left = 0, right = w, top = 0, bottom = h;
        nBtn = 0;
        if (mode != 0) {
            top = (showBat ? 64 : 56) * dp + inset * 0.5f;
            int[] ids = mode == 1 ? new int[]{B_PRESET, B_START, B_SKIP, B_STOP} : new int[]{B_FLOW, B_FEND};
            float[] wt = mode == 1 ? new float[]{1.5f, 1.2f, 1f, 1f} : new float[]{1f, 1f};
            float bh = 44 * dp, g = 8 * dp;
            if (!land) {   // buttons in a row at the bottom
                float by = h - 20 * dp - bh - inset * 0.5f, rowW = Math.min(w - 2 * mg, 520 * dp), x = (w - rowW) / 2f, sum = 0;
                bottom = by - 14 * dp;
                for (float v : wt) sum += v;
                float usable = rowW - g * (ids.length - 1);
                for (int i = 0; i < ids.length; i++) {
                    float bw = usable * wt[i] / sum;
                    btn[i].set(x, by, x + bw, by + bh);
                    btnId[i] = ids[i];
                    x += bw + g;
                }
            } else {       // landscape: buttons in a column on the right, tiles get the full height
                right = w - 170 * dp;
                float totalH = ids.length * bh + (ids.length - 1) * g;
                float y = top + Math.max(0, (h - top - mg - totalH) / 2f);
                for (int i = 0; i < ids.length; i++) {
                    btn[i].set(right + 8 * dp, y, w - 12 * dp - inset * 0.5f, y + bh);
                    btnId[i] = ids[i];
                    y += bh + g;
                }
            }
            nBtn = ids.length;
        }
        float availW = right - left, availH = bottom - top, side, scale = mode == 0 ? sizeClock : sizeTimer;
        if (!land) {
            side = Math.max(10, Math.min(availW - 2 * mg, (availH - gap - 2 * mg) / 2f)) * scale;
            float x = left + (availW - side) / 2f, y0 = top + (availH - 2 * side - gap) / 2f;
            hr.set(x, y0, x + side, y0 + side);
            mr.set(x, y0 + side + gap, x + side, y0 + 2 * side + gap);
        } else {
            side = Math.max(10, Math.min((availW - gap - 2 * mg) / 2f, availH - 2 * mg)) * scale;
            float x0 = left + (availW - 2 * side - gap) / 2f, y0 = top + (availH - side) / 2f;
            hr.set(x0, y0, x0 + side, y0 + side);
            mr.set(x0 + side + gap, y0, x0 + 2 * side + gap, y0 + side);
        }
        corner = side * 0.12f;
        splitGap = Math.max(3 * dp, side * 0.012f);
        digit.setTextSize(side * 0.62f);
        float tw = digit.measureText("88"), maxW = side * 0.86f;
        if (tw > maxW) digit.setTextSize(digit.getTextSize() * maxW / tw);
        Rect b = new Rect();
        digit.getTextBounds("8", 0, 1, b);
        digitOff = -(b.top + b.bottom) / 2f;
        small.setTextSize(side * 0.075f);
    }

    // ------------------------------------------------------------ background + colours
    private void drawImage(Canvas c, int w, int h) {
        int sw = src.getWidth(), sh = src.getHeight();
        Matrix m = new Matrix();
        switch (pos) {
            case 1: m.setScale((float) w / sw, (float) h / sh); break;           // Scaled
            case 2: {                                                              // Scaled, keep proportions
                float s = Math.min((float) w / sw, (float) h / sh);
                m.setScale(s, s); m.postTranslate((w - sw * s) / 2f, (h - sh * s) / 2f);
                c.drawColor(0xFF000000);
                break;
            }
            case 3: m.setTranslate((w - sw) / 2f, (h - sh) / 2f); c.drawColor(0xFF000000); break; // Centered
            case 4: {                                                              // Tiled
                Paint p = new Paint();
                p.setShader(new BitmapShader(src, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
                c.drawRect(0, 0, w, h, p);
                return;
            }
            default: {                                                             // Scaled and Cropped
                float s = Math.max((float) w / sw, (float) h / sh);
                m.setScale(s, s); m.postTranslate((w - sw * s) / 2f, (h - sh * s) / 2f);
            }
        }
        c.drawBitmap(src, m, imgP);
    }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        doLayout(w, h);
        full.set(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        boolean image = false;
        if (look == Prefs.A_LIGHT) c.drawColor(0xFFE6E6E6);
        else if (look == Prefs.A_CUSTOM) c.drawColor(ovBg != 0 ? ovBg : 0xFF1E2430);
        else if (src != null && (look == Prefs.A_WALLHAVEN || look == Prefs.A_LOCAL)) { drawImage(c, w, h); image = true; }
        else c.drawColor(0xFF000000);
        bg = b;

        int avg = ColorUtil.avg(b), tile, dig;
        if (look == Prefs.A_LIGHT) { tile = 0xFFFFFFFF; dig = 0xFF2B2B2B; }
        else if (look == Prefs.A_CUSTOM || image) { tile = ColorUtil.accentTile(b); dig = 0; }
        else { tile = 0xFF212121; dig = 0xFFB0B0B0; }
        if (ovTile != 0) { tile = ovTile; if (!(look == Prefs.A_CUSTOM || image)) dig = 0; }
        if (ovDigit != 0) dig = ovDigit; else if (dig == 0) dig = ColorUtil.digitFor(tile, avg, tileAlpha);
        tileColor = tile; digitColor = dig;
        hudColor = ColorUtil.lum(avg) < 0.45 ? 0xFFFFFFFF : 0xFF1A1A1A;

        blurBmp = null;
        if (tileAlpha < 0.99f && blurR > 0) {   // computed ONCE, on a ~1/6 size copy
            int sw = Math.max(8, w / 6), sh = Math.max(8, h / 6);
            Bitmap s = Bitmap.createScaledBitmap(b, sw, sh, true);
            int[] px = new int[sw * sh];
            s.getPixels(px, 0, sw, 0, 0, sw, sh);
            int r = Math.max(1, blurR / 2);
            ColorUtil.boxBlur(px, sw, sh, r);
            ColorUtil.boxBlur(px, sw, sh, r);
            Bitmap o = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            o.setPixels(px, 0, sw, 0, 0, sw, sh);
            blurBmp = o;
        }
        invalidate();
    }

    // ------------------------------------------------------------ time / timers
    private static String two(long v) { return v < 10 ? "0" + v : "" + v; }

    private void beep() {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 700);
            postDelayed(new Runnable() { public void run() { tg.release(); } }, 1200);
        } catch (Exception ignored) { }
    }

    private void tick() {
        handler.removeCallbacks(tickR);
        if (fmtDate == null) readPrefs();
        Context c = getContext();
        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();
        boolean h24 = hfmt == 1;
        int hh = cal.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!h24 && hh == 0) hh = 12;
        String hs = two(hh), ms = two(cal.get(Calendar.MINUTE)), mk = hs + ms;
        if (!mk.equals(minKey)) {
            minKey = mk;
            dateS = fmtDate.format(cal.getTime());
            dayS = fmtDay.format(cal.getTime());
            smallTime = fmtSmall.format(cal.getTime());
            bellText = Alarms.nextText(c, h24);
            if (shift) {
                int idx = (int) ((now / 180000L) % 9);
                shiftDx = (int) (((idx % 3) - 1) * 6 * dp);
                shiftDy = (int) (((idx / 3) - 1) * 6 * dp);
            } else { shiftDx = shiftDy = 0; }
        }
        ampm = h24 ? "" : (cal.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        sec = showSec ? two(cal.get(Calendar.SECOND)) : "";

        String n1, n2;
        boolean live;
        overrun = false;
        if (mode == 0) {
            n1 = hs; n2 = ms; u1 = u2 = ""; stateLabel = ""; live = showSec;
        } else {
            long v;
            boolean countdown;
            if (mode == 1) {
                if (Timers.pAdvance(c, now)) { beep(); TimerAlerts.sync(c); }
                int s = Timers.pState(c);
                boolean pz = Timers.pPaused(c);
                v = Timers.pRemaining(c, now);
                countdown = true;
                live = s != 0 && !pz;
                rounds = Prefs.i(c, "tm_round", 0);
                if (s == 0) { stateLabel = "READY"; stateColor = hudColor; }
                else if (s == 1) { stateLabel = pz ? "FOCUS · PAUSED" : "FOCUS"; stateColor = 0xFFFF8A65; }
                else { String bn = Timers.pLong(c) ? "LONG BREAK" : "BREAK"; stateLabel = pz ? bn + " · PAUSED" : bn; stateColor = 0xFF69F0AE; }
            } else {
                int s = Timers.fState(c);
                v = s == 0 ? 0 : (s == 1 ? Timers.fElapsed(c, now) : Timers.fBreakRem(c, now));
                countdown = s == 2;
                live = s != 0;
                overrun = s == 2 && v < 0;
                if (s == 0) { stateLabel = "READY"; stateColor = hudColor; }
                else if (s == 1) { stateLabel = "FLOW"; stateColor = 0xFF4FC3F7; }
                else if (overrun) { stateLabel = "BREAK OVERRUN"; stateColor = 0xFFFF5252; }
                else { stateLabel = "BREAK FLOW"; stateColor = 0xFF69F0AE; }
                if (overrun && !overrunBeeped) { overrunBeeped = true; beep(); }
                if (!overrun) overrunBeeped = false;
            }
            long secs = countdown ? (v < 0 ? (-v) / 1000 : (v + 999) / 1000) : v / 1000;
            if (secs >= 6000) { n1 = two(secs / 3600); n2 = two(secs % 3600 / 60); u1 = "HR"; u2 = "MIN"; }
            else { n1 = two(secs / 60); n2 = two(secs % 60); u1 = "MIN"; u2 = "SEC"; }
        }

        boolean first = t1Now.isEmpty();
        if (first || !n1.equals(t1Now) || !n2.equals(t2Now)) {
            f1 = !first && !n1.equals(t1Now);
            f2 = !first && !n2.equals(t2Now);
            t1Old = t1Now; t2Old = t2Now; t1Now = n1; t2Now = n2;
            if (f1 || f2) flipStart = SystemClock.uptimeMillis();
        }
        invalidate();
        long period = live ? 1000 : 60000;
        handler.postDelayed(tickR, period - (now % period) + 5);
    }

    // ------------------------------------------------------------ drawing
    private void drawText(Canvas c, String t, RectF r, int part) {
        c.save();
        if (part == 1) c.clipRect(r.left, r.top, r.right, r.centerY());
        else if (part == 2) c.clipRect(r.left, r.centerY(), r.right, r.bottom);
        digit.setColor(drawDigit);
        c.drawText(t, r.centerX(), r.centerY() + digitOff, digit);
        c.restore();
    }

    private void drawFace(Canvas c, RectF r) {
        if (blurBmp != null && tileAlpha < 0.99f) {
            c.save();
            clipPath.reset();
            clipPath.addRoundRect(r, corner, corner, Path.Direction.CW);
            c.clipPath(clipPath);
            c.translate(-shiftDx, -shiftDy);
            c.drawBitmap(blurBmp, null, full, imgP);
            c.restore();
        }
        tileP.setColor(tileColor);
        tileP.setAlpha((int) (tileAlpha * 255));
        c.drawRoundRect(r, corner, corner, tileP);
    }

    private void flap(Canvas c, RectF r, String t, boolean top, float sy) {
        if (sy <= 0.01f) return;
        float cy = r.centerY();
        c.save();
        c.scale(1, sy, r.centerX(), cy);
        if (top) c.clipRect(r.left, r.top, r.right, cy); else c.clipRect(r.left, cy, r.right, r.bottom);
        tileP.setColor(tileColor);
        tileP.setAlpha(255);
        c.drawRoundRect(r, corner, corner, tileP);
        drawText(c, t, r, 0);
        c.restore();
    }

    private void drawTile(Canvas c, RectF r, String nw, String old, float p) {
        drawFace(c, r);
        if (p >= 1f) drawText(c, nw, r, 0);
        else {
            drawText(c, nw, r, 1);
            drawText(c, old, r, 2);
            if (p < 0.5f) flap(c, r, old, true, 1 - 2 * p); else flap(c, r, nw, false, 2 * (p - 0.5f));
        }
        // real split between top and bottom half: copy the background back over a thin strip
        if (bg != null) {
            float cy = r.centerY(), g = splitGap / 2f;
            srcR.set((int) (r.left + shiftDx), (int) (cy - g + shiftDy), (int) (r.right + shiftDx), (int) (cy + g + shiftDy));
            dstR.set(r.left, cy - g, r.right, cy + g);
            c.drawBitmap(bg, srcR, dstR, null);
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (bg != null) c.drawBitmap(bg, 0, 0, null); else c.drawColor(0xFF000000);
        float p = 1f;
        if (flipStart > 0) {
            p = (SystemClock.uptimeMillis() - flipStart) / (float) FLIP_MS;
            if (p >= 1f) { p = 1f; f1 = f2 = false; flipStart = 0; }
            else postInvalidateOnAnimation();
        }
        drawDigit = overrun ? 0xFFFF6B6B : digitColor;
        c.save();
        c.translate(shiftDx, shiftDy);
        drawTile(c, hr, t1Now, t1Old, f1 ? p : 1f);
        drawTile(c, mr, t2Now, t2Old, f2 ? p : 1f);

        small.setColor((drawDigit & 0x00FFFFFF) | 0xCC000000);
        float pad = hr.height() * 0.06f, ty = hr.height() * 0.115f;
        if (mode == 0) {
            if (showDate) {
                small.setTextAlign(Paint.Align.CENTER);
                c.drawText(dateS, hr.centerX(), hr.top + ty, small);
                c.drawText(dayS, mr.centerX(), mr.top + ty, small);
            }
            small.setTextAlign(Paint.Align.LEFT);
            if (!ampm.isEmpty()) c.drawText(ampm, hr.left + pad, hr.bottom - pad, small);
            small.setTextAlign(Paint.Align.RIGHT);
            if (!sec.isEmpty()) {
                float old = small.getTextSize();
                small.setTextSize(old * 1.3f);
                c.drawText(sec, mr.right - pad, mr.bottom - pad, small);
                small.setTextSize(old);
            }
        } else {
            small.setTextAlign(Paint.Align.CENTER);
            c.drawText(u1, hr.centerX(), hr.top + ty, small);
            c.drawText(u2, mr.centerX(), mr.top + ty, small);
            if (overrun) {   // minus sign
                Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
                m.setColor(0xFFFF6B6B);
                c.drawRoundRect(new RectF(hr.left + pad, hr.bottom - pad - 5 * dp, hr.left + pad + 20 * dp, hr.bottom - pad), 2 * dp, 2 * dp, m);
            }
        }
        c.restore();

        if (showBat && batPct >= 0) drawBattery(c);
        if (mode == 0 && !bellText.isEmpty()) drawBell(c);
        if (mode != 0) drawHud(c);
        if (menuVisible || (mode != 0 && !dream)) drawMenu(c);
        drawCorners(c);
    }

    private void drawBell(Canvas c) {
        float y = 10 * dp + cornerR * 0.2f;
        hudP.setColor((hudColor & 0x00FFFFFF) | 0xCC000000);
        hudP.setTextSize(14 * dp);
        hudP.setTextAlign(Paint.Align.LEFT);
        float total = 20 * dp + hudP.measureText(bellText), x = getWidth() / 2f - total / 2f + 8 * dp;
        Path b = new Path();
        b.moveTo(x - 8 * dp, y + 13 * dp);
        b.lineTo(x - 6 * dp, y + 11 * dp);
        b.quadTo(x - 6 * dp, y, x, y);
        b.quadTo(x + 6 * dp, y, x + 6 * dp, y + 11 * dp);
        b.lineTo(x + 8 * dp, y + 13 * dp);
        b.close();
        c.drawPath(b, hudP);
        c.drawCircle(x, y + 16 * dp, 2 * dp, hudP);
        c.drawText(bellText, x + 14 * dp, y + 13 * dp, hudP);
    }

    private void drawCorners(Canvas c) {
        if (cornerR <= 0) return;
        float w = getWidth(), h = getHeight();
        c.drawPath(cornerPath, blackP);
        c.save(); c.scale(-1, 1, w / 2f, 0); c.drawPath(cornerPath, blackP); c.restore();
        c.save(); c.scale(1, -1, 0, h / 2f); c.drawPath(cornerPath, blackP); c.restore();
        c.save(); c.scale(-1, -1, w / 2f, h / 2f); c.drawPath(cornerPath, blackP); c.restore();
    }

    private void drawHud(Canvas c) {
        float inset = cornerR * 0.29f;
        hudP.setColor(hudColor);
        hudP.setTextSize(22 * dp);
        hudP.setTextAlign(Paint.Align.RIGHT);
        c.drawText(smallTime, getWidth() - 14 * dp - inset, (showBat ? 58 : 40) * dp + inset * 0.3f, hudP);
        hudP.setColor(stateColor);
        hudP.setTextSize(16 * dp);
        hudP.setLetterSpacing(0.12f);
        hudP.setTextAlign(Paint.Align.CENTER);
        c.drawText(stateLabel, getWidth() / 2f, 34 * dp + inset * 0.3f, hudP);
        hudP.setLetterSpacing(0f);
        long t = Timers.todayMs(getContext()) / 60000;
        String today = "Today " + (t >= 60 ? (t / 60) + "h " : "") + (t % 60) + "m";
        if (mode == 1 && rounds > 0) today = "Round " + rounds + "  ·  " + today;
        hudP.setColor((hudColor & 0x00FFFFFF) | 0xAA000000);
        hudP.setTextSize(12 * dp);
        c.drawText(today, getWidth() / 2f, 52 * dp + inset * 0.3f, hudP);
        for (int i = 0; i < nBtn; i++) drawButton(c, btn[i], labelFor(btnId[i]), btnId[i] == B_START || btnId[i] == B_FLOW);
    }

    private String labelFor(int id) {
        Context c = getContext();
        switch (id) {
            case B_PRESET: return Timers.focusMin(c) + " / " + Timers.breakMin(c) + " min ▲";
            case B_START: return Timers.pState(c) == 0 ? "Start" : (Timers.pPaused(c) ? "Resume" : "Pause");
            case B_SKIP: return "Skip";
            case B_STOP: return "Stop";
            case B_FLOW: return Timers.fState(c) == 1 ? "Break" : "Start";
            default: return "End Session";
        }
    }

    private void drawButton(Canvas c, RectF r, String label, boolean primary) {
        btnP.setStyle(Paint.Style.FILL);
        btnP.setColor(primary ? digitColor : tileColor);
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, btnP);
        btnP.setColor(primary ? tileColor : digitColor);
        btnP.setTextSize(15 * dp);
        btnP.setTextAlign(Paint.Align.CENTER);
        c.drawText(label, r.centerX(), r.centerY() - (btnP.ascent() + btnP.descent()) / 2f, btnP);
    }

    private void drawBattery(Canvas c) {
        float bw = 22 * dp, bh = 11 * dp, x = getWidth() - 14 * dp - bw - cornerR * 0.29f, y = 10 * dp + cornerR * 0.2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor((hudColor & 0x00FFFFFF) | 0xDD000000);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * dp);
        c.drawRoundRect(new RectF(x, y, x + bw, y + bh), 2 * dp, 2 * dp, p);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(x + bw, y + bh * .3f, x + bw + 2 * dp, y + bh * .7f, p);
        c.drawRect(x + 2.5f * dp, y + 2.5f * dp, x + 2.5f * dp + (bw - 5 * dp) * batPct / 100f, y + bh - 2.5f * dp, p);
        p.setTextSize(13 * dp);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText((charging ? "+" : "") + batPct + "%", x - 6 * dp, y + bh - 0.5f * dp, p);
    }

    private void drawMenu(Canvas c) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x99000000);
        c.drawCircle(menuRect.centerX(), menuRect.centerY(), 24 * dp, p);
        p.setColor(0xFFFFFFFF);
        for (int i = -1; i <= 1; i++)
            c.drawRect(menuRect.centerX() - 9 * dp, menuRect.centerY() + i * 6 * dp - dp,
                    menuRect.centerX() + 9 * dp, menuRect.centerY() + i * 6 * dp + dp, p);
    }

    // ------------------------------------------------------------ touch
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                edge = downX < 64 * dp; swiped = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (edge && !swiped) {
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (dx > 48 * dp && Math.abs(dx) > Math.abs(dy) * 1.5f) { swiped = true; if (host != null) host.openDrawer(); }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (swiped) return true;
                float x = e.getX(), y = e.getY();
                if ((menuVisible || mode != 0) && menuRect.contains(x, y)) { if (host != null) host.openDrawer(); return true; }
                if (mode != 0) {
                    for (int i = 0; i < nBtn; i++) if (btn[i].contains(x, y)) { onButton(btnId[i]); return true; }
                    return true;
                }
                menuVisible = !menuVisible;
                handler.removeCallbacks(hideMenu);
                if (menuVisible) handler.postDelayed(hideMenu, 4000);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void onButton(int id) {
        Context c = getContext();
        long now = System.currentTimeMillis();
        if ((id == B_START || id == B_FLOW) && Build.VERSION.SDK_INT >= 33 && c instanceof Activity
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
                && !Prefs.b(c, "tm_asked", false)) {
            Prefs.put(c, "tm_asked", true);
            ((Activity) c).requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
        switch (id) {
            case B_START: Timers.pStartPause(c, now); break;
            case B_SKIP: Timers.pSkip(c, now); break;
            case B_STOP: Timers.pStop(c, now); break;
            case B_PRESET:
                if (Timers.pState(c) != 0) Toast.makeText(c, "Stop the timer to change durations", Toast.LENGTH_SHORT).show();
                else showPresetMenu(btn[0]);
                return;
            case B_FLOW: Timers.fStartBreak(c, now); break;
            default: {
                long tot = Timers.fEnd(c, now);
                Toast.makeText(c, "Session total: " + (tot / 60000) + " min of flow", Toast.LENGTH_LONG).show();
            }
        }
        overrunBeeped = false;
        TimerAlerts.sync(c);
        tick();
    }

    /** Drop-up menu: opens above the button because the buttons sit at the bottom of the screen. */
    private void showPresetMenu(RectF anchor) {
        final Context c = getContext();
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(0xF2202020);
        bgd.setCornerRadius(12 * dp);
        l.setBackground(bgd);
        int sel = Prefs.i(c, "tm_preset", 0);
        String[] items = {"25 / 5 minutes", "50 / 10 minutes", "90 / 20 minutes",
                "Custom (" + Prefs.i(c, "tm_focus", 25) + " / " + Prefs.i(c, "tm_break", 5) + ")…"};
        final PopupWindow pw = new PopupWindow(l, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        for (int i = 0; i < items.length; i++) {
            TextView t = new TextView(c);
            t.setText(items[i]);
            t.setTextSize(16);
            t.setTextColor(i == sel ? 0xFFFFD54F : 0xFFFFFFFF);
            t.setPadding((int) (20 * dp), (int) (14 * dp), (int) (28 * dp), (int) (14 * dp));
            final int idx = i;
            t.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    pw.dismiss();
                    if (idx == 3) customDialog();
                    else { Prefs.put(c, "tm_preset", idx); tick(); }
                }
            });
            l.addView(t);
        }
        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        l.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        pw.showAtLocation(this, Gravity.NO_GRAVITY, loc[0] + (int) anchor.left, loc[1] + (int) anchor.top - l.getMeasuredHeight() - (int) (8 * dp));
    }

    private EditText num(Context c, String hint, int v) {
        EditText e = new EditText(c);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint(hint);
        e.setText(String.valueOf(v));
        return e;
    }

    private int parse(EditText e, int d) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception x) { return d; }
    }

    private void customDialog() {
        final Context c = getContext();
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * dp);
        l.setPadding(p, p, p, p);
        final EditText f = num(c, "Focus (minutes)", Prefs.i(c, "tm_focus", 25));
        final EditText b = num(c, "Rest (minutes)", Prefs.i(c, "tm_break", 5));
        l.addView(f);
        l.addView(b);
        new AlertDialog.Builder(c).setTitle("Custom focus / rest").setView(l)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Prefs.put(c, "tm_focus", Math.max(1, Math.min(240, parse(f, 25))));
                        Prefs.put(c, "tm_break", Math.max(1, Math.min(60, parse(b, 5))));
                        Prefs.put(c, "tm_preset", 3);
                        tick();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }
}
