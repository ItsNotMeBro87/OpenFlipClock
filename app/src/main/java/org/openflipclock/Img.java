package org.openflipclock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import java.io.*;

final class Img {
    static int reqLong(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return Math.max(m.widthPixels, m.heightPixels);
    }

    private static int sample(BitmapFactory.Options o, int reqLong) {
        int s = 1, lg = Math.max(o.outWidth, o.outHeight);
        while (lg / (s * 2) >= reqLong) s *= 2;
        return s;
    }

    static Bitmap decode(byte[] d, int reqLong) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, o);
        o = new BitmapFactory.Options();
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, b);
        o.inSampleSize = sample(b, reqLong);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(d, 0, d.length, o);
    }

    static Bitmap decodeFile(File f, int reqLong) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), b);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample(b, reqLong);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    /** Downscales to screen size and replaces the previous file, so nothing accumulates. */
    static void importBytes(Context c, byte[] d, String name) throws IOException {
        Bitmap b = decode(d, reqLong(c));
        if (b == null) throw new IOException("Not a valid image");
        File dst = new File(c.getFilesDir(), name);
        File tmp = new File(c.getFilesDir(), name + ".tmp");
        FileOutputStream o = new FileOutputStream(tmp);
        b.compress(Bitmap.CompressFormat.JPEG, 88, o);
        o.close();
        b.recycle();
        dst.delete();
        if (!tmp.renameTo(dst)) throw new IOException("Could not save image");
    }
}
