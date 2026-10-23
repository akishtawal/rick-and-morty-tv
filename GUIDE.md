# Guide: put Interdimensional Cable on your Android TV

This walks through everything from a stock Android TV to the setup in the [launch video](docs/media/launch-video.mp4). Allow about an hour the first time. Nothing here roots the TV or uninstalls anything, and every step can be undone.

**Contents**
1. [What you need](#1-what-you-need)
2. [Turn on network debugging on the TV](#2-turn-on-network-debugging-on-the-tv)
3. [Connect from your computer](#3-connect-from-your-computer)
4. [Optional: font, logos and wallpapers](#4-optional-font-logos-and-wallpapers)
5. [Choose your channels](#5-choose-your-channels)
6. [Build and install](#6-build-and-install)
7. [Make it your Home screen](#7-make-it-your-home-screen)
8. [Set the screensaver](#8-set-the-screensaver)
9. [Your photos in Channel surf](#9-your-photos-in-channel-surf)
10. [Using it](#10-using-it)
11. [Optional: debloat the TV safely](#11-optional-debloat-the-tv-safely)
12. [Check the speed](#12-check-the-speed)
13. [Troubleshooting](#13-troubleshooting)
14. [Undo everything](#14-undo-everything)
15. [Building it with Claude Code](#15-building-it-with-claude-code)

---

## 1. What you need

**The TV**
- Android TV with the classic **Android TV home** (`com.google.android.tvlauncher`) on **Android 11 (API 30) or later**. On Google TV devices the stock home is a different package (`com.google.android.apps.tv.launcherx`): change `Prefs.STOCK_LAUNCHER` in `home/Prefs.kt` to match (untested). The app was built and tested on a realme SLED 55" 4K (MediaTek, 32-bit `armeabi-v7a`, 1.35 GB RAM, Android TV 11). It is plain Kotlin with no native code, so it runs on 32-bit and 64-bit TVs.
- The TV and your computer on the same Wi-Fi network.

**The computer** (macOS or Linux; Windows works with the same commands in Git Bash or WSL)
- **JDK 17**. macOS: `brew install openjdk@17`, then `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`.
- **Android SDK** with `platforms;android-34` and `build-tools;34.0.0`. Easiest is Android Studio. Without it on macOS: `brew install --cask android-commandlinetools`, then `sdkmanager "platforms;android-34" "build-tools;34.0.0"`. Point Gradle at it with `android/local.properties` containing `sdk.dir=/path/to/sdk` (or set `ANDROID_HOME`).
- **adb**: `brew install android-platform-tools` (or it ships with Android Studio).

The project uses the Gradle wrapper (Gradle 8.9, Android Gradle Plugin 8.5.2, Kotlin 1.9.24), so you don't need to install Gradle.

## 2. Turn on network debugging on the TV

1. **Settings → Device Preferences → About**, scroll to **Build** and press OK on it seven times until it says you're a developer.
2. **Settings → Device Preferences → Developer options**: turn on **USB debugging**, and **Network debugging** if your TV lists it separately.
3. Find the TV's IP address: **Settings → Network & Internet →** your Wi-Fi network. It looks like `192.168.x.y`.

## 3. Connect from your computer

```bash
adb connect <tv-ip>:5555
```

The first time, the TV shows an "Allow USB debugging?" prompt. Tick **Always allow from this computer** and press OK. Check:

```bash
adb devices                       # shows <tv-ip>:5555  device
adb shell getprop ro.build.version.release   # Android version, should be 11 or higher
```

The scripts in `tools/` read the TV address from `ANDROID_SERIAL`, so set it once per terminal:

```bash
export ANDROID_SERIAL=<tv-ip>:5555
```

If adb says **offline**, run `adb disconnect` and connect again. If it hangs, the TV is probably asleep; wake it with the remote.

## 4. Optional: font, logos and wallpapers

The app works without any of these, but this is what makes it look like the screenshots. None of them ship in this repo because they belong to other people.

| What | Where it goes | Without it |
|---|---|---|
| **Get Schwifty** display font (free fan font by jonizaak, on FontSpace) | `android/app/src/main/res/font/get_schwifty.ttf` (lowercase file name, exactly this) | Titles use VT323 |
| Transparent **app logos** for the portal | `android/app/src/main/assets/logos/<package>.png`, e.g. `com.netflix.ninja.png` | The portal shows the app's own TV banner |
| **Sticker** versions of logos (Settings → Portal logo style → Stickers) | `android/app/src/main/assets/stickers/<package>.png` | Same fallback |
| **Wallpapers** for the Channel surf screensaver (1920×1080 JPG/PNG) | `android/app/src/main/assets/wallpapers/` | Channel surf shows only your photos, or "NO SIGNAL" |

Notes:
- Get Schwifty has only letters, digits and spaces, and it draws most capitals as lowercase shapes. That is why the home title only accepts letters, digits, spaces and apostrophes (the apostrophe falls back to the system font).
- Find package names with `adb shell pm list packages`. Wikimedia Commons has many brand logos with clear licence notes.
- For Rick and Morty art: the show's pages on [TMDB](https://www.themoviedb.org/tv/60625-rick-and-morty/images/backdrops), the [Fandom wiki](https://rickandmorty.fandom.com/) and [Adult Swim](https://www.adultswim.com/videos/rick-and-morty). That art is copyrighted: fine on your own TV, don't redistribute it.
- `design/portal/render.py` regenerates the portal images (`python3 render.py`, needs numpy and Pillow). Copy the results into `android/app/src/main/assets/portal/` if you change them.

## 5. Choose your channels

The seven main channels and their one-line descriptions live in `android/app/src/main/java/com/aditya/cabletv/home/Catalog.kt`:

```kotlin
object Channels {
    val primary = listOf(
        Channel(1, "com.netflix.ninja", "Netflix", "Picks up where you left off."),
        Channel(2, "com.google.android.youtube.tv", "YouTube", "Subscriptions and the long tail of the multiverse."),
        ...
    )
}
```

Change the package names to the apps you use (any app missing on the TV simply shows as an empty slot). You can also rearrange and swap channels later on the TV, in Settings → Home channels, without rebuilding.

Two more things you may want to change before building:
- **Weather city**: `Prefs.DEFAULT_CITY` in `home/Prefs.kt` (set to Ahmedabad). Any city name Open-Meteo's geocoder knows works.
- **About → This TV** text: in `home/SettingsActivity.kt`, it describes the realme TV.

## 6. Build and install

```bash
cd android
./gradlew :app:assembleBench
adb install -r app/build/outputs/apk/bench/app-bench.apk
```

`bench` is a release build (R8-minified) signed with the debug key, which is all a sideloaded app needs. The APK is about 0.5 MB, more with your own logos and wallpapers.

Try it once without touching any settings:

```bash
adb shell am start -n com.aditya.cabletv/.home.HomeActivity
```

## 7. Make it your Home screen

Android TV doesn't let a sideloaded app replace the stock launcher: the system launcher's Home filter has priority 2 and sideloaded apps are capped at 0, so the system always picks the stock one. Setting the "home role" with `cmd package set-home-activity` doesn't help either.

Instead the app includes a small **accessibility service** that:
- catches the remote's **Home** key and opens Interdimensional Cable;
- watches for the stock launcher's windows (for example after Back, or when an app exits) and brings Interdimensional Cable forward;
- opens Interdimensional Cable when the TV boots.

It reads no screen content and needs no network. Turn it on yourself on the TV:

**Settings → Device Preferences → Accessibility → Interdimensional Cable Home button → On**

Then press Home on the remote.

- **Hold Home for 2 seconds** to get the stock launcher (for its settings gear or the inputs menu). The service stays out of the way until your next short Home press. A reboot also resets it.
- Settings → About → **Open stock home** does the same.
- The stock launcher stays installed and enabled. Don't disable it: it is your safety net.

Check from the computer:

```bash
adb shell settings get secure enabled_accessibility_services
# com.aditya.cabletv/com.aditya.cabletv.home.HomeKeyService
```

## 8. Set the screensaver

**Settings → Device Preferences → Screen saver → Screen saver → Interdimensional Cable.** The "When to start" option there sets the idle time (Android's own setting; the app can't change it).

If the choice doesn't stick, pick it again and check:

```bash
adb shell settings get secure screensaver_components
# com.aditya.cabletv/com.aditya.cabletv.saver.CableDream
```

Preview any scene without waiting:

```bash
adb shell am start -n com.aditya.cabletv/.saver.ScreensaverPreviewActivity --es scene portal   # or surf, offair
```

Or Settings → Screensaver → Preview in the app. In the app you can also pick which scenes run, how long each stays up, and whether the clock, date and weather show.

## 9. Your photos in Channel surf

```bash
export ANDROID_SERIAL=<tv-ip>:5555
tools/push_photos.sh ~/Pictures/TV
```

The script resizes each photo to 1920 px wide (macOS `sips`; on Linux swap in ImageMagick's `convert -resize 1920x1920`) and copies it into the app's own folder on the TV, `/sdcard/Android/data/com.aditya.cabletv/files/photos`, so no storage permission is needed. Channel surf alternates your photos with any bundled wallpapers, one every 30 seconds, with a static flash in between.

## 10. Using it

| Remote | On Home | In Other Apps | In Settings |
|---|---|---|---|
| ◀ ▶ | Flip channels | Move | — |
| ▲ ▼ | ▲ to the Settings button | Move | Move |
| OK | Tune in (open the app) | Open | Change |
| ☰ Menu | Settings | Promote the app to the main seven | — |
| Back | Nothing (you're already home) | Back to Home | Back |
| Home (short) | Interdimensional Cable | | |
| Home (hold 2 s) | Stock launcher | | |

Settings, by section:
- **Look**: theme (Night, Living room), home title (up to 24 characters, the last word turns green), portal logo style (glowing core or stickers), motion (full or reduced), static between channels.
- **Home channels**: move, swap, or promote apps from Other Apps.
- **Screensaver**: preview, scenes, switch interval, photos, clock, date and weather.
- **About**: open the TV's own settings (the launcher hides the stock settings gear), open the stock home, credits.

## 11. Optional: debloat the TV safely

Many TVs ship with ad and tracking services. On the realme TV this included `tv.anoki.acr.controller`, an automatic content recognition service that watches what is on screen for ad targeting. `tools/debloat.sh` turns packages off with `pm disable-user`. It **never uninstalls**, works in batches of at most 5, refuses anything on its keep list, and logs every change so you can undo it.

```bash
export ANDROID_SERIAL=<tv-ip>:5555
tools/debloat.sh --plan               # list the tiers and whether each package is enabled
tools/debloat.sh --apply A            # ads, tracking, stock-launcher promos
tools/debloat.sh --apply B            # unused helpers
tools/debloat.sh --apply <pkg> <pkg>  # anything else, up to 5 at a time
tools/debloat.sh --rollback <pkg>     # undo one
tools/debloat.sh --rollback all       # undo everything the script did
```

**The tiers in the script were chosen for the realme/HKC TV.** Before you apply anything on another TV:
1. Save the full package list first: `adb shell pm list packages -f > packages-before.txt`.
2. Look up each package you don't recognise. Leave the keep list alone: the stock launcher, Google services, the keyboard, the voice remote (`katniss`), Chromecast built-in (`mediashell`), and anything from your TV's chip vendor (`com.mediatek.*` or similar).
3. After each batch, check the remote, HDMI inputs and sound, and that Home still opens Interdimensional Cable.

On the realme TV, disabling 21 packages (ads, tracker, Backdrop/Glance screensaver, recommendation rows, unused preinstalled apps) took free RAM from about 430 MB to 578 MB.

## 12. Check the speed

`tools/measure.sh` records warm Home time, memory, frame stats and channel coverage with `am start -W` and `dumpsys`. It opens MUBI and other apps in between, so run it when nobody is watching, and edit its `PRIMARY` list to your channels:

```bash
export ANDROID_SERIAL=<tv-ip>:5555
tools/measure.sh
```

## 13. Troubleshooting

**Home opens the stock launcher again.** The accessibility service got switched off. Android turns it off when the app crashes or is force-stopped. Turn it back on in Settings → Device Preferences → Accessibility. Avoid `adb shell am force-stop com.aditya.cabletv`; to restart the home screen use `adb shell am start --activity-clear-task -n com.aditya.cabletv/.home.HomeActivity`. After installing a new build, check `settings get secure enabled_accessibility_services`.

**Home works on the remote but not with `adb shell input keyevent KEYCODE_HOME`.** Expected: keys injected over adb skip accessibility key filtering. Only the physical remote goes through it.

**The settings say the service is on, but Home still goes to the stock launcher.** Toggle it off and on again. The TV UI can show "On" while the system has it off; `settings get secure accessibility_enabled` should print `1`.

**Don't drive the TV over adb while someone is using the remote.** Scripted `am start` and key presses land on whatever is on screen. A restart of the home screen throws away an open dialog (for example a half-typed home title).

**Screensaver doesn't start.** Check `settings get secure screensaver_components`, the TV's "When to start" time, and that no app is holding the screen awake (video players do).

**`adb shell screencap` output is corrupt with `exec-out`.** Some TV builds print to stdout. Use `adb shell screencap -p /sdcard/c.png && adb pull /sdcard/c.png`.

**Weather doesn't show.** It hides itself when the city isn't found or the network is down. It refreshes every 60 minutes.

**Gradle can't find the SDK.** Create `android/local.properties` with `sdk.dir=/path/to/Android/sdk`.

## 14. Undo everything

1. Turn off **Settings → Device Preferences → Accessibility → Interdimensional Cable Home button**.
2. Pick another screen saver in **Settings → Device Preferences → Screen saver**.
3. `adb uninstall com.aditya.cabletv`
4. If you debloated: `tools/debloat.sh --rollback all`.

The stock launcher was never touched, so the TV is back to how it was.

## 15. Building it with Claude Code

The whole project was done in one long [Claude Code](https://claude.com/claude-code) session: idea and research, a reference board, a design system and mockups, then the app in milestones, each one checked on the real TV over adb with screenshots. A few things that worked well, if you want to try the same on your TV:

- **Read the device first.** `adb shell getprop`, `pm list packages`, `dumpsys meminfo` gave the real facts (Android 11, not 9; 32-bit only; 430 MB free) before any design work.
- **Spike the risky parts.** Two throwaway apps compared Views and Compose on the TV, and tested every way of taking over Home, before building the real thing.
- **Keep a rollback for everything.** Package baseline before debloating, `disable-user` instead of uninstall, and a one-press way back to the stock launcher.
- **You flip the security switches.** Accessibility and screensaver choices were always made by hand on the TV, never scripted.
- The launch video was made with the [`/brag`](https://github.com/latent-spaces/brag) skill: frames rendered from the real screenshots, soundtrack synthesised from scratch.
