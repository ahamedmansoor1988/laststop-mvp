# LastStop MVP Handoff

## Product idea

LastStop is a travel exit assistant. It helps users avoid leaving belongings behind by watching a journey and prompting them before arrival.

Core promise:

> Check before you arrive.

The app is not trying to replace Google Maps. Google Maps answers "How do I get there?" LastStop answers "What should I remember before I get out?"

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
