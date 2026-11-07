package org.openflipclock;

import android.content.Context;
import android.util.DisplayMetrics;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Pexels (needs your free API key) and Bing daily image (unofficial public endpoint, no key). */
final class Sources {
    static final class Item { String thumb, preview, full, full2, caption, link; }

    static final String[] MKT = {"en-US", "en-GB", "en-CA", "en-AU", "en-IN", "de-DE", "fr-FR", "es-ES", "pt-BR", "ja-JP", "zh-CN", "id-ID"};
    static final String[] MKT_NAMES = {"United States", "United Kingdom", "Canada", "Australia", "India", "Germany", "France", "Spain", "Brazil", "Japan", "China", "Indonesia"};

    static byte[] get(String url, String auth) throws IOException {
        HttpURLConnection u = (HttpURLConnection) new URL(url).openConnection();
        u.setConnectTimeout(15000);
        u.setReadTimeout(30000);
        u.setRequestProperty("User-Agent", "OpenFlipClock/0.2");
        if (auth != null && !auth.isEmpty()) u.setRequestProperty("Authorization", auth);
        int code = u.getResponseCode();
        if (code == 429) throw new IOException("Rate limit reached, try again later");
        if (code == 401 || code == 403) throw new IOException("Invalid or missing API key");
        if (code != 200) throw new IOException("HTTP " + code);
        try { return Img.readAll(u.getInputStream()); } finally { u.disconnect(); }
    }

    private static boolean portraitScreen(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return m.heightPixels >= m.widthPixels;
    }

    // ---------------------------------------------------------------- Pexels
    static List<Item> pexels(Context c, String q, int page) throws Exception {
        String key = Prefs.s(c, "px_key", "").trim();
        if (key.isEmpty()) throw new IOException("Add your free Pexels API key first (API key button)");
        int o = Prefs.i(c, "px_orient", 0);   // 0 match screen, 1 any, 2 landscape, 3 portrait
        String orient = o == 2 ? "landscape" : o == 3 ? "portrait" : o == 0 ? (portraitScreen(c) ? "portrait" : "landscape") : "";
        String u = q.isEmpty() ? "https://api.pexels.com/v1/curated?per_page=30&page=" + page
                : "https://api.pexels.com/v1/search?query=" + URLEncoder.encode(q, "UTF-8") + "&per_page=30&page=" + page
                + (orient.isEmpty() ? "" : "&orientation=" + orient);
        JSONArray a = new JSONObject(new String(get(u, key), "UTF-8")).getJSONArray("photos");
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject p = a.getJSONObject(i);
            boolean tall = p.optInt("height") >= p.optInt("width");
            if (q.isEmpty() && !orient.isEmpty() && tall != orient.equals("portrait")) continue;   // curated has no orientation filter
            JSONObject s = p.getJSONObject("src");
            Item it = new Item();
            it.thumb = s.getString("medium");
            it.preview = s.getString("large");
            it.full = s.optString("large2x", s.getString("large"));
            it.caption = "Photo by " + p.optString("photographer", "unknown") + " on Pexels";
            it.link = p.optString("url", null);
            out.add(it);
        }
        return out;
    }

    // ---------------------------------------------------------------- Bing daily
    static List<Item> bing(Context c) throws Exception {
        String mkt = MKT[Math.min(MKT.length - 1, Prefs.i(c, "bing_mkt", 0))];
        JSONArray a = new JSONObject(new String(get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=" + mkt, null), "UTF-8")).getJSONArray("images");
        boolean portrait = portraitScreen(c);
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String base = "https://www.bing.com" + o.getString("urlbase");
            Item it = new Item();
            it.thumb = base + "_400x240.jpg";
            it.preview = base + "_800x480.jpg";
            it.full2 = "https://www.bing.com" + o.getString("url");           // 1920x1080, always exists
            it.full = portrait ? base + "_1080x1920.jpg" : it.full2;          // portrait version if Bing has one
            it.caption = o.optString("copyright", o.optString("title", ""));
            out.add(it);
        }
        return out;
    }

    /** Downloads the full picture (falls back to the second URL) and replaces the cached background file. */
    static void download(Context c, Item it, String file) throws Exception {
        byte[] d;
        try { d = get(it.full, null); }
        catch (Exception e) { if (it.full2 == null) throw e; d = get(it.full2, null); }
        Img.importBytes(c, d, file);
    }

    static void bingAuto(Context c) throws Exception {
        List<Item> l = bing(c);
        if (!l.isEmpty()) download(c, l.get(0), "bg_bing.jpg");
    }
}
