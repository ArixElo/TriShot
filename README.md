# TriShot

Take a screenshot on Android by swiping down with three fingers, in any app.
Built with Kotlin, Jetpack Compose and Material 3 (Material You colors, the device's system font). English and Polish.

Requires **Android 11+**. The swipe in every app needs **[Shizuku](https://shizuku.rikka.app/)**.

## Features
- **Three-finger swipe** down in any app (with Shizuku) or inside TriShot.
- **Android's own screenshot**: the same flash and Share / Edit / Capture more preview as power + volume down.
- **Quick Settings tile** and a **Capture** button that hides the app first.
- **Gallery** of the screenshots TriShot took: full-screen viewer, details, share, multi-select, delete.
- **Stats**: how many are saved, their total size, and how many were taken in total.

## Install
Download `TriShot-<version>.apk` from [Releases](../../releases) and open it on your phone.

## Setup
1. Open TriShot and tap **Turn on**, then switch on **TriShot capture** in Accessibility settings.
   If the switch is greyed out ("Restricted setting"), open App info → ⋮ → **Allow restricted settings** first.
2. Tap **Allow** on the photo card and choose **Allow all**, so TriShot can show and measure the screenshots Android saves.
3. Turn on **Three-finger swipe with Shizuku**. If Shizuku isn't installed, TriShot opens its Google Play page; start Shizuku, then flip the switch again and allow TriShot.
4. Optional: add the **Screenshot** tile to Quick Settings.

## How it works
- **Capture**: an accessibility service asks the system for a normal screenshot (`GLOBAL_ACTION_TAKE_SCREENSHOT`). It doesn't read screen content.
- **Three-finger detection**: a Shizuku user service ([`TouchWatcher.kt`](app/src/main/java/pl/arixelo/trishot/shizuku/TouchWatcher.kt)) runs as the shell user and only *reads* raw touchscreen events from `/dev/input`. Touches still reach apps unchanged, so typing and taps behave normally.
- **Gallery**: screenshots stay in `Pictures/Screenshots`; TriShot remembers the MediaStore IDs of the ones it triggered.

## Build
```
gradlew assembleDebug
```
Needs JDK 17+ (Android Studio's bundled JBR works) and Android SDK 37.

Release builds are signed with the key described in a `keystore.properties` file in the project root (not committed):
```
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```
Without that file, `assembleRelease` falls back to the debug key.

On Windows, the AIDL compiler fails on very long paths; build from a short path (or a `subst` drive) if that happens.

P.S: This app is a test of LLM capabilities, i'll update it on my own without it's help for further versions.
