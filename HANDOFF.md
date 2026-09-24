# exitchck MVP Handoff

## Handoff status — native Android (August 20, 2026, late update)

The native Android app is the active implementation. The product is branded
**QuikLook**. Its permanent Play Store application ID and Kotlin namespace are
`com.quiklook.app`; the older PWA notes are retained only for historical context.

### Repository and important paths

```text
Repository: /Users/mansoor/Documents/Meeting App/laststop-mvp
Android app: /Users/mansoor/Documents/Meeting App/laststop-mvp/android-app
Main UI: android-app/app/src/main/java/com/quiklook/app/MainActivity.kt
Onboarding flow: android-app/app/src/main/java/com/quiklook/app/Onboarding.kt
Google Sign-In: android-app/app/src/main/java/com/quiklook/app/GoogleAuth.kt
Passive watcher: android-app/app/src/main/java/com/quiklook/app/PassiveDetectionService.kt
Active tracking: android-app/app/src/main/java/com/quiklook/app/JourneyTrackingService.kt
Boot/reinstall survival: android-app/app/src/main/java/com/quiklook/app/BootReceiver.kt
Travel-mode speed table: android-app/app/src/main/java/com/quiklook/app/TravelSpeeds.kt
Manifest: android-app/app/src/main/AndroidManifest.xml
Latest packaged APK: /Users/mansoor/Documents/Meeting App/laststop-mvp/LastStop-MVP.apk
Gradle APK output: android-app/app/build/outputs/apk/debug/app-debug.apk
```

The packaged APK is a debug-signed, directly installable test build, not a
Play-Store release-signed artifact.

### Local Android toolchain

```text
JDK 17: /opt/homebrew/opt/openjdk@17
Android SDK: /opt/homebrew/share/android-commandlinetools
ADB: /opt/homebrew/share/android-commandlinetools/platform-tools/adb
Gradle wrapper (./gradlew) now works directly — the earlier /tmp fallback
gradle distribution is no longer needed, wrapper download succeeded this
session.
compileSdk/targetSdk: 36
minSdk: 26
applicationId: com.quiklook.app
```

Build:

```sh
cd "/Users/mansoor/Documents/Meeting App/laststop-mvp/android-app"
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew assembleDebug
```

Install and launch:

```sh
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r \
  app/build/outputs/apk/debug/app-debug.apk
/opt/homebrew/share/android-commandlinetools/platform-tools/adb shell monkey \
  -p com.quiklook.app -c android.intent.category.LAUNCHER 1
```

Test phone: Samsung SM-M066B, ADB serial `R9ZY90RYY5B`. Connectivity via USB
was intermittent throughout this session (adb repeatedly lost the device, and
`screencap` frequently returned a black/Dozing frame even when reportedly
awake) — don't assume a `adb devices` failure means the phone is unusable,
just reconnect and retry. Prefer `wm dismiss-keyguard` + `input keyevent
KEYCODE_WAKEUP` before screenshotting.

### High-level architecture (post-rebuild)

Three long-running pieces, all Compose/Kotlin, no backend server:

1. **`PassiveDetectionService`** — always-on, low-power foreground service.
   Runs whenever the user isn't mid-journey. Detects travel via a *session*
   model (not a fixed time window): sums GPS segment distances continuously,
   resets the session only after a 5-minute stationary gap, and fires once
   cumulative session distance reaches 400 m **or** 2 consecutive fixes read
   ≥8 m/s. After firing, snoozes 30 minutes (`PREF_SNOOZE_UNTIL_EPOCH_MS`).
   Rejects fixes with accuracy worse than 100 m or implausible jumps
   (>55 m/s implied speed). Tapping its notification opens `MainActivity`
   with `ACTION_OPEN_TRAVEL_SETUP`, landing straight on the merged journey
   screen (see below).

