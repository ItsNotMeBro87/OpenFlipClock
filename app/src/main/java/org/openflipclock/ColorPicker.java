package org.openflipclock;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;

/** Small HSV colour wheel (hue around, saturation outwards) plus a brightness slider. No libraries. */
final class ColorPicker extends LinearLayout {
    private float hue = 210f, sat = 0.5f, val = 1f;
    private final View wheel, preview;

    ColorPicker(Context c, int init) {
        super(c);
        setOrientation(VERTICAL);
        if (init != 0) {
            float[] h = new float[3];
            Color.colorToHSV(init, h);
            hue = h[0]; sat = h[1]; val = h[2];
        }
        wheel = new Wheel(c);
        addView(wheel, new LayoutParams(LayoutParams.MATCH_PARENT, Math.min(UI.dp(c, 260), (int) (c.getResources().getDisplayMetrics().heightPixels * 0.5f))));
        SeekBar sb = new SeekBar(c);
        sb.setMax(100);
        sb.setProgress((int) (val * 100));
        addView(sb);
        preview = new View(c);
        addView(preview, new LayoutParams(LayoutParams.MATCH_PARENT, UI.dp(c, 36)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { val = p / 100f; refresh(); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        refresh();
    }

    int getColor() { return Color.HSVToColor(new float[]{hue, sat, val}); }

    private void refresh() {
        preview.setBackgroundColor(getColor());
        wheel.invalidate();
    }

    private final class Wheel extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float cx, cy, r;

        Wheel(Context c) { super(c); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            cx = w / 2f; cy = h / 2f; r = Math.min(w, h) / 2f - 10 * getResources().getDisplayMetrics().density;
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
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            float dx = e.getX() - cx, dy = e.getY() - cy;
            sat = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / r);
            hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
            refresh();
            return true;
        }
    }
}
