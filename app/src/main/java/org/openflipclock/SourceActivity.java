package org.openflipclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Browser for Pexels ("px") and Bing daily image ("bing"). Preview, then set as wallpaper or go back. */
public class SourceActivity extends Activity {
    private boolean px;
    private final List<Sources.Item> items = new ArrayList<Sources.Item>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private BaseAdapter ad;
    private EditText q;
    private TextView status;
    private int page = 1, gen = 0;
    private boolean loading, more = true;
    private String query = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = "px".equals(getIntent().getStringExtra("kind"));
        setTitle(px ? "Pexels" : "Bing daily image");
        final Context c = this;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(c, 8);
        root.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);

        if (px) {
            LinearLayout row = new LinearLayout(this);
            q = new EditText(this);
            q.setHint("Search Pexels (empty = curated)");
            q.setSingleLine(true);
            q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            q.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView v, int a, android.view.KeyEvent e) { doSearch(); return true; }
            });
            row.addView(q, lp);
            row.addView(UI.button(c, "Go", new View.OnClickListener() { public void onClick(View v) { doSearch(); } }));
            root.addView(row);
            LinearLayout row2 = new LinearLayout(this);
            row2.addView(UI.button(c, "Orientation", new View.OnClickListener() { public void onClick(View v) { orientDialog(); } }), lp);
            row2.addView(UI.button(c, "API key", new View.OnClickListener() { public void onClick(View v) { keyDialog(); } }), lp);
            root.addView(row2);
            TextView credit = UI.label(c, "Photos provided by Pexels (pexels.com)", false);
            credit.setTextSize(12);
            root.addView(credit);
        } else {
            LinearLayout row2 = new LinearLayout(this);
            row2.addView(UI.button(c, "Region", new View.OnClickListener() { public void onClick(View v) { regionDialog(); } }), lp);
            row2.addView(UI.button(c, "Daily update", new View.OnClickListener() { public void onClick(View v) { dailyDialog(); } }), lp);
            root.addView(row2);
            TextView note = UI.label(c, "Bing's picture of the day, last 8 days. Pictures belong to their owners; for personal use.", false);
            note.setTextSize(12);
            root.addView(note);
        }
        status = new TextView(this);
        root.addView(status);

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(UI.dp(c, 150));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(UI.dp(c, 4));
        grid.setVerticalSpacing(UI.dp(c, 4));
        ad = new BaseAdapter() {
            public int getCount() { return items.size(); }
            public Object getItem(int i) { return items.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup parent) {
                ImageView iv = (ImageView) cv;
                if (iv == null) {
                    iv = new ImageView(c);
                    iv.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 100)));
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setBackgroundColor(0xFF333333);
                }
                String url = items.get(i).thumb;
                iv.setTag(url);
                Bitmap bm = cache.get(url);
                if (bm != null) iv.setImageBitmap(bm); else { iv.setImageDrawable(null); load(url, iv); }
                return iv;
            }
        };
        grid.setAdapter(ad);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int i, long id) { preview(items.get(i)); }
        });
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int s) { }
            public void onScroll(AbsListView v, int first, int vis, int total) {
                if (px && more && !loading && total > 0 && first + vis >= total - 4) search(false);
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        if (px && Prefs.s(this, "px_key", "").trim().isEmpty()) keyDialog(); else search(true);
    }

    @Override protected void onDestroy() { pool.shutdownNow(); super.onDestroy(); }

    private void doSearch() {
        query = q.getText().toString().trim();
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(q.getWindowToken(), 0);
        search(true);
    }

    private void search(boolean reset) {
        if (!reset && (loading || !px)) return;
        if (reset) { gen++; page = 1; items.clear(); ad.notifyDataSetChanged(); more = true; }
        loading = true;
        status.setText("Loading…");
        final int pg = page, g = gen;
        final String qq = query;
        pool.execute(new Runnable() {
            public void run() {
                try {
                    final List<Sources.Item> r = px ? Sources.pexels(SourceActivity.this, qq, pg) : Sources.bing(SourceActivity.this);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (g != gen) return;
                            items.addAll(r); more = px && !r.isEmpty(); page++; loading = false;
                            ad.notifyDataSetChanged();
                            status.setText(items.isEmpty() ? "No results" : "");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { if (g != gen) return; loading = false; status.setText("Error: " + e.getMessage()); }
                    });
                }
            }
        });
    }

    private void load(final String url, final ImageView iv) {
        pool.execute(new Runnable() {
            public void run() {
                try {
                    byte[] d = Sources.get(url, null);
                    final Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length);
                    if (bm == null) return;
                    cache.put(url, bm);
                    runOnUiThread(new Runnable() { public void run() { if (url.equals(iv.getTag())) iv.setImageBitmap(bm); } });
                } catch (Exception ignored) { }
            }
        });
    }

    private void preview(final Sources.Item it) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(this, 12);
        l.setPadding(p, p, p, p);
        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setTag(it.preview);
        Bitmap bm = cache.get(it.preview);
        if (bm != null) iv.setImageBitmap(bm); else load(it.preview, iv);
        l.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView cap = new TextView(this);
        cap.setText(it.caption);
        cap.setTextSize(13);
        cap.setPadding(0, UI.dp(this, 8), 0, 0);
        l.addView(cap);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setView(UI.scroll(this, l))   // scrollable in landscape
            .setPositiveButton("Set as wallpaper", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { setWallpaper(it); }
            }).setNegativeButton("Back", null);
        if (px && it.link != null) b.setNeutralButton("Open on Pexels", new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.link))); } catch (Exception ignored) { }
            }
        });
        b.show();
    }

    private void setWallpaper(final Sources.Item it) {
        Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show();
        final Context c = getApplicationContext();
        final int look = px ? Prefs.A_PEXELS : Prefs.A_BING;
        new Thread(new Runnable() {
            public void run() {
                try {
                    Sources.download(c, it, Prefs.bgFile(look));
                    Prefs.put(c, "look", look);
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(SourceActivity.this, "Wallpaper set", Toast.LENGTH_SHORT).show(); finish(); }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(SourceActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        }).start();
    }

    private void orientDialog() {
        new AlertDialog.Builder(this).setTitle("Orientation")
            .setSingleChoiceItems(new String[]{"Match my screen", "Any", "Landscape", "Portrait"}, Prefs.i(this, "px_orient", 0),
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { Prefs.put(SourceActivity.this, "px_orient", w); d.dismiss(); search(true); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void regionDialog() {
        new AlertDialog.Builder(this).setTitle("Region")
            .setSingleChoiceItems(Sources.MKT_NAMES, Prefs.i(this, "bing_mkt", 0), new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { Prefs.put(SourceActivity.this, "bing_mkt", w); d.dismiss(); search(true); }
            }).setNegativeButton("Cancel", null).show();
    }

    private void dailyDialog() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(this, 16);
        l.setPadding(p, p, p, p);
        l.addView(UI.check(this, "Update wallpaper automatically once a day", "bing_auto", false));
        l.addView(UI.check(this, "Only on unmetered network (Wi-Fi)", "wh_wifi", true));
        TextView n = UI.label(this, "Checked when the clock is opened. The previous picture is replaced, nothing piles up on your phone.", false);
        n.setTextSize(12);
        l.addView(n);
        new AlertDialog.Builder(this).setTitle("Daily update").setView(UI.scroll(this, l)).setPositiveButton("OK", null).show();
    }

    private void keyDialog() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(this, 16);
        l.setPadding(p, p, p, p);
        TextView n = UI.label(this, "Get a free key at pexels.com/api and paste it here. Requests are limited by Pexels (about 200 per hour).", false);
        n.setTextSize(13);
        l.addView(n);
        final EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint("Pexels API key");
        e.setText(Prefs.s(this, "px_key", ""));
        l.addView(e);
        new AlertDialog.Builder(this).setTitle("Pexels API key").setView(UI.scroll(this, l))
            .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    Prefs.put(SourceActivity.this, "px_key", e.getText().toString().trim());
                    search(true);
                }
            }).setNegativeButton("Cancel", null).show();
    }
}
