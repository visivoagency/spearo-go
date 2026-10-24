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
• No subscriptions, no ads. Account optional, for spot sync

Spearo Go uses free Open-Meteo APIs for weather and marine data, and Spearo's own tide service, which sources predictions from NOAA tide stations in US waters and WorldTides elsewhere. Solunar periods are computed on the watch using Meeus orbital math. Your position is used only to fetch conditions. The tide service rounds it to about a kilometre and keeps that point for up to 24 hours so the same spot is not looked up repeatedly, and keeps a one-way hash of your IP address for up to about two hours to limit abuse; neither is linked to you. There are no ads and no analytics. Signing in to a Spearo account is optional; if you do, your email and saved spots are kept in it, private and never shared, so you can see them at spearo.pro. If you search for a place by name, the text you type goes to Open-Meteo's geocoding service. Where a location has no marine or tide coverage, the app says so rather than estimating.

Standalone Wear OS app — no phone companion required. Works on Galaxy Watch 4, 5, 6, 7 and all Wear OS 3+ watches.

## Category
Sports

## Tags
spearfishing, dive, swell, tides, solunar, fishing, marine, weather, ocean, watch

## Price
$0.99

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

Both stores must list the same data types. Last aligned 2026-10-23 with the website
Privacy Policy. If you change one, change the App Store section in
`docs/APP_STORE_METADATA.md` and `SpearoGo/PrivacyInfo.xcprivacy` too.

| Data type | Where it goes | Linked to the user | Shared | Purpose |
|-----------|---------------|--------------------|--------|---------|
| Location (device position) | Open-Meteo and the tide service (NOAA or WorldTides) | No | Yes, with those services, only to look up conditions | App functionality |
| Location (saved spot coordinates) | Open-Meteo and the tide service. If signed in, also your Spearo account | Yes, once signed in | No (the account copy is private) | App functionality |
| Email address (signed in only) | Your Spearo account | Yes | No | App functionality |
| Saved spot names (signed in only) | Your Spearo account | Yes | No | App functionality |
| User ID (signed in only): the Firebase uid, written as `user_id` | Your Spearo account | Yes | No | App functionality |
| Device or other IDs: one-way hash of the IP address, up to about two hours | Spearo tide service | No | No | Fraud prevention and security (Apple: Other) |
| Place-name search text | Open-Meteo geocoding | No | Yes, with Open-Meteo, only to find the place | App functionality (not stored by Go) |

None of it is used for tracking, advertising or analytics.

What the server keeps:
- The tide service keeps the coordinate, rounded to about 1 km, for up to 24 hours so it can answer repeat requests.
- It also keeps a one-way hash of the IP address for up to about two hours to limit abuse (`functions/index.js`).
- Neither is linked to you.

### Play Console answers

**Location → Approximate location** (the manifest asks for `ACCESS_COARSE_LOCATION` only)
- Collected: Yes. Shared: Yes (Open-Meteo, NOAA, WorldTides, to look up conditions).
- Processed ephemerally: No (the tide service caches the rounded point for 24 hours).
- Required or optional: Required (no position, no verdict).
- Purpose: App functionality. Linked to the user: No. Tracking: No.

**Location → Precise location** (saved spot coordinates)
- Collected: Yes. Shared: No for the account copy; the coordinates are also sent to Open-Meteo and the tide service to look up conditions.
- Required or optional: Required to get a verdict; the account copy is optional (sign-in).
- Purpose: App functionality. Linked to the user: Yes once signed in. Tracking: No.

**Personal info → Email address**
- Collected: Yes, only if the diver signs in. Shared: No. Optional.
- Purpose: App functionality (account). Linked: Yes. Tracking: No.

**App activity → Other user-generated content** (saved spot names)
- Collected: Yes, only if the diver signs in. Shared: No. Optional.
- Purpose: App functionality. Linked: Yes. Tracking: No.

**Personal info → User IDs** (the Firebase uid, `user_id`)
- Collected: Yes, only if the diver signs in. Shared: No. Optional.
- Purpose: App functionality. Linked: Yes. Tracking: No.

**App info and performance / Device or other IDs** (one-way IP hash)
- Collected: Yes. Shared: No. Required. Kept up to about two hours.
- Purpose: Fraud prevention, security and compliance. Linked: No. Tracking: No.
- Declared as its own type. Confirmed by Kevin on 23 Oct.

Place-name search text is sent to Open-Meteo geocoding to find the place. It is not stored by Go. Covered by "Shared" for location above; no separate Play type.

### Data NOT collected
- Name: not collected by Go. Google sign-in asks for `openid email` only (no `profile` scope), and Apple sign-in requests the email scope only, so no name or photo reaches Go.
- Financial info, health & fitness, messages, photos/videos, audio, files, calendar, contacts
- App interactions, web browsing, diagnostics, advertising IDs

### Security practices
- Data encrypted in transit (HTTPS)
- No data sold, none used for advertising
- Signed-in data: a spot the diver deletes on the watch is marked deleted in the account and hidden everywhere. Full removal of the data happens through account deletion, which goes through the Spearo website; the exact page is set in the later legal-links stage (see `docs/HANDOFF-from-website-2026-10-23.md`, item 5).
- Signed out: nothing is stored on a Spearo server except the cache and hash above, which expire.

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

### 2.1.1 (16)

Fixes text being cut off at larger text sizes.

If you set a larger text size in your watch's settings, some screens ran their
first line into the clock and lost their last line off the bottom of the round
display. Every screen now keeps its text clear of the clock and the screen
edges at any text size, and scrolls when there is more to show. The app no
longer limits how large you can make the text.

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
- [ ] Complete Data Safety form (see Data Safety Form Responses above)
- [ ] Complete IARC content rating questionnaire
- [ ] Set pricing ($0.99)
- [ ] Add privacy policy URL
- [ ] Verify spearotracker.com/privacy-policy covers Wear OS
- [ ] Promote to Production track
- [ ] Submit for review (typically 1-3 days)

---

## Data safety declaration

The answers are in "Data Safety Form Responses" above. That is the single source;
this section used to repeat it and had drifted (it said "Shared: Yes" while the
form above said "Not shared", and precise location where the manifest asks for
coarse). Do not copy the answers back here.

Play Console path: Policy and programs, App content, Data safety. Enter the four
rows from the table, then the security practices.
