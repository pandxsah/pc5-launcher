# PC5 Launcher – Android TV

Runs the **unchanged** PC5 web UI (`../app/web`) inside an Android TV home-screen app, so the theme is identical.
The Python core is replaced by `Backend.kt`, which lists your installed Android apps and launches them.

## Build
1. Keep this `android/` folder inside the pc5-launcher repo (the build copies `../app/web` into the APK).
2. Open `android/` in Android Studio (Ladybug or newer, JDK 17) and let Gradle sync.
3. Build › Build APK(s) → `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the TV
```
adb connect <tv-ip>
adb install -r app-debug.apk
# make it the home screen (Google TV / Android TV 12+ need this; older boxes show a chooser on HOME)
adb shell cmd package set-home-activity com.pc5.tvlauncher/.MainActivity
```
To undo: `adb shell pm disable-user --user 0 com.pc5.tvlauncher`, or uninstall it.

## What works
- Home screen, Library, focus ring, sounds, fonts, backgrounds: the original HTML/CSS/JS
- Games row = apps Android marks as games; Apps row = everything else
- Real app icons and TV banners, launching, "recently played" order, move/hide tiles, rename/favourite
- Remote and gamepad: D-pad, OK/A = cross, Back/B = circle, X, Y, Menu/Start = options, L1/R1, L2/R2
- HOME button jumps back to the PC5 home

## Not available on Android
Steam/GOG/Epic stores, installer, Proton, file explorer, gallery, trophies, Discord, system power options.
Those tiles are hidden by default. Calls they make return null and are logged under `adb logcat -s PC5`.
