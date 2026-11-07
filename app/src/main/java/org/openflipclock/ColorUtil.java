package org.openflipclock;

import android.graphics.Bitmap;
import android.graphics.Color;

final class ColorUtil {
    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
    static double lum(int c) {
        return 0.2126 * lin((c >> 16) & 255) + 0.7152 * lin((c >> 8) & 255) + 0.0722 * lin(c & 255);
    }
    static double contrast(int a, int b) {
        double l1 = lum(a), l2 = lum(b);
        if (l1 < l2) { double t = l1; l1 = l2; l2 = t; }
        return (l1 + 0.05) / (l2 + 0.05);
    }
    static int blend(int top, int bottom, float a) {
        int r = (int) (((top >> 16) & 255) * a + ((bottom >> 16) & 255) * (1 - a));
        int g = (int) (((top >> 8) & 255) * a + ((bottom >> 8) & 255) * (1 - a));
        int b = (int) ((top & 255) * a + (bottom & 255) * (1 - a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    private static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[]{h, s, v});
    }

    /** Average colour of a picture (16x16 sample). */
    static int avg(Bitmap bg) {
        Bitmap s = Bitmap.createScaledBitmap(bg, 16, 16, true);
        int[] px = new int[256];
        s.getPixels(px, 0, 16, 0, 0, 16, 16);
        long R = 0, G = 0, B = 0;
        for (int p : px) { R += (p >> 16) & 255; G += (p >> 8) & 255; B += p & 255; }
        return 0xFF000000 | ((int) (R / 256) << 16) | ((int) (G / 256) << 8) | (int) (B / 256);
    }

    /** Dark shade of the picture's dominant hue, used for the flipboard in automatic mode. */
    static int accentTile(Bitmap bg) {
        int sw = 32, sh = Math.max(1, 32 * bg.getHeight() / bg.getWidth());
        Bitmap s = Bitmap.createScaledBitmap(bg, sw, sh, true);
        int[] px = new int[sw * sh];
        s.getPixels(px, 0, sw, 0, 0, sw, sh);
        double[] wt = new double[24], sat = new double[24];
        float[] hsv = new float[3];
        for (int p : px) {
            Color.colorToHSV(p, hsv);
            double w = hsv[1] * hsv[2];
            int k = ((int) (hsv[0] / 15f)) % 24;
            wt[k] += w; sat[k] += w * hsv[1];
        }
        int best = 0;
        for (int k = 1; k < 24; k++) if (wt[k] > wt[best]) best = k;
        float hue = 215f, sv = 0.3f;
        if (wt[best] > px.length * 0.02) { hue = best * 15f + 7.5f; sv = (float) (sat[best] / wt[best]); }
        return hsv(hue, Math.min(0.65f, Math.max(0.25f, sv * 0.8f)), 0.22f);
    }

    /** Brightest readable colour: the flipboard's own hue, tinted near-white (or near-black on light boards). */
    static int digitFor(int tile, int avg, float tileAlpha) {
        float[] t = new float[3];
        Color.colorToHSV(tile, t);
        float tint = t[1] > 0.08f ? 0.22f : 0f;
        int eff = blend(tile, avg, tileAlpha);
        int[] light = {hsv(t[0], tint, 1f), hsv(t[0], tint * 0.5f, 1f), 0xFFFFFFFF};
        int[] dark = {hsv(t[0], Math.min(0.6f, tint * 2.5f), 0.12f), 0xFF000000};
        int[][] order = lum(eff) < 0.35 ? new int[][]{light, dark} : new int[][]{dark, light};
        int pick = 0xFFFFFFFF; double bestC = 0;
        for (int[] set : order) for (int c : set) {
            double cr = contrast(c, eff);
            if (cr >= 4.5) return c;
            if (cr > bestC) { bestC = cr; pick = c; }
        }
        return pick;
    }

    // ---- fast box blur on int[] ARGB pixels (small downscaled copy only) ----
    private static int cl(int v, int max) { return v < 0 ? 0 : (v > max ? max : v); }

    private static void pass(int[] s, int[] d, int len, int lines, int stride, int lineStep, int r) {
        int n = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int base = l * lineStep, sr = 0, sg = 0, sb = 0;
            for (int k = -r; k <= r; k++) {
                int p = s[base + cl(k, len - 1) * stride];
                sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
            }
            for (int i = 0; i < len; i++) {
                d[base + i * stride] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
                int a = s[base + cl(i + r + 1, len - 1) * stride];
                int m = s[base + cl(i - r, len - 1) * stride];
                sr += ((a >> 16) & 255) - ((m >> 16) & 255);
                sg += ((a >> 8) & 255) - ((m >> 8) & 255);
                sb += (a & 255) - (m & 255);
            }
        }
    }

    static void boxBlur(int[] px, int w, int h, int r) {
        int[] t = new int[px.length];
        pass(px, t, w, h, 1, w, r);
        pass(t, px, h, w, w, 1, r);
    }
}
