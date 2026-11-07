package org.openflipclock;

import android.app.AlertDialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.*;

/** Small helpers so the settings screens stay plain framework widgets (light on RAM/APK size). */
final class UI {
    static final String VERSION = "v0.2.2 Beta";

    interface IntCb { void run(int v); }

    static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + .5f); }

    static TextView label(Context c, String t, boolean header) {
        TextView tv = new TextView(c);
        tv.setText(t);
        if (header) { tv.setTextSize(15); tv.setAllCaps(true); tv.setTextColor(0xFF00796B); tv.setPadding(0, dp(c, 20), 0, dp(c, 4)); }
        else { tv.setTextSize(16); tv.setPadding(0, dp(c, 8), 0, 0); }
        return tv;
    }

    static CheckBox check(Context c, String t, String k, boolean d) { return check(c, t, k, d, null); }

    static CheckBox check(final Context c, String t, final String k, boolean d, final Runnable after) {
        CheckBox cb = new CheckBox(c);
        cb.setText(t);
        cb.setChecked(Prefs.b(c, k, d));
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { Prefs.put(c, k, on); if (after != null) after.run(); }
        });
        return cb;
    }

    static Spinner spinner(final Context c, String[] items, final String k, final int d, final IntCb cb) {
        Spinner sp = new Spinner(c);
        sp.setAdapter(new ArrayAdapter<String>(c, android.R.layout.simple_spinner_dropdown_item, items));
        sp.setSelection(Prefs.i(c, k, d), false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                if (pos != Prefs.i(c, k, d)) { Prefs.put(c, k, pos); if (cb != null) cb.run(pos); }
            }
            public void onNothingSelected(AdapterView<?> a) { }
        });
        return sp;
    }

    static LinearLayout seek(final Context c, final String t, final String k, int max, int d, final String unit) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        final TextView tv = label(c, "", false);
        SeekBar sb = new SeekBar(c);
        sb.setMax(max);
        sb.setProgress(Prefs.i(c, k, d));
        tv.setText(t + ": " + sb.getProgress() + unit);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { tv.setText(t + ": " + p + unit); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { Prefs.put(c, k, s.getProgress()); }
        });
        l.addView(tv);
        l.addView(sb);
        return l;
    }

    static Button button(Context c, String t, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(t);
        b.setOnClickListener(l);
        return b;
    }

    // ---------- tap-to-open rows (no inline spinners/sliders, so scrolling can't change a setting) ----------
    static LinearLayout row(Context c, String title, String value) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 12);
        r.setPadding(0, p, 0, p);
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) r.setBackgroundResource(tv.resourceId);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(0xFF212121);
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(13);
        v.setTextColor(0xFF757575);
        r.addView(t);
        r.addView(v);
        r.setClickable(true);
        return r;
    }

    static void setValue(LinearLayout r, String s) { ((TextView) r.getChildAt(1)).setText(s); }

    static View choiceRow(final Context c, final String title, final String[] items, final String key, final int def, final IntCb cb) {
        final LinearLayout r = row(c, title, items[Math.min(items.length - 1, Prefs.i(c, key, def))]);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(c).setTitle(title)
                    .setSingleChoiceItems(items, Math.min(items.length - 1, Prefs.i(c, key, def)), new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            Prefs.put(c, key, w);
                            setValue(r, items[w]);
                            d.dismiss();
                            if (cb != null) cb.run(w);
                        }
                    }).setNegativeButton("Cancel", null).show();
            }
        });
        return r;
    }

    static View sliderRow(final Context c, final String title, final String key, final int max, final int def, final String unit) {
        final LinearLayout r = row(c, title, Prefs.i(c, key, def) + unit);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                LinearLayout l = new LinearLayout(c);
                l.setOrientation(LinearLayout.VERTICAL);
                int p = dp(c, 20);
                l.setPadding(p, p, p, p);
                final TextView tv = new TextView(c);
                tv.setTextSize(18);
                final SeekBar sb = new SeekBar(c);
                sb.setMax(max);
                sb.setProgress(Prefs.i(c, key, def));
                tv.setText(sb.getProgress() + unit);
                sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar s, int pr, boolean u) { tv.setText(pr + unit); }
                    public void onStartTrackingTouch(SeekBar s) { }
                    public void onStopTrackingTouch(SeekBar s) { }
                });
                l.addView(tv);
                l.addView(sb);
                new AlertDialog.Builder(c).setTitle(title).setView(l)
                    .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            Prefs.put(c, key, sb.getProgress());
                            setValue(r, sb.getProgress() + unit);
                        }
                    }).setNegativeButton("Cancel", null).show();
            }
        });
        return r;
    }

    static String colorName(int v) { return v == 0 ? "Automatic" : String.format("#%06X", v & 0xFFFFFF); }

    static View colorRow(final Context c, final String title, final String key) {
        final LinearLayout r = row(c, title, colorName(Prefs.i(c, key, 0)));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final ColorPicker cp = new ColorPicker(c, Prefs.i(c, key, 0));
                LinearLayout l = new LinearLayout(c);
                int p = dp(c, 16);
                l.setPadding(p, p, p, p);
                l.addView(cp, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                ScrollView sv = new ScrollView(c);   // scrollable, so it still fits in landscape
                sv.addView(l);
                new AlertDialog.Builder(c).setTitle(title).setView(sv)
                    .setPositiveButton("Select", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            Prefs.put(c, key, cp.getColor());
                            setValue(r, colorName(cp.getColor()));
                        }
                    })
                    .setNeutralButton("Automatic", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            Prefs.put(c, key, 0);
                            setValue(r, colorName(0));
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
            }
        });
        return r;
    }

    static String hm(int mins) { return String.format("%02d:%02d", mins / 60, mins % 60); }

    static View timeRow(final Context c, final String title, final String key, final int def) {
        final LinearLayout r = row(c, title, hm(Prefs.i(c, key, def)));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int cur = Prefs.i(c, key, def);
                new android.app.TimePickerDialog(c, new android.app.TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(TimePicker tp, int h, int m) { Prefs.put(c, key, h * 60 + m); setValue(r, hm(h * 60 + m)); }
                }, cur / 60, cur % 60, true).show();
            }
        });
        return r;
    }

    static ScrollView scroll(Context c, View v) {
        ScrollView s = new ScrollView(c);
        s.addView(v);
        return s;
    }
}
