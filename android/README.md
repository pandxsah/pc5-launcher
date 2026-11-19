# PC5 Launcher – Android TV (native)

A native Android TV home screen (Kotlin + Jetpack Compose) with the PC5 Launcher look: same colours, Noto Sans,
tile row geometry, focus ring, hub layout, timings and UI sounds, taken from `app/web/css/tokens.css`. No WebView.

## Install
```
adb connect <TV-IP>:5555
curl -L -o pc5.apk https://github.com/pandxsah/pc5-launcher/releases/download/tv-latest/pc5-tv-launcher.apk
adb install -r pc5.apk
adb shell cmd package set-home-activity com.pc5.tvlauncher/.MainActivity
```
Every push to `android-tv` rebuilds the APK (GitHub Actions) and replaces the release file.

## Controls
- Left/Right: move along the row · Down: hub (Play/Open, ⋯ app info) · Up: tabs, Settings, Power
- OK / A: open · Back / B: back · Menu / Start: app info · L1 / R1: Games / Apps tab
- HOME while on the launcher: jump to the first tile

Games row = apps Android marks as games; Apps = everything else. Order: last opened or installed first.
