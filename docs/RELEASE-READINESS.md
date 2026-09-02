# Release readiness — 2026-08-31

State of Spearo Go for App Store and Play submission, after the day's work
replacing fabricated data with real predictions.

Versions: **watchOS 1.1.0 (3)**, **Wear OS 2.1.0 (15)**.

---

## Blocking — must be done before submitting

**Status 2026-09-01:** the three original blockers are closed. Both submission
artifacts are built and signed. Only the store console work remains — the
privacy answers, screenshots, and pressing submit.

### Artifacts

Built 2026-09-01, in `build/release-2026-09-01/` (gitignored):

| File | What |
|---|---|
| `SpearoGo.ipa` | watchOS 1.1.0 (3), signed `Apple Distribution: VISIVO (RBDNV7NG89)` |
| `spearo-go-2.1.0-15.aab` | Wear OS 2.1.0 (15), signed with the upload key |

Rebuild the iOS one with:

```
xcodebuild -project SpearoGo.xcodeproj -scheme SpearoGo \
  -destination 'generic/platform=iOS' -configuration Release \
  -archivePath <path>/SpearoGo.xcarchive archive -allowProvisioningUpdates
xcodebuild -exportArchive -archivePath <path>/SpearoGo.xcarchive \
  -exportPath <path>/export -exportOptionsPlist <path>/ExportOptions.plist \
  -allowProvisioningUpdates
```

with `method: app-store-connect`, `teamID: RBDNV7NG89`, `signingStyle: automatic`.
Uploading still needs an App Store Connect session — Xcode Organizer, Transporter,
or `xcrun altool` with an API key.

### ~~1. Enable Firestore TTL policies~~ — done 2026-09-01

All three — `tides_cache`, `tides_rate`, `tides_budget` — show **Serving** on
`expiresAt` with a 0 sec offset. The 24-hour retention the privacy text promises
is now actually enforced.

Note when creating it: the dropdown only offers fields Firestore has already
seen. `expiresAt` was added to that collection after the first documents were
written, so a metered call has to happen before the field appears.

Original instructions, kept for the remaining policy:

```
gcloud firestore fields ttls update expiresAt \
  --collection-group=tides_cache --enable-ttl --project=spearo-tracker
gcloud firestore fields ttls update expiresAt \
  --collection-group=tides_rate --enable-ttl --project=spearo-tracker
gcloud firestore fields ttls update expiresAt \
  --collection-group=tides_budget --enable-ttl --project=spearo-tracker
```

Or Firebase console → Firestore → TTL. Verify afterwards that a `tides_rate`
document written more than two hours ago has gone.

### 2. Update the store privacy answers

Both stores were answered when nothing left the watch for a Spearo server. That
is no longer true.

- **Play Console → Data safety.** The exact selections are in
  `docs/GOOGLE_PLAY_METADATA.md` under "Data safety declaration". The change
  that matters: approximate location is now *shared with third parties* and *not
  processed ephemerally*.
- **App Store Connect → App Privacy.** Declare **Coarse Location**, purpose *App
  Functionality*, **not** linked to identity, **not** used for tracking. This
  matches `SpearoGo/PrivacyInfo.xcprivacy`, which is already updated.

### ~~3. Set the WorldTides ceiling deliberately~~ — done 2026-09-01

Set to **500 metered calls/day** and deployed.

The plan is 20,000 credits a month and is **shared with Spearo Vision**, which
reads the same `WORLDTIDES_API_KEY` secret. Vision was running at roughly 63
credits a day on 2026-09-01. 500/day x 31 = 15,500/month for Go leaves ~4,500
for Vision, several times its current usage.

A call is not a user: each covers a week for one ~1km square and is cached 24h,
so this is 500 distinct *spots* a day. A watch checking one spot every 30
minutes costs one credit a day. Real daily totals accumulate in the
`tides_budget` collection — look there before changing this.

---

## Done and verified

### Correctness

No fabricated readings remain. Each of these was shipping:

| Was | Now |
|---|---|
| Tides from a model anchored to the Unix epoch, with Lagos **inverted** | NOAA / WorldTides gauges via `tidesGo` |
| A 0.0m sea at 20°C wherever the marine API had no data | "No swell data for this spot" |
| Sunrise/sunset inverted and 13 hours out | Correct to ~20 seconds |
| Lunar anti-transit 6h out; transit 8h out | Correct to under a minute |
| Wind defaulting to 0kn (a flat calm) on a null | Required; the fetch fails instead |
| Wave period defaulting to 0s | Optional, shown as "—" |
| A GO verdict 400km inland | "No sea here" |

Verified against independent computation, not against itself: an separate Meeus
implementation for the astronomy, and the live gauges for tides.

### Tests

27 across three suites — 15 on the backend, 12 on Kotlin. The repo had none this
morning. Swift still has no test target (see BACKLOG); its astronomy was
verified by running the shipping code standalone against the same fixtures.

### Store metadata

`README.md`, `docs/APP_STORE_METADATA.md` and `docs/GOOGLE_PLAY_METADATA.md` no
longer claim offline tides or "M2+S2 harmonic analysis", and the privacy
paragraph describes what actually happens. In-app privacy text is corrected on
both platforms.

### Builds

Release configuration builds clean on both platforms. Wear: `2.1.0 (15)`,
targetSdk 35, minified, `com.spearotracker.spearogo`.

---

## Known and accepted

- **Not installed on a physical watch since the tide wiring.** The test device is
  landlocked — so it correctly shows "No sea here" — and its wireless debugging
  drops whenever the screen sleeps. A coastal spot should be saved and the happy
  path eyeballed before submitting.
- **No App Check.** An App Check token requires the Firebase client SDK, which
  Go deliberately does not ship. The rate limit, daily ceiling and shared cache
  stand in, and all three fail closed.
- **MAYBE is now blue**, not orange, from adopting Spearo Vision's card styling.
- **`outside_coverage` is cached 24h**, so a spot gaining coverage takes a day to
  appear.
