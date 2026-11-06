# Next conversation: full review of the Wear OS app (Google Play) only

Written at the end of the 6 Nov 2026 session. Kevin's scope: **the Wear OS (Kotlin) app only**. No watchOS and no
`tidesGo` backend changes in that session. Anything found there goes into BACKLOG as a watchOS or backend follow-up.

## Where things stand (6 Nov, end of session)

- **Play:** 2.2.1 (18) is live (published 3 Nov). **2.2.2 (19) is in review** (sent 6 Nov, full rollout). It contains:
  - wear-compose 1.5.6 (stable material3). This fixes a crash **at launch** on Wear OS 5 (`reduce_motion` SecurityException).
  - wear-phone-interactions 1.1.1, from Play's SDK note about RemoteAuth on Wear OS 5 with target 35.
  - Condition values are now one line that shrinks to fit (the rating showed "Excellen / t").
- `main` = 27190bc (PR #21). **PR #22 (docs only) is waiting for Kevin to merge.** Merge it first if it's still open.
- Trader status: done for the account and for Go. The other six apps are listed in
  `docs/HANDOFF-trader-status-other-apps-2026-11-06.md` (deadline 1 Feb 2027). They're done in their own sessions, not here.
- Untracked: `docs/HANDOFF-founders-prompt-2026-11.md` (the Founders early-access prompt, written by the website repo).
  It hasn't been built yet, and it isn't part of the review.

## First steps

1. Read `CLAUDE.md` (the "never fabricate a reading" rule, `ScrollingPage`, two languages, Firebase codebase rules), then
   the "Start here" block in `docs/HANDOFF.md`.
2. Check the Play review of 2.2.2 (19) in Play Console (info@visivoagency.com, `/console/u/2`, Spearo Go). If it was
   rejected, fix that first.
3. Then the review.

## Suggested review plan (confirm the scope with Kevin first, one area at a time)

Source: `wear/app/src/main/java/com/spearotracker/spearogo/` (ui/pages, ui/components, sync, services, di, utils).

1. **Data honesty:** every parse site in the weather, marine, tide and solunar services. Look for no `?: 0`-style fallbacks,
   null fields from a 200 response handled, and "—" shown instead of a made-up value. This is the most important rule.
2. **Score / verdict:** `DiveScore` weights and renormalising when signals are missing (see `docs/SCORE_ALGORITHM.md`).
3. **Sync and auth:** `AuthClient`, `FirestoreClient`, `SpotMerge`, `RemoteAuthSignIn`, and the account-switch rules in CLAUDE.md.
   The BACKLOG already lists known narrow windows; don't re-report them.
4. **UI / Wear quality:** every page through `ScrollingPage`/`ScrollingList`, Text size Largest (1.24), rotary scroll,
   loading states that end. In this session the verdict showed "Consulting the fish..." indefinitely on the emulator with
   no location fix. Check that a no-location state says so instead of spinning.
5. **Lifecycle / permissions / battery:** location permission flow, background work, network on a watch.
6. **Build / release hygiene:** dependency versions (compose BOM 2024.12.01 next to wear-compose 1.5.6), R8 rules, native
   debug symbols warning (BACKLOG), unused deps (`kotlinx-coroutines-test` androidTest).

Report findings in docs (a review doc in `docs/`), not as a long chat list. Kevin wants one thing at a time: ask, confirm,
move on.

## Testing setup that worked

- `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`; from `wear/`:
  `./gradlew :app:testDebugUnitTest :app:assembleDebug -Psideload` (111 tests).
- **Wear OS 5 emulator:** AVD `WearOS5_SpearoGo` (API 34). It now launches the store configuration (targetSdk 35). Use it,
  because Kevin's watch is Wear OS 6 and hides Wear OS 5 crashes. It has no paired phone, so sign-in ends at "Keep your phone
  nearby" (`RemoteAuth error 1`).
- **Galaxy Watch Ultra SM-L705F (API 36):** wireless adb. Kevin gives the pairing code + port; `adb mdns services` then shows
  the connect port. Wake it with `input keyevent KEYCODE_WAKEUP` and keep it on with `svc power stayon true` while taking
  screenshots (black screenshots mean the screen was off). Turn stay-on off afterwards. A tap sent while the screen is waking
  can land twice. The sideload copy is signed in as Kevin.
- Text size: `adb shell settings put system font_scale 1.24` (Largest); reset to 1.0 afterwards.
