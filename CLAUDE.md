# Spearo Go — working notes

Standalone dive-conditions app for Apple Watch (SwiftUI) and Wear OS (Kotlin /
Compose). No account, no subscription.

## The rule that matters most: never fabricate a reading

This app shipped for months telling divers things that were not true — invented
tide times, a 0 m sea at 20 °C wherever the marine API had no data, sunrise and
sunset thirteen hours wrong. Every one of those looked exactly like a real
measurement, which is what made them dangerous: a diver cannot tell a fabricated
number from a measured one.

So:

- **Never coalesce a missing value into a plausible one.** `?? 0`, `?: 20.0` and
  friends at a parse site turn absent data into a reading. If the API did not
  say it, the app does not either.
- **A 200 response is not evidence of data.** Open-Meteo Marine answers HTTP 200
  with null fields for landlocked coordinates. Check the fields, not the status.
- **"Neutral" defaults are not neutral.** 0 m swell and 22 °C water are
  near-ideal inputs — a failed lookup using them *inflates* the verdict.
- **Missing signals are dropped and the weights renormalised**, never scored as
  average ones. `DiveScore.calculate` takes optionals; the verdict card names
  what it could not see.
- **Show "—" or say there is no data.** Never a shimmer that implies loading
  forever.

Spearo Vision learned this the same way, from a customer in Galicia. Its
`lib/services/tide_service.dart` carries the same instruction: *"Do not
reintroduce a fallback that fabricates."*

## Running the apps

Wear: `./gradlew :app:assembleDebug -Psideload` and install beside the Play copy.

watchOS: build the **target**, not the scheme — the scheme pulls in the iOS
container and fails:

```
xcodebuild -project SpearoGo.xcodeproj -target "SpearoGo Watch App" \
  -sdk watchsimulator -configuration Debug CONFIGURATION_BUILD_DIR=<dir> build
```

`SUPPORTED_PLATFORMS` must include `watchsimulator`; it did not until
2026-09-01, which is why the Apple app had never been run.

## Two languages, one behaviour

`SpearoGo/` (Swift) and `wear/` (Kotlin) are the same app twice. The two
`TideService` files once contained the same defect in both, because a change was
made to one and mirrored carelessly. **Change both together, and keep the type
scale, weights and thresholds in lockstep.**

This is not theoretical. On 2026-09-01 two tide fixes were made in Kotlin and
not ported; watchOS shipped both defects for hours and only running the app
found them. If you fix one, fix the other in the same commit.

Where logic can be centralised server-side instead of duplicated, prefer that —
it is why the tide design puts the NOAA/WorldTides choice in a Cloud Function
rather than in both clients.

## Astronomy is testable — test it

Solunar and tide maths must be checked against independently computed ground
truth, not against itself. The existing fixtures (Queidersbach, 2026-08-31) came
from a separate Meeus implementation and caught four real bugs. Kotlin tests
live in `wear/app/src/test/`. Swift has no test target yet.

## Building and sideloading

- watchOS: `xcodebuild -project SpearoGo.xcodeproj -scheme "SpearoGo Watch App"`.
  New source files must be added to `generate_xcodeproj.py`, then regenerate.
- Wear: `./gradlew :app:assembleRelease` from `wear/`. Needs
  `JAVA_HOME` — Android Studio's bundled JBR works.
- **Sideloading to a real watch:** build with `-Psideload`. A locally built APK
  can never update the Play install, because Play App Signing re-signs uploads
  with a different key. The flag gives it its own application id so it installs
  alongside, leaving the user's real app and its saved spots untouched.

## Firebase

The project (`spearo-tracker`) is shared with three other Spearo apps.

Go's functions live in `functions/` under their **own codebase**, `spearogo`.
Spearo Vision owns `default`. This is not cosmetic: two repos deploying the same
codebase would delete each other's functions. Deploy naming both:

```
firebase deploy --only functions:spearogo:tidesGo
```

Go's `firebase.json` deliberately declares **no Firestore config**. Rules live in
Spearo-Empire and indexes in Vision; both describe the whole project, so
deploying either from here would rewrite access control for every other app.
Never `--force` an index.

`tidesGo` is the tide backend. It owns the NOAA-vs-WorldTides decision so the
two clients do not have to — see the two-languages rule above.
