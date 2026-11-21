# VivoDesktop Android project

VivoDesktop is an early Android app for choosing a connected presentation display and opening the app on that screen. It is device-vendor agnostic and uses Android's display APIs.

## Current status

This is an early preview, not a screen-mirroring or desktop-streaming app yet. The current build lets you:

- See Android presentation displays with their names, resolution, refresh rate, and ID.
- Refresh the list when a display is connected or changes.
- Select and remember the last display, with a helpful fallback if it disconnects.
- Open a small confirmation screen on the selected display. On Android versions before 8.0, it uses an Android Presentation instead.

## Build

Open this repository in Android Studio and allow Gradle sync, or build with an installed Gradle distribution and Android SDK:

```sh
gradle :app:assembleDebug
```

The project targets Android SDK 35, supports Android 6.0 (API 23) and newer, and uses Java 17.

## Limitations

The preview confirms that the selected display can be opened; it does not mirror the phone screen or forward keyboard, mouse, or touch input. Those capabilities are not implemented yet.
