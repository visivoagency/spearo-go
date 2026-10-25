# Backlog

## Open

### Account sync: console setup, live E2E and store forms (added 2026-10-23)
The code is built but never run against a live sign-in. Console setup P1 to P5 was done on
25 Oct (`docs/ACCOUNT-SYNC-SETUP.md`). Next is the E2E in the plan, then enter the new Data safety and App Privacy
answers, then take signed-in screenshots. Kevin also needs to set the 18+ store ratings and
the new legal URLs in both consoles (steps in the two metadata docs, 25 Oct). (The More
Spearo apps entry was dropped by Kevin on 25 Oct; Go won't promote the other apps.)
The hashed IP's own privacy-label type (Device ID) was confirmed by Kevin on 23 Oct.

Device checks before release. Each one is something the tests could not reach:
1. A release AAB with `WEB_CLIENT_SECRET` set. The first REST call is not a 403 `*_APP_BLOCKED`, so the API keys allow the Play signing SHA-1.
2. Wear sign-in through the phone, for both the Play id and `.sideload`. The Firebase Auth record has no name or photo.
3. Sign in with Apple: a new account, an existing Apnea account (same uid), a hidden-email account, and a Google-created email signing in with Apple (the account-exists message).
4. First sign-in with spots already on the watch. "Lagos" against an account "lagos " about 60 m away links instead of duplicating. Spots show at spearo.pro within seconds.
5. Pulls from the website, from Tracker (camelCase) and from Vision. Vision rows say "Saved in Vision" and can't be deleted while signed in.
6. Deletes both ways. A Tracker spot deleted on the watch disappears in Tracker, and a Tracker restore still returns everything.
7. Airplane mode: save and delete, reconnect, both sync.
8. A token older than an hour refreshes silently. A revoked token returns to signed out with the spots still on the watch.
9. Sign out and back into the same account: no duplicates. Sign into a second account: the first account's synced spots leave the watch and are not uploaded; spots saved while signed out upload; a spot saved under the first account while offline stays hidden (not listed, not uploaded) and uploads to the first account when it signs back in. Delete the active spot on each watch; both fall back to your location.
10. A brand-new diver: `users/{uid}` and the website profile come out right. Go never creates that doc.
11. Upgrade installs over real data: Wear from 2.1.1 and watchOS from 1.1.0. The order and the active spot survive, with no 1970 timestamps. Also a Wear backup restored onto a second watch.
12. Signed out, a network capture shows only Open-Meteo, geocoding and `tidesGo`.
13. Every sync-row state at Wear Text size Largest and at the largest watchOS Dynamic Type.
14. In the Firestore console, a Go doc has exactly the 12 snake_case keys, `is_private: true`, and no `isDeleted` or `lastModifiedAt`.
15. The account prompt (24 Oct). On a fresh install, signed out, save a first spot: the prompt appears once (Wear: in place of the Spots list; Apple Watch: after the save sheet closes). "Not now" and Back dismiss it; after that a second save does not bring it back. "Sign in" starts the sign-in, and on Apple Watch the prompt closes once signed in. Check it at Wear Text size Largest.
16. Safety and legal (25 Oct). On a fresh install, onboarding shows "Before You Dive" (the disclaimer and the 18+ line) as page 3 of 4 on both watches. About shows Safety first and Legal last. Check both at Wear Text size Largest and at the largest watchOS Dynamic Type, which wasn't run on a screen.

### Account sync: small follow-ups from review (added 2026-10-23)
None of these blocks the release.
- (25 Oct) The two Firebase API keys Go shares with Apnea and Tracker have no application restrictions. Google flags this. Restricting them means listing every app's package, bundle ID and SHA-1 at once, so it is a cross-app job for Kevin and not something one app should do alone.
- (24 Oct) A deleted account's hidden spots stay on the watch indefinitely. Go cannot tell that an account was deleted, and the account can't sign in again to collect them. Possible fix: show a "N spots saved for another account" row with a remove option.
- (24 Oct) Swift `removeAccountSpots` and `isVisible(to:)` have no automated test (no Swift test target, and the harness is Foundation-only).
- (24 Oct) Apple Watch: the Locations list follows `@AppStorage("sync.linkedUid")`, which SpotSyncService writes from its actor. The KVO update may arrive off the main thread. If it is ever missed, an open Locations sheet keeps the old filter until it reopens. Fix if seen: mirror linkedUid into `SyncStatus`.
- (24 Oct) Device check: on Apple Watch, tapping a saved spot still updates the verdict now that `ContentView` keys on the active spot instead of the array.
- Account switch, narrow windows. If a session expires offline before it links, a spot saved under U2 can upload to U1 on a later same-account sign-in. Fix: run the removal even when `linked == uid`, with `includeLinked = false`. Also, a signed-out spot that was pushed to U1 but never linked (the app died mid-sync) can also reach U2. Fix: stamp ownerUid before the PATCH, not only in apply.
- The two stacked KDoc blocks on `AppViewModel.deleteLocation` should be merged.
- The shared merge fixtures check ids, not field contents. Add per-row field checks so the Swift field mapping is covered too.
- The website fixture should use `timestampValue` with microseconds, which is what the website actually writes.
- The Swift harness checks encoding less strictly than the Kotlin test: it doesn't check value types on soft deletes.
- Kotlin `Instant.parse` rejects `+00:00` offsets on Android. Firestore and the website always write `Z`.
- Swift has no test for the mapping, the soft delete or `removeAccountSpots`. Only the harness and the device checks cover them.
- On Kotlin, a cancelled caller can clear the in-flight refresh, which costs one extra request. Clear it with `invokeOnCompletion` instead.
- If removing the old account's spots fails at sign-in, the row shows "Sync failed" while actually signed out, until the next tap.
- On Swift, Vision rows can stay undeletable for a moment after a session expires, because the swipe follows the sync state.
- The marker treats any HTTP 400 as "no profile yet", which would also hide a bad API key. The spot calls would still show it.
- The unused androidTest dependency `kotlinx-coroutines-test`.
- `generate_xcodeproj.py` makes new random UUIDs on every run, so each regeneration rewrites the whole pbxproj.

### Wear crashes at launch on the API 34 emulator image with targetSdk 35
`androidx.wear.compose:compose-foundation:1.4.1` reads `Settings.Global`
`reduce_motion`, which the API 34 settings provider refuses to apps targeting
35: `SecurityException: Settings key: <reduce_motion> is only readable to apps
with targetSdkVersion lower than or equal to: 34`. Physical Wear OS 5/6 watches
are fine (the Play build was reviewed on one). Blocks emulator testing of the
store configuration; the 2026-09-07 session built with `targetSdk = 34`
locally to work around it. Check whether a newer wear-compose guards the read,
and whether any real device with the same settings provider exists.

### watchOS caps Dynamic Type at accessibility2
`SpearoGoApp.swift` applies `.dynamicTypeSize(...accessibility2)`. The Wear
equivalent (a 1.3 font-scale ceiling) is what Google Play rejected 2.1.0 (15)
for; Apple has not objected. The `CLAUDE.md` lockstep rule covers the type
scale, which is unchanged, but the two platforms now differ in whether they
bound the user's size. Decide deliberately; if the cap goes, the watchOS pages
need the same round-screen viewport treatment as `ScrollingPage.kt`.

### Release blockers
See `docs/RELEASE-READINESS.md`. Three: enable the Firestore TTL policies
(`gcloud` was unavailable when the backend was deployed), update the Play Data
Safety and App Store privacy answers now that location reaches a Spearo server
(the docs were rewritten 2026-10-23; the console forms still need entering),
and set the WorldTides daily ceiling against the real credit balance.

### ~~A landlocked spot still gets a dive verdict~~ — fixed 2026-08-31
Now says "No sea here" when both marine and tide report no coverage — which is
distinguished from a failed lookup, so a transient outage never triggers it.

### The Swift and Kotlin clients drift
The backend owns the NOAA/WorldTides decision so it cannot drift, but the
client-side derivation — which tide is next, station-local rendering, the
next-day rollover — exists twice. Two fixes made on Kotlin on 2026-09-01 were
not ported to Swift and shipped broken there for hours, found only by running
the app. Worth considering whether that derivation belongs in the payload, or
whether a shared fixture test should assert both languages agree.

### watchOS still has no test target
Now more pressing: the Swift side has no automated check at all, and the two
defects above would each have been caught by one line of test.

### Watch install is blocked by wireless debugging dropping
The Galaxy Watch turns wireless debugging off whenever the screen sleeps, which
cost five re-pairings in one session and blocked the final install. Enabling
Developer options → "Stay awake" with the watch on its charger avoids it.

### Swift has no test target
The Kotlin side has 8 JUnit tests. The Xcode project is generated by
`generate_xcodeproj.py` and has no test target, so Swift correctness is
currently verified by running the shipping code standalone. The tide spec (§4)
requires the same golden fixture in both languages, so this has to be built
before that lands.

### ~~Store listings still claim offline tides~~ — fixed 2026-08-31
Rewritten. Tides are described as NOAA/WorldTides gauges, and the privacy
paragraph now matches what the backend actually does.

### MAYBE changed colour
Adopting Vision's card styling brought its colour semantics: MAYBE moved from
orange to blue. Flagged to the owner 2026-08-31, not yet confirmed either way.

## Done

- **2026-09-01** — Spot search on both platforms; watchOS run for the first
  time; six UI and correctness defects fixed that the tests had not caught.
- **2026-08-31** — `tidesGo` built, deployed and verified live; both clients
  wired to it; NOAA + WorldTides logic ported from Vision into the function so
  it exists once rather than twice.

- **2026-08-31** — Fabricated tide model deleted; marine null-coalescing fixed;
  four solunar bugs fixed (transit complement, 6h anti-transit, `Date()`
  anchoring, rise/set sidereal); weather null defaults removed; score
  renormalisation; typography raised and made accessible; verdict page restyled
  to match Vision; Today/Sky weather pages added; personality copy stopped
  reshuffling on every recomposition.
