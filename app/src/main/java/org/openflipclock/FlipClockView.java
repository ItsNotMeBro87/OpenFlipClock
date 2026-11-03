package org.openflipclock;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.format.DateFormat;
import android.view.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** The whole clock is this one View. It redraws only when something changes. */
public class FlipClockView extends View {
    public interface Host { void openSettings(); }

    public Host host;
    public long rev = -1;
    private static final int FLIP_MS = 420;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp;
    private int appearance, pos, blurR, hfmt;
    private boolean showDate, showSec, showBat, shift;
    private float tileAlpha = 1f;
    private SimpleDateFormat fmtDate, fmtDay;
    private int tileColor = 0xFF212121, digitColor = 0xFFB0B0B0;
    private Bitmap src, bg, blurBmp;

    private String hNow = "", mNow = "", hOld = "", mOld = "", ampm = "", sec = "", dateS = "", dayS = "";
    private long flipStart = 0;
    private boolean hFlip, mFlip;
    private int batPct = -1;
    private boolean charging, batRegistered, menuVisible;
    private int shiftDx, shiftDy;

    private final Paint digit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tileP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint();
    private final Paint imgP = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF hr = new RectF(), mr = new RectF(), full = new RectF(), menuRect = new RectF();
    private final Path clipPath = new Path();
    private float corner, digitOff;

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
        linePaint.setColor(0xCC000000);
        menuRect.set(8 * dp, 8 * dp, 56 * dp, 56 * dp);
    }

    // ------------------------------------------------------------ config
    private void readPrefs() {
        Context c = getContext();
        appearance = Prefs.i(c, "appearance", 0);
        hfmt = Prefs.i(c, "hfmt", DateFormat.is24HourFormat(c) ? 1 : 0);
        showDate = Prefs.b(c, "date", true);
        showSec = Prefs.b(c, "sec", true);
        showBat = Prefs.b(c, "bat", false);
        shift = Prefs.b(c, "shift", true);
        pos = Prefs.i(c, "pos", 0);
        tileAlpha = 1f - Prefs.i(c, "trans", 0) / 100f;
        blurR = Prefs.i(c, "blur", 8);
        try { fmtDate = new SimpleDateFormat(Prefs.s(c, "datefmt", "yyyy/MM/dd"), Locale.getDefault()); }
        catch (IllegalArgumentException e) { fmtDate = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()); }
        fmtDay = new SimpleDateFormat("EEEE", Locale.getDefault());
    }

    public void reload() {
        readPrefs();
        rev = Prefs.rev(getContext());
        final Context c = getContext().getApplicationContext();
        final int a = appearance;
        new Thread(new Runnable() {
            public void run() {
                Bitmap s = null;
                if (a == Prefs.A_WALLHAVEN || a == Prefs.A_LOCAL) {
                    File f = new File(c.getFilesDir(), a == Prefs.A_WALLHAVEN ? "bg_wh.jpg" : "bg_local.jpg");
                    if (f.exists()) s = Img.decodeFile(f, Img.reqLong(c));
                }
                final Bitmap fs = s;
                post(new Runnable() { public void run() { src = fs; hNow = ""; rebuild(); tick(); } });
            }
        }).start();
    }

    public void start() {
        if (showBat && !batRegistered) {
            Intent i = getContext().registerReceiver(batR, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            batRegistered = true;
            if (i != null) batR.onReceive(getContext(), i);
        }
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

    // ------------------------------------------------------------ background + colours
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void layout(int w, int h) {
        boolean land = w > h;
        float mg = Math.min(w, h) * 0.06f;
        if (!land) {
            float tw = w - 2 * mg, th = Math.min((h - 3 * mg) / 2f, tw * 1.15f), top = (h - 2 * th - mg) / 2f;
            hr.set(mg, top, mg + tw, top + th);
            mr.set(mg, top + th + mg, mg + tw, top + 2 * th + mg);
        } else {
            float th = h - 2 * mg, tw = Math.min((w - 3 * mg) / 2f, th * 1.15f), left = (w - 2 * tw - mg) / 2f;
            hr.set(left, mg, left + tw, mg + th);
            mr.set(left + tw + mg, mg, left + 2 * tw + mg, mg + th);
        }
        corner = Math.min(hr.width(), hr.height()) * 0.12f;
        digit.setTextSize(hr.height() * 0.62f);
        float tw = digit.measureText("88"), maxW = hr.width() * 0.86f;
        if (tw > maxW) digit.setTextSize(digit.getTextSize() * maxW / tw);
        Rect b = new Rect();
        digit.getTextBounds("8", 0, 1, b);
        digitOff = -(b.top + b.bottom) / 2f;
        small.setTextSize(hr.height() * 0.075f);
    }

    private void drawImage(Canvas c, int w, int h) {
        int sw = src.getWidth(), sh = src.getHeight();
        Matrix m = new Matrix();
        switch (pos) {
            case 1: // Scaled (stretch)
                m.setScale((float) w / sw, (float) h / sh);
                break;
            case 2: { // Scaled, keep proportions
                float s = Math.min((float) w / sw, (float) h / sh);
                m.setScale(s, s); m.postTranslate((w - sw * s) / 2f, (h - sh * s) / 2f);
                c.drawColor(0xFF000000);
                break;
            }
            case 3: // Centered
                m.setTranslate((w - sw) / 2f, (h - sh) / 2f);
                c.drawColor(0xFF000000);
                break;
            case 4: { // Tiled
                Paint p = new Paint();
                p.setShader(new BitmapShader(src, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
                c.drawRect(0, 0, w, h, p);
                return;
            }
            default: { // Scaled and Cropped (fill)
                float s = Math.max((float) w / sw, (float) h / sh);
                m.setScale(s, s); m.postTranslate((w - sw * s) / 2f, (h - sh * s) / 2f);
            }
        }
        c.drawBitmap(src, m, imgP);
    }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        layout(w, h);
        full.set(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        boolean image = false;
        if (appearance == Prefs.A_LIGHT) c.drawColor(0xFFE6E6E6);
        else if (appearance >= Prefs.A_PRESET0 && appearance < Prefs.A_WALLHAVEN) Scenes.draw(appearance - Prefs.A_PRESET0, c, w, h);
        else if (src != null && (appearance == Prefs.A_WALLHAVEN || appearance == Prefs.A_LOCAL)) { drawImage(c, w, h); image = true; }
        else c.drawColor(0xFF000000);
        bg = b;

        if (appearance == Prefs.A_LIGHT) { tileColor = 0xFFFFFFFF; digitColor = 0xFF2B2B2B; }
        else if (appearance >= Prefs.A_PRESET0 && appearance < Prefs.A_WALLHAVEN) {
            tileColor = Scenes.TILE[appearance - Prefs.A_PRESET0];
            digitColor = Scenes.DIGIT[appearance - Prefs.A_PRESET0];
        } else if (image) {
            int[] cc = ColorUtil.adapt(b, tileAlpha);
            tileColor = cc[0]; digitColor = cc[1];
        } else { tileColor = 0xFF212121; digitColor = 0xFFB0B0B0; }

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

    // ------------------------------------------------------------ time
    private static String two(int v) { return v < 10 ? "0" + v : "" + v; }

    private void tick() {
        handler.removeCallbacks(tickR);
        if (fmtDate == null) readPrefs();
        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();
        boolean h24 = hfmt == 1;
        int hh = cal.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!h24 && hh == 0) hh = 12;
        String hs = two(hh), ms = two(cal.get(Calendar.MINUTE));
        boolean first = hNow.isEmpty();
        if (first || !hs.equals(hNow) || !ms.equals(mNow)) {
            hFlip = !first && !hs.equals(hNow);
            mFlip = !first && !ms.equals(mNow);
            hOld = hNow; mOld = mNow; hNow = hs; mNow = ms;
            if (hFlip || mFlip) flipStart = SystemClock.uptimeMillis();
            dateS = fmtDate.format(cal.getTime());
            dayS = fmtDay.format(cal.getTime());
            if (shift) {
                int idx = (int) ((now / 180000L) % 9);
                shiftDx = (int) (((idx % 3) - 1) * 6 * dp);
                shiftDy = (int) (((idx / 3) - 1) * 6 * dp);
            } else { shiftDx = shiftDy = 0; }
        }
        ampm = h24 ? "" : (cal.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        sec = showSec ? two(cal.get(Calendar.SECOND)) : "";
        invalidate();
        long period = showSec ? 1000 : 60000;
        handler.postDelayed(tickR, period - (now % period) + 5);
    }

    // ------------------------------------------------------------ drawing
    private void drawText(Canvas c, String t, RectF r, int mode) {
        c.save();
        if (mode == 1) c.clipRect(r.left, r.top, r.right, r.centerY());
        else if (mode == 2) c.clipRect(r.left, r.centerY(), r.right, r.bottom);
        digit.setColor(digitColor);
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
        float sw = Math.max(2f, 1.5f * dp), cy = r.centerY();
        c.drawRect(r.left, cy - sw / 2, r.right, cy + sw / 2, linePaint);
    }

    @Override protected void onDraw(Canvas c) {
        if (bg != null) c.drawBitmap(bg, 0, 0, null); else c.drawColor(0xFF000000);
        float p = 1f;
        if (flipStart > 0) {
            p = (SystemClock.uptimeMillis() - flipStart) / (float) FLIP_MS;
            if (p >= 1f) { p = 1f; hFlip = mFlip = false; flipStart = 0; }
            else postInvalidateOnAnimation();
        }
        c.save();
        c.translate(shiftDx, shiftDy);
        drawTile(c, hr, hNow, hOld, hFlip ? p : 1f);
        drawTile(c, mr, mNow, mOld, mFlip ? p : 1f);

        small.setColor((digitColor & 0x00FFFFFF) | 0xCC000000);
        float pad = hr.height() * 0.06f, ty = hr.height() * 0.115f;
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
        c.restore();

        if (showBat && batPct >= 0) drawBattery(c);
        if (menuVisible) drawMenu(c);
    }

    private void drawBattery(Canvas c) {
        float bw = 22 * dp, bh = 11 * dp, x = getWidth() - 14 * dp - bw, y = 12 * dp;
        int col = (digitColor & 0x00FFFFFF) | 0xDD000000;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(col);
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
            c.drawRect(menuRect.centerX() - 9 * dp, menuRect.centerY() + i * 6 * dp - 1 * dp,
                    menuRect.centerX() + 9 * dp, menuRect.centerY() + i * 6 * dp + 1 * dp, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (menuVisible && menuRect.contains(e.getX(), e.getY())) {
                if (host != null) host.openSettings();
                return true;
            }
            menuVisible = !menuVisible;
            handler.removeCallbacks(hideMenu);
            if (menuVisible) handler.postDelayed(hideMenu, 4000);
            invalidate();
        }
        return true;
    }
}
