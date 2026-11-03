package org.openflipclock;

import android.content.Context;
import android.view.View;
import android.widget.*;

/** Small helpers so the settings screens stay plain framework widgets (light on RAM/APK size). */
final class UI {
    interface IntCb { void run(int v); }

    static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + .5f); }

    static TextView label(Context c, String t, boolean header) {
        TextView tv = new TextView(c);
        tv.setText(t);
        if (header) { tv.setTextSize(15); tv.setAllCaps(true); tv.setTextColor(0xFF00796B); tv.setPadding(0, dp(c, 20), 0, dp(c, 4)); }
        else { tv.setTextSize(16); tv.setPadding(0, dp(c, 8), 0, 0); }
        return tv;
    }

    static CheckBox check(final Context c, String t, final String k, boolean d) {
        CheckBox cb = new CheckBox(c);
        cb.setText(t);
        cb.setChecked(Prefs.b(c, k, d));
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { Prefs.put(c, k, on); }
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
}
