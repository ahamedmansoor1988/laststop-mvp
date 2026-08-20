# LastStop Android

Native Android foundation for LastStop, built with Kotlin and Jetpack Compose.

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install on a connected phone

Enable Developer options and USB debugging on the phone, connect it by USB, then run:

```sh
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Current stage

The app requests precise or approximate location permission, recovers from denied permissions through Android settings, receives live locations from the Fused Location Provider, and displays coordinates and reported accuracy. Destination setup is gated until a location fix exists.

The destination-first flow lets users search for a place, choose belongings and travel mode, review distance and ETA, and start tracking. Location and notification permissions are requested only when the journey starts. During a journey the app shows remaining distance, keeps a foreground notification active, and offers checklist-based Exit Mode.

Timed arrival reminders and automatic Exit Mode are the next stage.
