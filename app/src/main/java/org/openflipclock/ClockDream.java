package org.openflipclock;

import android.service.dreams.DreamService;

/** Optional Android screensaver: shows the flip clock (always in clock mode) while charging / docked. */
public class ClockDream extends DreamService {
    private FlipClockView v;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        v = new FlipClockView(this);
        v.dream = true;
        setContentView(v);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        v.reload();
        v.start();
    }

    @Override public void onDreamingStopped() {
        v.stop();
        super.onDreamingStopped();
    }
}
