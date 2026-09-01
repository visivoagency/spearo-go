# Spot search — adding a dive spot you are not standing at

**Date:** 2026-09-01
**Status:** Design approved, implementation not started

---

## 1. The problem

Neither app can save a spot the diver is not physically at.

- **Wear OS** has no locations UI at all. The Room entity, DAO and every
  ViewModel function — `addLocation`, `deleteLocation`, `setActiveLocation` —
  already exist and are wired to the refresh pipeline. Nothing calls them. A
  Wear user gets conditions only for where they are standing.
- **watchOS** has `LocationsView`, but its only way to create a spot is
  "save my current GPS fix under a name". Same limitation, nicer wrapper.

This became visible when the verdict page gained a "The sea is calling" state
that tells inland users to *"Save a dive spot on the coast"* — advice a Wear
user cannot act on, because there is no screen to do it.

## 2. Decisions taken

| Decision | Choice | Rejected |
|---|---|---|
| What the user enters | A place name, geocoded | Coordinates only; both; phone companion |
| Geocoder | Open-Meteo geocoding API | Android `Geocoder` / `CLGeocoder` |
| Scope | Both platforms, one release | Wear first; watchOS first |

### Why Open-Meteo rather than the platform geocoders

Android's `Geocoder` **is** available on the test watch — it is what already
turns coordinates into "Queidersbach, Rheinland-Pfalz" for the verdict page. So
forward geocoding through it would work on Wear.

It is still the wrong choice here. `Geocoder` and `CLGeocoder` are different
services with different result sets and different ranking, so the two apps would
disagree about what "Lagos" means. Open-Meteo returns the same JSON to both, can
be pinned in a test fixture, needs no key, and is the provider the app already
uses for weather — no new account, cost, or privacy surface.

Reverse geocoding stays on the platform APIs. It is only used for a display
label and works offline-ish on device.

## 3. Design

### 3.1 GeocodingService — one per language, same shape

```
GET https://geocoding-api.open-meteo.com/v1/search
      ?name=<query>&count=8&language=en&format=json
```

Returns a list of `GeocodedPlace { name, region, country, latitude, longitude }`
mapped from `name`, `admin1`, `country_code`, `latitude`, `longitude`.

**Results MUST render region and country.** A search for "Lagos" returns four
places — Nigeria, France, Spain, and Portugal — and the Portuguese one a diver
in the Algarve wants is **fourth**. A list of four identical rows reading
"Lagos" would be worse than no search at all.

Empty `results` is a normal answer, not an error: "No places found".

### 3.2 Wear OS — a locations screen

Entry point: **long-press the verdict page.** That gesture currently opens
`InfoPage`; it becomes a small chooser, or Info moves to a button within the new
screen. Deliberately not a sixth pager page — the pager is for conditions, and a
management screen does not belong in that rhythm.

The screen holds:

1. **Search** — opens the system input activity via `RemoteInput`, which gives
   voice dictation, keyboard and handwriting without the app implementing any of
   them. Voice is how people actually enter text on a watch.
2. **Results** — name, region, country. Tapping one saves it and makes it active.
3. **Saved spots** — tap to activate, long-press to delete, plus a "Use my
   location" row that clears the override.

All of it calls ViewModel functions that already exist. No data-layer work.

### 3.3 watchOS — extend LocationsView

`LocationsView` keeps "save here" and gains a search field above it, feeding the
same results list and the same `SavedLocation` insert. Input is the standard
watchOS `TextField`, which already offers dictation and scribble.

### 3.4 Data

No schema change on either platform. `SavedLocation` already carries name,
latitude, longitude and isActive, and the active override is already threaded
through `AppState` and `AppViewModel`.

## 4. What happens when the spot has no sea

A geocoder returns town centres, not dive sites. This was tested rather than
assumed: Open-Meteo Marine covers the Lagos town centre (0.74 m swell, 16.6 °C
on 2026-09-01), so a coastal town generally lands close enough for real data.

Where it does not — an inland city, or a coast the grid misses — the existing
"The sea is calling" state answers it, and the tide backend answers
`outside_coverage`. Nothing new is needed, and nothing is estimated.

Deliberately **not** doing: nudging a geocoded point seaward to find data. It
would move the spot somewhere the diver did not choose, which is a quiet form of
inventing a location.

## 5. Testing

Kotlin unit tests over a recorded Open-Meteo response for "Lagos":

| Test | Assertion |
|---|---|
| Four Lagoses stay distinct | All four parse, each with its own region and country |
| The Portuguese one is findable | 37.10, -8.67 present with country `PT`, region `Faro District` |
| Empty results | Parsed as an empty list, not an error |
| Missing `admin1` | Renders without a region rather than crashing |

Swift is verified against the same recorded response.

## 6. Deliberately out of scope

Map picking, coordinate entry, phone-companion sync, reordering saved spots,
and a spot limit. Each is a real feature; none is needed to close this hole.

## 7. Order of work

1. `GeocodingService` + tests, Kotlin
2. Wear locations screen; resolve the long-press/Info collision
3. `GeocodingService`, Swift
4. `LocationsView` search field
5. Verify on the Galaxy Watch: search "Lagos", save the Portuguese one, confirm
   real tides render — which also closes the outstanding "no human has seen real
   tide data on a watch" gap