2. **`JourneyTrackingService`** — active tracking once the user commits to a
   trip. Two modes:
   - **Duration/timer mode**: pure countdown, no GPS needed.
   - **Destination mode**: GPS distance + live traffic-aware ETA from
     Google's **Routes API** (separate key, see below), with a graceful
     fallback to a straight-line-distance × 1.3 road-inefficiency factor ÷
     realistic average speed (`TravelSpeeds.kt`) when the API is unavailable.
   Fires staged alerts at **5 min, 3 min, 1 min, and arrival** (arrival is
   **always** local GPS distance ≤60 m — never depends on the API). Each
   stage alert except arrival has a **Snooze** action button
   (`ACTION_DISMISS_ALERT`) — tapping it just dismisses that notification;
   it does not change timing, since the next checkpoint already fires on its
   own schedule regardless (added this session per explicit user request —
   "snooze" here means acknowledge/dismiss, not a real alarm-style re-ring).
   Stage alerts play the **device's default alarm sound** (not notification
   sound) via a dedicated channel `journey_alerts_alarm` (renamed from
   `movement_alerts` — channel sound can't be changed after creation, so a
   fresh ID was required to apply this on devices with the old channel
   already installed). Arrival auto-triggers Exit Mode. The passive
   "looks like you're traveling" prompt's 30-minute snooze/cooldown was
   removed entirely this session — it can re-fire immediately once movement
   conditions are met again.

3. **`BootReceiver`** — restarts whichever of the above should be running
   after a device reboot (`BOOT_COMPLETED` only). Before this was added, a
   reboot mid-journey silently killed tracking with no recovery — this
   matters most for long trips, where a reboot is statistically more likely
   to happen mid-trip. **Deliberately does not act on `MY_PACKAGE_REPLACED`**
   (app update/reinstall) — an earlier version did, and it crashed the app
   every single time with `SecurityException: Starting FGS with type
   location ... the app must be in the eligible state`. Android only grants
   the background-start eligibility exemption for location-type foreground
   services to `BOOT_COMPLETED`, not `MY_PACKAGE_REPLACED`, when only
   while-in-use (not "always") location permission is held — which is all
   this app ever requests. `MainActivity.onResume()` already covers the
   reinstall/update case reliably since the app is always reopened shortly
   after. Both `PassiveDetectionService` and `JourneyTrackingService` also
   now wrap their `startForeground()` calls in `try/catch(SecurityException)`
   as defense-in-depth, so this class of failure can never crash the app
   again even in some other edge case — it just logs and stops itself
   quietly instead.

### Adaptive Routes API polling (cost control)

`JourneyTrackingService.computeNextPollDelaySeconds()` schedules the next
live-ETA fetch relative to how much trip is left, not a flat interval:

```text
> 4h remaining   → wait 2/3 of remaining, capped at a 3h ceiling
30min–4h         → lands at T-90min before arrival
5min–30min       → lands exactly at the T-5min checkpoint
1min–5min        → lands exactly at the T-1min checkpoint
< 1min           → polling stops entirely once the 1-min alert has fired
```

Plus: a "stuck near arrival" dampener (if ETA hasn't shrunk ≥20s since the
last poll within 5 min of arrival, backs off to a 90s floor instead of
chasing the countdown), exponential retry backoff on fetch failure
(60s → 120s → … capped at 10min), and **live ETA decays in real time**
between polls (`currentLiveEtaSeconds()`) rather than freezing at whatever
was last fetched — critical once polling became this sparse, otherwise a
multi-hour-old fetch would freeze the arrival clock and could falsely trigger
"arrived" if ever allowed to decay unchecked. A 12h trip costs roughly
4–5 Routes API calls total; a 12-minute trip costs roughly 2–3.

### Google Places / Routes API configuration — now TWO separate keys

Both are Git-ignored in `android-app/local.properties` (never commit or
print either). `local.defaults.properties` holds CI-safe placeholder
defaults (`DEFAULT_API_KEY` / `DEFAULT_CLIENT_ID`) so the project still
builds without secrets present.

