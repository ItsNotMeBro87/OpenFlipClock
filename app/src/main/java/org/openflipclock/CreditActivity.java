package org.openflipclock;

import android.app.Activity;
import android.os.Bundle;
import android.text.util.Linkify;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class CreditActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(this, 20);
        l.setPadding(p, p, p, p);
        sv.addView(l);

        TextView name = new TextView(this);
        name.setText("OpenFlipClock");
        name.setTextSize(28);
        name.setTextColor(0xFF212121);
        l.addView(name);
        l.addView(line(UI.VERSION, 15, 0xFF757575));
        l.addView(line("A lightweight, open-source flip clock for Android 5.0 and above, with Pomodoro, Flowmodoro, "
                + "alarms, adaptive colors and wallpapers from Wallhaven or your own pictures.", 16, 0xFF212121));
        l.addView(line("Author", 13, 0xFF00796B));
        l.addView(line("itsnotmebro87 and Claude", 16, 0xFF212121));
        l.addView(line("License", 13, 0xFF00796B));
        l.addView(line("GNU General Public License v3.0 (GPLv3)", 16, 0xFF212121));
        l.addView(line("GitHub", 13, 0xFF00796B));
        TextView link = line("https://github.com/ItsNotMeBro87/OpenFlipClock", 16, 0xFF212121);
        Linkify.addLinks(link, Linkify.WEB_URLS);
        l.addView(link);
        setContentView(sv);
    }

    private TextView line(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, UI.dp(this, 12), 0, 0);
        return t;
    }
}
