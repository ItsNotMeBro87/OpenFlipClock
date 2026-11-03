# OpenFlipClock v0.1.0 Beta

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
