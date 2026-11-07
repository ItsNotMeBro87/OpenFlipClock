package org.openflipclock;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * HSV colour wheel + brightness slider + preview. In landscape the wheel sits beside the controls so
 * everything is visible at once; the dialog around it also scrolls. Touches outside the wheel scroll the dialog.
 */
final class ColorPicker extends LinearLayout {
    private float hue = 210f, sat = 0.5f, val = 1f;
    private final View wheel, preview;
    private final TextView hex;

    ColorPicker(Context c, int init) {
        super(c);
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        boolean land = dm.widthPixels > dm.heightPixels;
        setOrientation(land ? HORIZONTAL : VERTICAL);
        if (init != 0) {
            float[] h = new float[3];
            Color.colorToHSV(init, h);
            hue = h[0]; sat = h[1]; val = h[2];
        }
        int ws = land ? Math.min(UI.dp(c, 230), (int) (dm.heightPixels * 0.6f)) : Math.min(UI.dp(c, 270), (int) (dm.heightPixels * 0.42f));
        wheel = new Wheel(c);
        addView(wheel, new LayoutParams(ws, ws));

        LinearLayout side = new LinearLayout(c);
        side.setOrientation(VERTICAL);
        side.setPadding(land ? UI.dp(c, 16) : 0, UI.dp(c, 8), 0, 0);
        TextView lab = new TextView(c);
        lab.setText("Brightness");
        side.addView(lab);
        SeekBar sb = new SeekBar(c);
        sb.setMax(100);
        sb.setProgress((int) (val * 100));
        side.addView(sb);
        preview = new View(c);
        LayoutParams pl = new LayoutParams(LayoutParams.MATCH_PARENT, UI.dp(c, 40));
        pl.topMargin = UI.dp(c, 12);
        side.addView(preview, pl);
        hex = new TextView(c);
        hex.setPadding(0, UI.dp(c, 6), 0, 0);
        side.addView(hex);
        addView(side, land ? new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f) : new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { val = p / 100f; refresh(); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        refresh();
    }

    int getColor() { return Color.HSVToColor(new float[]{hue, sat, val}); }

    private void refresh() {
        int col = getColor();
        preview.setBackgroundColor(col);
        hex.setText(String.format("#%06X", col & 0xFFFFFF));
        wheel.invalidate();
    }

    private final class Wheel extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float cx, cy, r;
        private boolean dragging;

        Wheel(Context c) { super(c); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            cx = w / 2f; cy = h / 2f; r = Math.min(w, h) / 2f - 12 * getResources().getDisplayMetrics().density;
        }

        @Override protected void onDraw(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new SweepGradient(cx, cy, new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000}, null));
            c.drawCircle(cx, cy, r, p);
            p.setShader(new RadialGradient(cx, cy, r, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);
            p.setColor(Color.argb((int) ((1 - val) * 255), 0, 0, 0));
            c.drawCircle(cx, cy, r, p);
            float mx = cx + (float) Math.cos(Math.toRadians(hue)) * sat * r, my = cy + (float) Math.sin(Math.toRadians(hue)) * sat * r;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            p.setColor(0xFF000000);
            c.drawCircle(mx, my, 16, p);
            p.setStrokeWidth(2);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(mx, my, 16, p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float dx = e.getX() - cx, dy = e.getY() - cy, dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                dragging = dist <= r + 8 * getResources().getDisplayMetrics().density;
                if (!dragging) return false;   // outside the circle: let the dialog scroll
            }
            if (!dragging) return false;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            sat = Math.min(1f, dist / r);
            hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
            refresh();
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) dragging = false;
            return true;
        }
    }
}
