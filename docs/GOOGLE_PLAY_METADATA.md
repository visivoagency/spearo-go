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
• Offline tide and solunar calculations — no API key needed
• Haptic feedback when conditions change
• No subscriptions, no ads, no account required

Spearo Go uses free Open-Meteo APIs for weather and marine data. Tide heights are calculated offline using M2+S2 harmonic analysis. Solunar periods use Meeus orbital math. Your location is used only to fetch conditions — never stored or shared.

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

### v2.0.9 (versionCode 13) — Play policy compliance release
- Added branded splash screen (48dp app icon on black background) to meet Wear OS branded-launch guideline.
- Scrollable views now display a scroll indicator (Wear Material3 `ScreenScaffold`).
- All pages are scrollable so text no longer clips when users set a large system font size.
- Updated store listing to mention Tile support.

### v2.0.7 / 2.0.8 (versionCode 11/12) — Draft iterations
- Internal iteration on Play Console submission flow; superseded by 2.0.9.

### v2.0.6 (versionCode 10) — First successful Play Console upload
- Verdict page shows resolved location name (city/region) instead of raw coordinates.
- Added info page accessible from the verdict screen.
- Reliability: explicit 10s/15s connect/read timeouts on weather and marine API calls.
- Permissions: removed `ACCESS_FINE_LOCATION`, `VIBRATE`, `WAKE_LOCK`; now requires only `ACCESS_COARSE_LOCATION`.
- GPS requests use `PRIORITY_LOW_POWER` for better battery life.
- Migrated UI to Wear Compose `AppScaffold` + `HorizontalPagerScaffold`.
- Tiles: migrated imports from `androidx.wear.tiles` to `androidx.wear.protolayout`.
- ProGuard keep rules for Gson, Retrofit, OkHttp, Hilt, and coroutines to prevent R8 stripping.
- Background refresh worker scheduled on app startup.

### v1.0.0 — Initial Wear OS release
Initial release — your dive-day verdict in one glance.

---

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
