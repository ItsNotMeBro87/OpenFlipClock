package org.openflipclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

/** Browse / search Wallhaven, preview, set as wallpaper, and configure auto-update. */
public class WallhavenActivity extends Activity {
    private final List<Wallhaven.Item> items = new ArrayList<Wallhaven.Item>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private BaseAdapter ad;
    private EditText q;
    private TextView status;
    private int page = 1;
    private boolean loading, more = true;
    private String query = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final Context c = this;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(c, 8);
        root.setPadding(p, p, p, p);

        LinearLayout row = new LinearLayout(this);
        q = new EditText(this);
        q.setHint("Search Wallhaven");
        q.setSingleLine(true);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int a, android.view.KeyEvent e) { doSearch(); return true; }
        });
        row.addView(q, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(UI.button(c, "Go", new View.OnClickListener() { public void onClick(View v) { doSearch(); } }));
        root.addView(row);

        LinearLayout row2 = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        row2.addView(UI.button(c, "Filters", new View.OnClickListener() { public void onClick(View v) { filters(); } }), lp);
        row2.addView(UI.button(c, "API key", new View.OnClickListener() { public void onClick(View v) { apiKey(null); } }), lp);
        row2.addView(UI.button(c, "Auto-update", new View.OnClickListener() { public void onClick(View v) { autoDialog(); } }), lp);
        root.addView(row2);

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
                if (more && !loading && total > 0 && first + vis >= total - 4) search(false);
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        search(true);
    }

    @Override protected void onDestroy() { pool.shutdownNow(); super.onDestroy(); }

    private void doSearch() {
        query = q.getText().toString().trim();
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(q.getWindowToken(), 0);
        search(true);
    }

    private int gen = 0;

    /** A new search (reset) always wins over any request still in flight, so "Go" works immediately. */
    private void search(boolean reset) {
        if (!reset && loading) return;
        if (reset) { gen++; page = 1; items.clear(); ad.notifyDataSetChanged(); more = true; }
        loading = true;
        status.setText("Loading…");
        final int pg = page, g = gen;
        final String qq = query;
        pool.execute(new Runnable() {
            public void run() {
                try {
                    final List<Wallhaven.Item> r = Wallhaven.search(WallhavenActivity.this, qq, Wallhaven.SORTS[Prefs.i(WallhavenActivity.this, "wh_sort", 0)], pg);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (g != gen) return;
                            items.addAll(r); more = !r.isEmpty(); page++; loading = false;
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
                    byte[] d = Wallhaven.get(WallhavenActivity.this, url);
                    final Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length);
                    if (bm == null) return;
                    cache.put(url, bm);
                    runOnUiThread(new Runnable() { public void run() { if (url.equals(iv.getTag())) iv.setImageBitmap(bm); } });
                } catch (Exception ignored) { }
            }
        });
    }

    private void preview(final Wallhaven.Item it) {
        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setTag(it.large);
        Bitmap bm = cache.get(it.large);
        if (bm != null) iv.setImageBitmap(bm); else load(it.large, iv);
        new AlertDialog.Builder(this)
                .setTitle(it.res)
                .setView(iv)
                .setPositiveButton("Set as wallpaper", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { setWallpaper(it); }
                })
                .setNegativeButton("Back", null)
                .show();
    }

    private void setWallpaper(final Wallhaven.Item it) {
        Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show();
        final Context c = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                try {
                    Wallhaven.downloadAsBackground(c, it.path);
                    Prefs.put(c, "look", Prefs.A_WALLHAVEN);
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(WallhavenActivity.this, "Wallpaper set", Toast.LENGTH_SHORT).show(); finish(); }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(WallhavenActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        }).start();
    }

    private void filters() {
        final Context c = this;
        ScrollView sv = new ScrollView(c);
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(c, 16);
        l.setPadding(p, p, p, p);
        sv.addView(l);
        l.addView(UI.label(c, "Categories", true));
        l.addView(UI.check(c, "General", "wh_gen", true));
        l.addView(UI.check(c, "Anime", "wh_ani", true));
        l.addView(UI.check(c, "People", "wh_peo", true));
        l.addView(UI.label(c, "Purity", true));
        l.addView(UI.check(c, "SFW", "wh_sfw", true));
        l.addView(UI.check(c, "Sketchy", "wh_sky", false));
        CheckBox ns = UI.check(c, "NSFW (needs API key)", "wh_nsfw", false);
        ns.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.put(c, "wh_nsfw", on);
                if (on && Prefs.s(c, "wh_key", "").trim().isEmpty()) apiKey(null);
            }
        });
        l.addView(ns);
        l.addView(UI.label(c, "Orientation", true));
        l.addView(UI.spinner(c, new String[]{"Any", "Wide (landscape)", "Portrait"}, "wh_ratio", 0, null));
        l.addView(UI.label(c, "Sort by", true));
        l.addView(UI.spinner(c, new String[]{"Latest", "Relevance", "Random", "Views", "Favorites", "Toplist"}, "wh_sort", 0, null));
        new AlertDialog.Builder(c).setTitle("Filters").setView(sv)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { search(true); }
                }).show();
    }

    private void apiKey(Runnable after) {
        final EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint("Wallhaven API key (optional, for NSFW)");
        e.setText(Prefs.s(this, "wh_key", ""));
        new AlertDialog.Builder(this).setTitle("API key").setView(e)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { Prefs.put(WallhavenActivity.this, "wh_key", e.getText().toString().trim()); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void autoDialog() {
        final Context c = this;
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(c, 16);
        l.setPadding(p, p, p, p);
        final CheckBox on = new CheckBox(c);
        on.setText("Change wallpaper automatically");
        on.setChecked(Prefs.b(c, "wh_auto", false));
        l.addView(on);
        final EditText qe = new EditText(c);
        qe.setHint("Search query (empty = random)");
        qe.setSingleLine(true);
        qe.setText(Prefs.s(c, "wh_query", ""));
        l.addView(qe);
        l.addView(UI.label(c, "Change every", false));
        LinearLayout r = new LinearLayout(c);
        long mins = Math.max(5, Prefs.l(c, "wh_interval", 30));
        boolean hrs = mins >= 60 && mins % 60 == 0;
        final EditText num = new EditText(c);
        num.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        num.setText(String.valueOf(hrs ? mins / 60 : mins));
        final Spinner unit = new Spinner(c);
        unit.setAdapter(new ArrayAdapter<String>(c, android.R.layout.simple_spinner_dropdown_item, new String[]{"minutes", "hours"}));
        unit.setSelection(hrs ? 1 : 0);
        r.addView(num, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(unit);
        l.addView(r);
        final CheckBox wifi = new CheckBox(c);
        wifi.setText("Only on unmetered network (Wi-Fi)");
        wifi.setChecked(Prefs.b(c, "wh_wifi", true));
        l.addView(wifi);
        TextView note = UI.label(c, "Pictures are not collected: each new wallpaper replaces the previous one. Runs only while the clock is on screen. Minimum 5 minutes.", false);
        note.setTextSize(12);
        l.addView(note);
        new AlertDialog.Builder(c).setTitle("Auto-update").setView(l)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        long v = 30;
                        try { v = Long.parseLong(num.getText().toString().trim()); } catch (Exception ignored) { }
                        if (unit.getSelectedItemPosition() == 1) v *= 60;
                        Prefs.put(c, "wh_interval", Math.max(5L, v));
                        Prefs.put(c, "wh_query", qe.getText().toString().trim());
                        Prefs.put(c, "wh_wifi", wifi.isChecked());
                        Prefs.put(c, "wh_auto", on.isChecked());
                        Prefs.put(c, "wh_last", 0L);
                        if (on.isChecked()) Prefs.put(c, "look", Prefs.A_WALLHAVEN);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }
}
