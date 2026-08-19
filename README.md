# LastStop MVP

LastStop is a mobile-first PWA prototype for a travel exit assistant. It tracks the user's current GPS location, stores a destination coordinate, estimates arrival time, and switches into a simple checklist-style Exit Mode near the destination.

## Run locally

```sh
python3 -m http.server 8123
```

Then open:

```text
http://localhost:8123/LastStopMVP/
```

## MVP features

- Phone GPS permission and live location updates
- Saved places
- Manual destination coordinates
- Google Maps or geo link coordinate parsing
- Belongings checklist
- Journey tracking with distance and ETA
- 10 min, 5 min, 2 min, and 30 sec reminder stages
- Full-screen Exit Mode
- Recent journey history
- PWA manifest and service worker

## Notes

This version intentionally avoids paid map/search APIs. A production mobile app can add Geoapify, LocationIQ, Mapbox, HERE, or Google Places later for address search and autocomplete.