```text
PLACES_API_KEY   — existing key, restricted to Android app (package + debug
                    SHA-1 A9:AE:C8:74:D2:60:32:2F:DF:9B:BF:AC:F7:9B:A4:52:BC:ED:DE:70),
                    API-restricted to Places API (New). Used only for the
                    in-app Places Autocomplete widget (a real Google SDK
                    call, so Android-app restriction works fine here).

ROUTES_API_KEY   — separate key, deliberately UNRESTRICTED (Application
                    restrictions: None), API-restricted to Routes API only.
                    Confirmed working this session via direct curl test
                    (Home→Office, 4.6km, real traffic-aware duration
                    returned). Has a Cloud Console daily quota cap set as a
                    safety net since it carries no app restriction.
```

**Why two keys, and why the Routes key has no Android restriction:** Android
app-restricted keys only work for calls made through Google's own SDKs
(Places SDK, Maps SDK) — the restriction can't be validated for a raw HTTPS
REST call like the one `JourneyTrackingService` makes directly to
`routes.googleapis.com`. The first attempt reused `PLACES_API_KEY` for Routes
and got `API_KEY_ANDROID_APP_BLOCKED` (`androidPackage: "<empty>"`) even
after enabling the Routes API — that's what led to creating the second,
unrestricted key.

Integration versions:

```text
Secrets Gradle Plugin: 2.0.1
Places SDK for Android: 4.3.1
Play Services Location: 21.3.0
OkHttp: 4.12.0 (raw Routes API calls)
androidx.credentials: 1.3.0 (Google Sign-In)
googleid: 1.1.1
```

### Google Sign-In (added this session, real OAuth via Credential Manager)

`GoogleAuth.kt` wraps `androidx.credentials.CredentialManager` +
`GetGoogleIdOption`. Reads `BuildConfig.GOOGLE_WEB_CLIENT_ID` (a **third**
`local.properties` entry, not yet added by the user as of this handoff).
Requires, in the same Google Cloud project:

