# OpenFlipClock v0.2.0 Beta

Lightweight flip clock for Android 5.0+ (API 21). Pure Java, no libraries, one custom View.

## Build the APK with GitHub (no Android Studio)
1. Create a new empty repository on github.com.
2. Upload **everything in this folder** (including the hidden `.github` folder) to the `main` branch.
   Easiest: install Git, then `git init && git add -A && git commit -m init && git branch -M main && git remote add origin <url> && git push -u origin main`.
3. Open the repo's **Actions** tab, wait for "Build APK" to finish (~3-5 min).
4. Open the finished run, download the **OpenFlipClock-v0.1.0-beta** artifact (a zip containing the APK), unzip, and install it.
   (You may need to allow "Install unknown apps".)
5. If the build fails, open the failed step, copy the log and send it to Claude.

## Using it
Tap the clock to reveal the menu button (top-left), tap it to open Settings.

## Signing
`app/openflipclock.jks` is a public keystore so updates install over older builds. For a Play Store release, make your own private one.

## v0.2.0 Beta changes
Pomodoro + Flowmodoro modes, left-edge swipe drawer, square flip tiles with a real split gap, colour wheels
(background / flipboard / number), tap-to-open settings (no accidental scroll changes), reset to default,
Wallhaven search fix. Appearance is now: Dark, Light, Custom color, Wallhaven, Local image.
Planned for v0.2.1: alarms (up to 10, no vibration by default, custom sounds) and more wallpaper sources.

## v0.2.1 Beta changes
Alarms (up to 10, snooze, no vibration by default, system/local sounds, bell on the clock), phase-end notifications
for Pomodoro/Flowmodoro while the app is closed, long break after 4 rounds + today's focus total, size sliders,
display corner radius, brightness + night dimming schedule, optional screensaver, settings that show only options
for the chosen appearance, Credit screen, scrollable color picker, larger timer layout.
Planned for v0.2.2: Pexels and Bing daily wallpaper sources.

## v0.2.2 Beta changes
Pexels (own free API key) and Bing daily image sources, scrollable drawer and dialogs (landscape),
color picker laid out side by side in landscape, timer buttons position option (bottom/left/right in landscape),
size sliders up to 130%.
