# Google Play Store Metadata — Spearo Go

## App Name
Spearo Go

## Short Description (80 chars max)
Dive-day verdict on your wrist. Weather, swell, tides & solunar in one score.

## Full Description

Spearo Go is the spearfisher's pre-dive checklist — on your wrist.

Before you load the car, just glance at your watch. Spearo Go combines real-time weather, swell height and period, tide phases, and solunar fish activity into a single 0–10 score with a clear verdict: GO, MAYBE, SKETCHY, or NO GO.

WHAT IT SCORES
• Wind speed, gusts, and direction
• Swell height and period
• Water temperature and visibility estimate
• Tide state (rising, falling, slack)
• Solunar major and minor feeding periods
• Moon phase influence on fish activity

HOW IT WORKS
Each factor is scored individually, then combined using a weighted algorithm (Weather 30%, Marine 30%, Tides 15%, Solunar 25%) tuned for spearfishing conditions. The result is one number and one word — so you spend less time checking apps and more time in the water.

TILE SUPPORT
Add the Spearo Go Tile to your watch face for an at-a-glance dive verdict without ever opening the app. Swipe to your Tile, see your GO/NO GO call, and head to the water.

FEATURES
• Dedicated Wear OS Tile — verdict at a glance from your watch face
• One-tap verdict — GO, MAYBE, SKETCHY, or NO GO
• Composite score from 0 to 10 with animated score ring
• Six detailed pages: Verdict, Conditions, Water, Tides, Fish Activity, Info
• Save multiple dive spots and switch between them
• Background refresh every 30 minutes — always current
• Real tide predictions from NOAA and WorldTides gauges
• Haptic feedback when conditions change
• No subscriptions, no ads, no account required

Spearo Go uses free Open-Meteo APIs for weather and marine data, and Spearo's own tide service, which sources predictions from NOAA tide stations in US waters and WorldTides elsewhere. Solunar periods are computed on the watch using Meeus orbital math. Your location is used only to fetch conditions; the tide service rounds it to about a kilometre and keeps that rounded point for 24 hours so the same spot is not looked up repeatedly. Nothing is linked to you, and there are no accounts, ads or analytics. Where a location has no marine or tide coverage, the app says so rather than estimating.

Standalone Wear OS app — no phone companion required. Works on Galaxy Watch 4, 5, 6, 7 and all Wear OS 3+ watches.

## Category
Sports

## Tags
spearfishing, dive, swell, tides, solunar, fishing, marine, weather, ocean, watch

## Price
$2.99

## Content Rating (IARC)
Everyone / PEGI 3

## Copyright
© 2026 Visivo Agency

## Contact Details
- Email: contact@spearotracker.com
- Website: https://spearotracker.com

## Privacy Policy URL
https://spearotracker.com/privacy-policy

---

## Data Safety Form Responses

### Data collected

| Data Type | Collected? | Shared? | Purpose |
|-----------|-----------|---------|---------|
| Approximate location | Yes | No | App functionality (fetch local conditions) |
| Precise location | Yes | No | App functionality (fetch local conditions) |

### Data NOT collected
- Personal info (name, email, etc.)
- Financial info
- Health & fitness data
- Messages
- Photos/videos
- Audio
- Files & docs
- Calendar
- Contacts
- App activity
- Web browsing
- Device identifiers

### Security practices
- Data encrypted in transit (HTTPS)
- No data shared with third parties
- No data sold
- Data deletion: Location is transient (not stored remotely); saved dive spots stored locally on device only

### Targeting
- App does NOT target children

---

## Store Listing Assets Needed

### Screenshots (Wear OS circular, min 384x384)
1. Verdict page — GO verdict with score ring
2. Conditions page — wind and swell data
3. Water page — temperature and visibility
4. Tides page — next high/low with phase
5. Fish Activity page — moon phase and solunar periods

### Feature Graphic (1024x500)
- Ocean-themed banner with app icon and tagline

### App Icon (512x512)
- Trident on ocean blue background (matches adaptive icon)

---

## Release Notes

### 2.1.0 (15)

Tide times now come from real tide stations.

Until now Spearo Go calculated tides on the watch using a simplified model. It
was wrong — in some places badly enough to show a high tide as a low. If you
have been timing dives around the tide, treat any earlier reading as unreliable.

Tides now come from NOAA stations in US waters and WorldTides gauges elsewhere,
and the app shows which station answered. Where a spot has no tide or sea data,
it says so instead of estimating.

Also new: save dive spots anywhere. Search for a place by name and switch between your saved spots, rather than only getting conditions where you happen to be standing.

Also: larger text that follows your watch's text size setting; a new Today
screen with temperature, rain and daylight; corrected sunrise, sunset and moon
times; sea temperature and swell no longer guessed where there is no data; and a
redesigned verdict screen that names any reading it could not include.

## Play Console Checklist

- [ ] Google Play Developer account ($25 one-time fee)
- [ ] Generate release keystore (keytool)
- [ ] Fill in wear/local.properties with keystore credentials
- [ ] Build signed AAB: cd wear && ./gradlew :app:bundleRelease
- [ ] Test release build on physical Wear OS device
- [ ] Create app in Play Console
- [ ] Upload AAB to Internal Testing track first
- [ ] Set Wear OS device type distribution
- [ ] Complete store listing (title, descriptions, screenshots)
- [ ] Complete Data Safety form (see responses above)
- [ ] Complete IARC content rating questionnaire
- [ ] Set pricing ($2.99)
- [ ] Add privacy policy URL
- [ ] Verify spearotracker.com/privacy-policy covers Wear OS
- [ ] Promote to Production track
- [ ] Submit for review (typically 1-3 days)

---

## Data safety declaration

What to select in the Play Console. This changed when tides moved to a backend:
before that nothing was sent to a Spearo server, and the listing said so.

**Data collected: Location → Approximate location**

- Collected: **Yes**
- Shared with third parties: **Yes** — the coordinate is passed to Open-Meteo,
  and to NOAA or WorldTides via Spearo's tide service, purely to look up
  conditions for that place.
- Processed ephemerally: **No** — the tide service keeps the rounded coordinate
  for 24 hours as a cache.
- Required or optional: **Required** (the app cannot produce a verdict without a
  position)
- Purpose: **App functionality**
- Linked to the user: **No** — there are no accounts and no identifiers are sent
- Used for tracking: **No**

Nothing else is collected. No personal info, no device or app IDs, no
diagnostics, no advertising, no analytics. Saved dive spots stay on the watch.

**Data security**

- Encrypted in transit: **Yes** (HTTPS throughout)
- Users can request deletion: no user data is retained to delete — the cache
  holds a rounded coordinate with no identifier and expires after 24 hours.
