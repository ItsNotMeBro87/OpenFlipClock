package org.openflipclock;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/** Minimal Wallhaven API v1 client (HttpURLConnection + org.json, both built into Android). */
final class Wallhaven {
    static final class Item { String id, thumb, large, path, res; }
    static final String[] SORTS = {"date_added", "relevance", "random", "views", "favorites", "toplist"};

    static String cats(Context c) {
        String s = (Prefs.b(c, "wh_gen", true) ? "1" : "0") + (Prefs.b(c, "wh_ani", true) ? "1" : "0") + (Prefs.b(c, "wh_peo", true) ? "1" : "0");
        return s.equals("000") ? "111" : s;
    }

    static String purity(Context c) {
        boolean key = !Prefs.s(c, "wh_key", "").trim().isEmpty();
        boolean sfw = Prefs.b(c, "wh_sfw", true), sk = Prefs.b(c, "wh_sky", false), ns = Prefs.b(c, "wh_nsfw", false) && key;
        if (!sfw && !sk && !ns) sfw = true;
        return (sfw ? "1" : "0") + (sk ? "1" : "0") + (ns ? "1" : "0");
    }

    static byte[] get(Context c, String url) throws IOException {
        HttpURLConnection u = (HttpURLConnection) new URL(url).openConnection();
        u.setConnectTimeout(15000);
        u.setReadTimeout(30000);
        u.setRequestProperty("User-Agent", "OpenFlipClock/0.1");
        String key = Prefs.s(c, "wh_key", "").trim();
        if (!key.isEmpty() && url.contains("/api/")) u.setRequestProperty("X-API-Key", key);
        int code = u.getResponseCode();
        if (code == 429) throw new IOException("Rate limited by Wallhaven, try again in a minute");
        if (code == 401) throw new IOException("Invalid API key");
        if (code != 200) throw new IOException("HTTP " + code);
        try { return Img.readAll(u.getInputStream()); } finally { u.disconnect(); }
    }

    static List<Item> search(Context c, String q, String sort, int page) throws Exception {
        StringBuilder u = new StringBuilder("https://wallhaven.cc/api/v1/search?q=")
                .append(URLEncoder.encode(q, "UTF-8"))
                .append("&categories=").append(cats(c))
                .append("&purity=").append(purity(c))
                .append("&sorting=").append(sort)
                .append("&page=").append(page);
        int r = Prefs.i(c, "wh_ratio", 0);
        if (r == 1) u.append("&ratios=landscape"); else if (r == 2) u.append("&ratios=portrait");
        JSONArray a = new JSONObject(new String(get(c, u.toString()), "UTF-8")).getJSONArray("data");
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Item it = new Item();
            it.id = o.getString("id");
            it.path = o.getString("path");
            it.res = o.optString("resolution", "");
            JSONObject t = o.getJSONObject("thumbs");
            it.thumb = t.getString("small");
            it.large = t.optString("large", it.thumb);
            out.add(it);
        }
        return out;
    }

    /** Downloads the picture, downsizes it to screen size and REPLACES the previous one. */
    static void downloadAsBackground(Context c, String url) throws Exception {
        Img.importBytes(c, get(c, url), "bg_wh.jpg");
    }

    static void autoUpdate(Context c) throws Exception {
        List<Item> l = search(c, Prefs.s(c, "wh_query", ""), "random", 1);
        if (l.isEmpty()) return;
        downloadAsBackground(c, l.get(new Random().nextInt(l.size())).path);
    }
}
