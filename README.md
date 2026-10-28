# TriShot

## Make the screenshots, with an easier way!

# Features:
- **Three-finger swipe gesture screenshots, with [Shizuku](https://shizuku.rikka.app/) required to work**,
- **Screenshot counter, so you know how many you took, and how much all they weigh together**,
- **Screenshot Manager, which lets you either share them with anyone, or either you can remove them (P.S: After certain numbers a suprise will appear, so keep screenshoting to see it)**,
- **Updates over-the-air, so you can be up to date with the latest changes,**
- **No ads, i'm not gonna make any revenue on that app, because I "wrote" this app only because of a missing feature on my main phone**,
- **Quick Settings tile, if you decide that you don't want to use Shizuku for some reason, you can resort to click the QS tile which also will make a screenshot.**

# How to install it: 
Download the latest `TriShot.apk` from [Releases](../../releases) and open it on your phone, however _**if Google says no**_, then you need going to **Developer Options** > **Allow installing apps from not verified developers** > **Turn it on, reboot device** > **Wait the exact 24h (retarded, I know)** > **Then install the app.**

# Usage: 
1. Open TriShot and tap **Turn on**, then switch on **TriShot capture** in Accessibility settings.
   If the switch is greyed out ("Restricted setting"), open App info → ⋮ → **Allow restricted settings** first.
2. Tap **Allow** on the photo card and choose **Allow all**, so TriShot can show and measure the screenshots Android saves.
3. Turn on **Three-finger swipe with Shizuku**. If Shizuku isn't installed, after turning on the switch TriShot opens its Google Play page; start Shizuku, then flip the switch again and allow TriShot.
4. Optional: add the **Screenshot** tile to Quick Settings.


# How does it work?:
- The app asks for few permissions at the first start (`GLOBAL_ACTION_TAKE_SCREENSHOT`, `POST_NOTIFICATIONS`, `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `BIND_ACCESSIBILITY_SERVICE`, `BIND_QUICK_SETTINGS_TILE`, `INTERACT_ACROSS_USERS_FULL` and for updates: `INTERNET`, `REQUEST_INSTALL_PACKAGES`),
- After you allow the app to use Shizuku by switching the mode to three-finger gesture, the `TouchWatcher` service works in the background to watch for the gesture (in detail: it catches the raw touch screen inputs from `/dev/input/touch`), which results in taking a screenshot, of course without interrupting anything you do or what orientation the device is currently in. **It doesn't read your screen content nor it sends screenshots anywhere, they are locally stored on your device, like a normal screenshot with a key-combo.**
- Screenshots are being saved to the default location for them: `Pictures/Screenshots`, where the app tracks the ones which were captured by it.


# Screenshots of the app
-soon.

***P.S: This app is a test of LLM capabilities, i'll update it on my own, after 2.0, probably.***
