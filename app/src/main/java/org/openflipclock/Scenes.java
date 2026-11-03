package org.openflipclock;

import android.graphics.*;
import java.util.Random;

/** Five built-in backgrounds drawn with plain Canvas shapes (no image files, ~0 KB). */
final class Scenes {
    static final String[] NAMES = {
        "Sakura Dusk (floral)", "Coral Reef (sea)", "Jellyfish Deep (sea creatures)",
        "Night Owl (animal)", "Koi & Lotus (animal + floral)"};
    static final int[] TILE = {0xFF3A1A33, 0xFF05384A, 0xFF1A1146, 0xFF1B2235, 0xFF0E3B3F};
    static final int[] DIGIT = {0xFFFFD9E6, 0xFFA8F5F0, 0xFFB9F2FF, 0xFFFFE9A8, 0xFFFFB199};

    static void draw(int id, Canvas c, int w, int h) {
        Random r = new Random(id * 77L + 5);
        switch (id) {
            case 0: sakura(c, w, h, r); break;
            case 1: reef(c, w, h, r); break;
            case 2: jellies(c, w, h, r); break;
            case 3: owl(c, w, h, r); break;
            default: koi(c, w, h, r);
        }
    }

    private static Paint P(int col) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(col);
        return p;
    }
    private static Paint stroke(int col, float wd) {
        Paint p = P(col);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(wd);
        p.setStrokeCap(Paint.Cap.ROUND);
        return p;
    }
    private static void sky(Canvas c, int w, int h, int[] cols) {
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h, cols, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }
    private static void glow(Canvas c, float x, float y, float r, int col, int w, int h) {
        Paint p = new Paint();
        p.setShader(new RadialGradient(x, y, r, col, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }
    private static void flower(Canvas c, float x, float y, float r, int n, int petal, int center) {
        Paint p = P(petal);
        for (int i = 0; i < n; i++) {
            c.save();
            c.rotate(i * 360f / n, x, y);
            c.drawOval(new RectF(x - r * 0.36f, y - r, x + r * 0.36f, y - r * 0.08f), p);
            c.restore();
        }
        c.drawCircle(x, y, r * 0.2f, P(center));
    }
    private static void fish(Canvas c, float x, float y, float len, float ang, int body, int spot) {
        c.save();
        c.rotate(ang, x, y);
        Paint b = P(body);
        c.drawOval(new RectF(x - len * .5f, y - len * .16f, x + len * .5f, y + len * .16f), b);
        Path t = new Path();
        t.moveTo(x - len * .45f, y);
        t.lineTo(x - len * .8f, y - len * .2f);
        t.quadTo(x - len * .68f, y, x - len * .8f, y + len * .2f);
        t.close();
        c.drawPath(t, b);
        Path f = new Path();
        f.moveTo(x, y + len * .13f);
        f.lineTo(x - len * .15f, y + len * .3f);
        f.lineTo(x + len * .1f, y + len * .15f);
        c.drawPath(f, b);
        c.drawOval(new RectF(x - len * .15f, y - len * .12f, x + len * .2f, y + len * .1f), P(spot));
        c.drawCircle(x + len * .38f, y - len * .06f, len * .03f, P(0xFF000000));
        c.restore();
    }

    // 1 -------------------------------------------------------------
    private static void sakura(Canvas c, int w, int h, Random r) {
        sky(c, w, h, new int[]{0xFF241437, 0xFF7A3A68, 0xFFE98FA5});
        glow(c, w * .5f, h * .85f, w * .8f, 0x77FFD6C2, w, h);
        int[] pink = {0xFFFFC2D6, 0xFFFFA7C4, 0xFFFFE1EB};
        float[][] br = {{1.05f, .05f, .8f, .15f, .5f, .04f, -.05f, .22f},
                        {1.05f, .95f, .8f, .8f, .5f, .92f, -.05f, .78f}};
        for (float[] b : br) {
            Path p = new Path();
            p.moveTo(b[0] * w, b[1] * h);
            p.cubicTo(b[2] * w, b[3] * h, b[4] * w, b[5] * h, b[6] * w, b[7] * h);
            c.drawPath(p, stroke(0xFF2A1425, w * .02f));
            PathMeasure pm = new PathMeasure(p, false);
            float[] q = new float[2];
            for (int i = 0; i < 18; i++) {
                pm.getPosTan(pm.getLength() * i / 17f, q, null);
                flower(c, q[0] + (r.nextFloat() - .5f) * w * .07f, q[1] + (r.nextFloat() - .5f) * w * .07f,
                        w * (.03f + r.nextFloat() * .04f), 5, pink[r.nextInt(3)], 0xFFF7D26A);
            }
        }
        Paint pt = P(0xAAFFC2D6);
        for (int i = 0; i < 40; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = w * (.008f + r.nextFloat() * .012f);
            c.save();
            c.rotate(r.nextInt(360), x, y);
            c.drawOval(new RectF(x - s, y - s * .55f, x + s, y + s * .55f), pt);
            c.restore();
        }
    }

    // 2 -------------------------------------------------------------
    private static void coral(Canvas c, float x, float y, float len, float ang, int depth, Paint p) {
        if (depth == 0) return;
        float ex = x + (float) Math.cos(Math.toRadians(ang)) * len;
        float ey = y + (float) Math.sin(Math.toRadians(ang)) * len;
        p.setStrokeWidth(len * .22f);
        c.drawLine(x, y, ex, ey, p);
        coral(c, ex, ey, len * .72f, ang - 28, depth - 1, p);
        coral(c, ex, ey, len * .72f, ang + 28, depth - 1, p);
    }

    private static void reef(Canvas c, int w, int h, Random r) {
        sky(c, w, h, new int[]{0xFF0F7A8A, 0xFF08435A, 0xFF021A2A});
        Paint ray = P(0x14FFFFFF);
        for (int i = 0; i < 5; i++) {
            float x = w * (.05f + i * .22f);
            Path p = new Path();
            p.moveTo(x, 0); p.lineTo(x + w * .1f, 0); p.lineTo(x + w * .35f, h * .8f); p.lineTo(x + w * .1f, h * .8f);
            c.drawPath(p, ray);
        }
        c.drawRect(0, h * .93f, w, h, P(0xFF123A40));
        int[] cc = {0xFFFF6F61, 0xFFFFA45B, 0xFFE75B9C, 0xFFFFD166};
        for (int i = 0; i < 9; i++) {
            Paint p = stroke(cc[i % 4], 4);
            coral(c, w * (i + .5f) / 9, h * .95f, h * (.045f + r.nextFloat() * .03f), -90 + (r.nextFloat() - .5f) * 30, 5, p);
        }
        for (int i = 0; i < 7; i++)
            fish(c, r.nextFloat() * w, h * (.15f + r.nextFloat() * .65f), w * (.06f + r.nextFloat() * .04f),
                    r.nextBoolean() ? 0 : 180, i % 2 == 0 ? 0xFFFFB347 : 0xFFFF7E8B, 0xFFFFFFFF);
        Paint b = stroke(0x55FFFFFF, 3);
        for (int i = 0; i < 25; i++) c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, w * (.004f + r.nextFloat() * .012f), b);
    }

    // 3 -------------------------------------------------------------
    private static void jelly(Canvas c, float x, float y, float rad, int col) {
        int rgb = col & 0x00FFFFFF;
        Paint t = stroke(rgb | 0x88000000, rad * .06f);
        for (int k = 0; k < 7; k++) {
            float tx = x - rad * .8f + k * rad * 1.6f / 6;
            Path p = new Path();
            p.moveTo(tx, y);
            p.cubicTo(tx + rad * .3f, y + rad * 1.2f, tx - rad * .3f, y + rad * 2f, tx + rad * .1f, y + rad * 3f);
            c.drawPath(p, t);
        }
        Path b = new Path();
        b.moveTo(x - rad, y);
        b.arcTo(new RectF(x - rad, y - rad, x + rad, y + rad), 180, 180);
        b.quadTo(x, y + rad * .45f, x - rad, y);
        b.close();
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setShader(new RadialGradient(x, y - rad * .3f, rad * 1.2f, rgb | 0xEE000000, rgb | 0x55000000, Shader.TileMode.CLAMP));
        c.drawPath(b, f);
    }

    private static void jellies(Canvas c, int w, int h, Random r) {
        sky(c, w, h, new int[]{0xFF090624, 0xFF1C1260, 0xFF0B3A6B});
        Paint d = P(0x55BFEFFF);
        for (int i = 0; i < 70; i++) c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, w * (.002f + r.nextFloat() * .004f), d);
        jelly(c, w * .25f, h * .18f, w * .14f, 0xFFFF8AD8);
        jelly(c, w * .75f, h * .42f, w * .2f, 0xFF8AD8FF);
        jelly(c, w * .3f, h * .72f, w * .12f, 0xFFB59BFF);
        jelly(c, w * .85f, h * .85f, w * .08f, 0xFFFFC38A);
    }

    // 4 -------------------------------------------------------------
    private static void owl(Canvas c, int w, int h, Random r) {
        sky(c, w, h, new int[]{0xFF0B1230, 0xFF1E2A5A, 0xFF3A3F72});
        Paint st = P(0xCCFFFFFF);
        for (int i = 0; i < 60; i++) c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * .7f, w * (.0015f + r.nextFloat() * .003f), st);
        float mx = w * .68f, my = h * .3f;
        glow(c, mx, my, w * .5f, 0x55FFF1C4, w, h);
        c.drawCircle(mx, my, w * .17f, P(0xFFFFF1C4));
        Paint cr = P(0x22B89E5B);
        c.drawCircle(mx - w * .05f, my - w * .03f, w * .03f, cr);
        c.drawCircle(mx + w * .06f, my + w * .05f, w * .04f, cr);
        Paint tr = P(0xFF0A1024);
        for (int i = 0; i < 9; i++) {
            float x = w * (i + .5f) / 9, th = h * (.08f + r.nextFloat() * .07f), tw = w * .09f;
            Path p = new Path();
            p.moveTo(x, h - th); p.lineTo(x + tw, h); p.lineTo(x - tw, h); p.close();
            c.drawPath(p, tr);
        }
        c.drawLine(w * .3f, h * .46f, w * 1.02f, h * .43f, stroke(0xFF1A1420, w * .03f));
        float s = w * .1f, x = w * .68f, y = h * .44f;
        Paint body = P(0xFF2D2430);
        c.drawOval(new RectF(x - s * .9f, y - s * 2f, x + s * .9f, y), body);
        c.drawOval(new RectF(x - s * .55f, y - s * 1.5f, x + s * .55f, y - s * .1f), P(0xFF4A3B45));
        c.drawCircle(x, y - s * 2.1f, s * .85f, body);
        Path ear = new Path();
        ear.moveTo(x - s * .85f, y - s * 2.3f); ear.lineTo(x - s * .7f, y - s * 3.1f); ear.lineTo(x - s * .2f, y - s * 2.8f); ear.close();
        c.drawPath(ear, body);
        ear = new Path();
        ear.moveTo(x + s * .85f, y - s * 2.3f); ear.lineTo(x + s * .7f, y - s * 3.1f); ear.lineTo(x + s * .2f, y - s * 2.8f); ear.close();
        c.drawPath(ear, body);
        for (int k = -1; k <= 1; k += 2) {
            c.drawCircle(x + k * s * .38f, y - s * 2.15f, s * .3f, P(0xFFFFD84D));
            c.drawCircle(x + k * s * .38f, y - s * 2.15f, s * .13f, P(0xFF000000));
        }
        Path beak = new Path();
        beak.moveTo(x - s * .1f, y - s * 1.95f); beak.lineTo(x + s * .1f, y - s * 1.95f); beak.lineTo(x, y - s * 1.7f); beak.close();
        c.drawPath(beak, P(0xFFE89B3C));
    }

    // 5 -------------------------------------------------------------
    private static void koi(Canvas c, int w, int h, Random r) {
        sky(c, w, h, new int[]{0xFF1D6A6B, 0xFF0E4B52, 0xFF06262E});
        Paint rp = stroke(0x22FFFFFF, 3);
        for (int i = 0; i < 6; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            for (int k = 1; k <= 3; k++) c.drawCircle(x, y, w * .04f * k, rp);
        }
        fish(c, w * .3f, h * .22f, w * .26f, 20, 0xFFFF7A3C, 0xFFFFFFFF);
        fish(c, w * .7f, h * .55f, w * .3f, 200, 0xFFFFFFFF, 0xFFE8432E);
        fish(c, w * .35f, h * .8f, w * .22f, -30, 0xFFE8432E, 0xFF222222);
        Paint pad = P(0xFF2E8B57), pad2 = P(0xFF3FA66B);
        for (int i = 0; i < 9; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = w * (.05f + r.nextFloat() * .06f);
            c.drawOval(new RectF(x - rad, y - rad * .8f, x + rad, y + rad * .8f), i % 2 == 0 ? pad : pad2);
        }
        float[][] lp = {{.18f, .42f, .08f}, {.82f, .18f, .07f}, {.6f, .9f, .09f}, {.12f, .92f, .06f}};
        for (float[] l : lp) {
            flower(c, l[0] * w, l[1] * h, w * l[2], 8, 0xFFFFB6CE, 0xFFFFE08A);
            flower(c, l[0] * w, l[1] * h, w * l[2] * .6f, 8, 0xFFFFE1EB, 0xFFFFD166);
        }
    }
}