1. OAuth consent screen configured (External, app name "exitchck", the
   user's own account added as a test user so sign-in works pre-verification).
2. An **Android** OAuth client (package `com.quiklook.app` + the same debug
   SHA-1 above) — proves the request comes from the real app.
3. A **Web application** OAuth client — its Client ID is the one that
   actually goes in `local.properties` as `GOOGLE_WEB_CLIENT_ID`, used as
   `setServerClientId()`.

No backend exists to verify the ID token server-side — sign-in is used
purely for display (`SignedInUser(displayName, email, photoUrl)`, persisted
locally in SharedPreferences), not real authentication/authorization. This
was an explicit scope choice (user picked "full Google Sign-In" over a
lightweight name-only alternative when asked).

Sign-in has a "Skip for now" escape hatch in onboarding so an unconfigured
or failed sign-in never hard-blocks getting into the app.

### Onboarding flow (new — `Onboarding.kt`)

Shown once, gated by `onboarding_complete` in SharedPreferences (defaults to
`false`, so it will trigger on next launch on the test phone too since this
key never existed before). Steps: **Welcome → How It Works (3-step
explainer) → Google Sign-In (skippable) → Set Home location → Location
permission ("while using the app," not "always" — deliberate; the app's
foreground-service design doesn't need `ACCESS_BACKGROUND_LOCATION`)**.
Setting Home during onboarding reuses the existing Saved Places mechanism
(saves under label "Home"), including its privacy disclosure copy that
location isn't collected by any server. `finishOnboarding()` explicitly
kicks off `startPassiveWatch()` immediately rather than waiting for the next
`onResume()`.

### Merged main screen (UX simplification this session)

The old two-screen flow (Belongings → tap Continue → separate Destination/
Timer screen) was merged into a single `JourneyScreen` composable — no page
navigation in between, one continuous scroll, one "Start journey" button at
the bottom. Destination tab is selected by default (Timer is the
alternative), per explicit user direction to reduce UX friction. `UiScreen`
enum is now just `{ Idle, Journey }`.

### Native Android features completed (cumulative)

- Kotlin + Jetpack Compose UI throughout.
- Always-on passive travel detection (no manual "Start journey" needed) —
  see architecture section above.
- Merged Belongings + Destination/Timer selection screen.
- Timer mode: Hours/Minutes stepper UI (`-`/value/`+` pills) matching a
  reference design the user provided, plus a live "Arriving around HH:MM"
  preview.
- Destination mode: Google Places Autocomplete (New) full-screen widget,
  Saved Places (Home/Office/custom labels, edit/remove), Recent Destinations
  (auto-populated, capped at 5) — both bypass Places API entirely on reuse.
- Belongings: persistent, user-growable catalog (seeded with 12 common
  items), add-by-typing with live suggestions, Edit-list mode to remove
  entries permanently.
- Travel modes: Walk, Bike, Car, Bus, Train (renamed from "Auto"→"Bike" this
  session).
- Live traffic-aware ETA via Routes API with adaptive polling (see above);
  graceful local fallback otherwise.
- Staged alerts at 5 min / 1 min / arrival; arrival auto-enters Exit Mode.
- Foreground location service with ongoing remaining-distance/ETA
  notification.
- Battery-optimization exemption banner + `Settings.ACTION_REQUEST_IGNORE_
  BATTERY_OPTIMIZATIONS` prompt — Samsung's battery management was
  confirmed (via `dumpsys deviceidle whitelist`) to be a real cause of the
  passive watcher silently dying.
- Boot/reinstall survival via `BootReceiver` (see above).
- Onboarding flow + Google Sign-In (see above).
- Debug-only test triggers: `com.quiklook.app.action.TEST_MOVEMENT_ALERT`
  (JourneyTrackingService "reached" alert) and
  `com.quiklook.app.action.TEST_TRAVEL_ALERT` (PassiveDetectionService
  "looks like you're traveling" alert).

### Settings screen (added this session)

`SettingsScreen` in `MainActivity.kt`, reachable via a "Settings" text link in
the header (hidden during an active journey). Three sections: **Account**
(signed-in user + Sign out, or Sign in with Google), **Home Location**
(view/change, reuses the same Saved-Places "Home" mechanism as onboarding),
**Permissions** (location grant status + "Open app settings", "Turn on
location" if system Location is off, "Allow background activity" if battery
optimization isn't exempted). Closes the gap where onboarding-only flows
(sign in, set home, request permission) had no way to be revisited later.

### Known open items / not yet done

- User has not yet added `GOOGLE_WEB_CLIENT_ID` to `local.properties` — sign-
  in will show "Google Sign-In isn't configured yet" until they do (falls
  back gracefully, doesn't crash).
- App launcher icon was not updated for the exitchck rebrand — still
  whatever the original LastStop icon asset was. User is doing their own UI
  design pass; may want to fold this in rather than have it done ad hoc.
- No release/Play-Store signing config exists — only debug-signed builds.
- `Theme.LastStop` style name and internal package/class names were
  deliberately left as-is; only user-facing copy and the manifest
  `android:label` were rebranded.
- The domain `exitchck.com` was confirmed intentional by the user (not a
  typo for "exitcheck.com").
- Only tested on one device (Samsung SM-M066B) — other OEMs (Xiaomi,
  OnePlus, etc.) have their own aggressive battery-killers not specifically
  handled.
- **Credits/monetization is explicitly deferred**, not started: 25 free
  credits on signup, then ~₹250 (auto-localized via Play Billing) for a
  100-credit top-up, no subscription, 1 credit = 1 trip started. Needs a
  real backend (user agreed to Firebase/Firestore when asked) since credits
  must follow the signed-in account, not the device. Do not start this
  without the user explicitly asking — see project memory for full context.

### State and source-control cautions

- `android-app/` and `LastStop-MVP.apk` are currently untracked in Git unless
  a future session stages them. Preserve them.
- The original repository currently has only commit `2d0f804 Initial
  LastStop MVP` — nothing from this session or the prior native-Android
  session has been committed.
- Never commit `android-app/local.properties` — it now holds three secrets/
  identifiers (`PLACES_API_KEY`, `ROUTES_API_KEY`, `GOOGLE_WEB_CLIENT_ID`).
- Updating/installing with `adb install -r` preserves app data, including
  recent destinations, saved places, belongings catalog, and onboarding
  state.

---

## Legacy PWA context

## Product idea

exitchck (formerly LastStop) is a travel exit assistant. It helps users avoid leaving belongings behind by watching a journey and prompting them before arrival.

Core promise:

> Check before you arrive.

The app is not trying to replace Google Maps. Google Maps answers "How do I get there?" exitchck answers "What should I remember before I get out?"

## Current app

This repository currently contains a mobile-first PWA prototype.

Production URL:

```text
https://laststop-mvp.vercel.app
```

Latest known version:

```text
MVP v15
```

## Current features

- Mobile-first minimal UI with icons
- GPS permission flow
- Destination selection
  - Saved places
  - Search
  - Coordinates
  - Google Maps / geo link
- Geoapify-powered search through Vercel serverless API
- Belongings checklist
- Custom item entry
- Travel modes
  - Walk
  - Auto
  - Car
  - Bus
  - Train
- ETA calculation using GPS speed when available, otherwise selected travel mode speed
- Journey tracking
- Reminder stages
  - 10 min
  - 5 min
  - 2 min
  - 30 sec
- Exit Mode
- Recent journey history
- Movement detection prompt:
  - If the app detects roughly 1 km movement within about 90 seconds, it asks:
    "Looks like you're traveling."
  - Yes opens setup flow
  - No snoozes for 30 minutes

## Important limitations

Because this is a browser/PWA prototype:

- Background GPS is limited by the browser and OS.
- Always-on travel detection is not reliable unless the app is open or allowed to run.
- Browser notification action buttons are not consistently supported on mobile.
- Exact business search may still miss places unless Google Places is used.

For production reliability, this should become a native Android/iOS app.

## Search providers

Current backend search priority:

1. Google Places, if `GOOGLE_MAPS_API_KEY` exists
2. Geoapify, if `GEOAPIFY_API_KEY` exists
3. LocationIQ, if `LOCATIONIQ_API_KEY` exists
4. OpenStreetMap fallback only when no configured provider exists

Current Vercel production environment:

```text
GEOAPIFY_API_KEY=configured in Vercel as sensitive env var
```

Do not commit API keys to GitHub.

## Why Hiver search may fail

Geoapify does not always know exact office/business names such as "Hiver Bangalore".

Google Maps finds it because Google has a stronger private Places database.

For exact Google-like search, add:

```text
GOOGLE_MAPS_API_KEY
```

to Vercel and enable Google Places API in Google Cloud.

## Local run

From the parent workspace:

```sh
python3 -m http.server 8123
```

Then open:

```text
http://localhost:8123/LastStopMVP/
```

For phone GPS testing, use the deployed HTTPS URL instead:

```text
https://laststop-mvp.vercel.app
```

## Deploy

The project is deployed with Vercel.

From inside `LastStopMVP`:

```sh
npx vercel --prod --yes
```

If environment variables are changed, redeploy after updating them.

## Files

```text
index.html              Main app UI
styles.css              Visual design
app.js                  Client-side app logic
api/search.js           Server-side place search proxy
api/resolve-maps.js     Google Maps link resolver
manifest.webmanifest    PWA manifest
service-worker.js       Offline/cache layer
icon.svg                App icon
README.md               Basic project README
HANDOFF.md              This handoff document
```

## Suggested Android app direction

Build a native Android version instead of a WebView wrapper.

Recommended stack:

```text
Kotlin
Jetpack Compose
Fused Location Provider
WorkManager / Foreground Service for journey tracking
Notification channels
Room or DataStore for saved places/items
Retrofit/Ktor for API calls
```

Native Android MVP should include:

- Location permission onboarding
- Foreground journey tracking notification
- Destination search via existing Vercel API
- Saved places
- Belongings checklist
- Travel mode selector
- Exit Mode
- Movement detection prompt
- Persistent notifications near arrival
- Battery optimization guidance

## Next product decisions

- Decide whether exact search needs Google Places now.
- Decide whether reminders should be distance-based, ETA-based, or both.
- Decide default items by context.
- Decide whether movement detection should be opt-in during onboarding.
- Build native Android app for real background reliability.
